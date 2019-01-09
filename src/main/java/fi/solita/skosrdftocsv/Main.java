package fi.solita.skosrdftocsv;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.*;

public class Main {

    public static void printUsage() {
        for(String x : new String[]{
            "skosrdftocsv",
            "----",
            "A utility to format skos/rdf format ontology definitions into csv format",
            "Usage: skosrdftocsv [options...] <source> <target>",
            "  options =",
            "      --inputEncoding <encoding>   -  UTF8, Cp1252, Latin1, ...",
            "      --outputEncoding <encoding>",
            "      --outputFullAboutNs ",
            "      --delim <delim>   -  ;,|  etc.",
            "      --endline <{r|n|rn}>",
            "      --exactMatch <weight>",
            "      --related <weight>",
            "      --broader <weight>",
            "      --narrower <weight>",
            "      --member <weight>",

        }) {
            System.out.println(x);
        }
    }

    static boolean outputFullAboutNs = false;
    static double exactMatchWeight = 1.0;
    static double relatedWeight = 0.5;
    static double broaderWeight = 2.0;
    static double narrowerWeight = 2.0;
    static double memberWeight = 2.0;
    static String DELIM = ";";
    static String ENDLINE = "\n";
    static String inputEncoding = null;
    static String outputEncoding = "UTF8";

    public static void main(String... args) throws IOException, ParserConfigurationException, SAXException {
        String source = null;
        String target = null;
        int context = 0;
        for(int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (context == 0 && !arg.startsWith("-")) {
                if (source == null)
                    source = arg;
                else if (source != null && target == null)
                    target = arg;
            }
            if (arg.startsWith("-")) {
                if (arg.equals("--outputFullAboutNs ")) {
                    outputFullAboutNs  = true;
                }
                else if (arg.equals("--inputEncoding")) {
                    try {
                        inputEncoding = args[++i];
                    } catch (Exception e) {
                        System.out.println("Usage: --inputEncoding <encoding>");
                        System.exit(1);
                    }
                }
                else if (arg.equals("--outputEncoding")) {
                    try {
                        outputEncoding = args[++i];
                    } catch (Exception e) {
                        System.out.println("Usage: --outputEncoding <encoding>");
                        System.exit(1);
                    }
                }
                else if (arg.equals("--exactMatch")) {
                    try {
                        exactMatchWeight = Integer.valueOf(args[++i]);
                    } catch (Exception e) {
                        System.out.println("Usage: --exactMatch <weight>   where weigth >= 0");
                        System.exit(1);
                    }
                }
                else if (arg.equals("--related")) {
                    try {
                        relatedWeight = Integer.valueOf(args[++i]);
                    } catch (Exception e) {
                        System.out.println("Usage: --related <weight>   where weigth >= 0");
                        System.exit(1);
                    }
                }
                else if (arg.equals("--broader")) {
                    try {
                        broaderWeight = Integer.valueOf(args[++i]);
                    } catch (Exception e) {
                        System.out.println("Usage: --broader <weight>   where weigth >= 0");
                        System.exit(1);
                    }
                }
                else if (arg.equals("--narrower")) {
                    try {
                        narrowerWeight = Integer.valueOf(args[++i]);
                    } catch (Exception e) {
                        System.out.println("Usage: --narrower <weight>   where weigth >= 0");
                        System.exit(1);
                    }
                }
                else if (arg.equals("--member")) {
                    try {
                        memberWeight = Integer.valueOf(args[++i]);
                    } catch (Exception e) {
                        System.out.println("Usage: --member <weight>   where weigth >= 0");
                        System.exit(1);
                    }
                }
                else if (arg.equals("--delim")) {
                    try {
                        DELIM = args[++i];
                    } catch (Exception e) {
                        System.out.println("Usage: --delim <delimiter>");
                        System.exit(1);
                    }
                }
                else if (arg.equals("--endline")) {
                    try {
                        ENDLINE = args[++i];
                        if (ENDLINE == "r") { ENDLINE = "\r"; }
                        else if (ENDLINE == "rn") { ENDLINE = "\r\n"; }
                        else if (ENDLINE == "n") { ENDLINE = "\n"; }
                        else throw new RuntimeException("Foo");
                    } catch (Exception e) {
                        System.out.println("Usage: --endline r/n/rn");
                        System.exit(1);
                    }
                }
                else {
                    System.out.println("Unknown argument: " + arg);
                    System.exit(1);
                }
            }
            // TODO: additional parameters
        }
        if (source == null) {
            printUsage();
            System.exit(1);
        }
        File srcFile = new File(source);
        if (!srcFile.exists()) {
            System.out.println("Source file does not exist");
            System.exit(1);
        }
        PrintStream pst;
        if (target == null) {
            pst = System.out;
        } else {
            pst = new PrintStream(new FileOutputStream(new File(target)), true, outputEncoding);
        }

        BufferedInputStream bis = new BufferedInputStream(new FileInputStream(srcFile));
        InputSource iso = new InputSource(bis);
        if (inputEncoding != null)
            iso.setEncoding(inputEncoding);
        readAndFormat(iso, pst);
        bis.close();
        pst.close();
    }

    public static String attr(Node n, String uri) {
        Node ret = null;
        int i = uri.indexOf(":");
        if (i > 0) {
            String ns = uri.substring(0, i);
            String local = uri.substring(i + 1);
            ret = n.getAttributes().getNamedItemNS(ns, local);
            if (ret == null)
                ret = n.getAttributes().getNamedItem(local);
        }
        if (ret == null)
            ret = n.getAttributes().getNamedItem(uri);
        if (ret == null)
            return null;
        return ret.getTextContent();
    }

    public static class NLList implements Iterable<Node> {
        private NodeList list;
        private int i = 0;
        public NLList(NodeList list) { this.list = list; }
        public Iterator<Node> iterator() {
            return new Iterator<Node>() {

                public boolean hasNext() {
                    return i < list.getLength();
                }

                public Node next() {
                    return list.item(i++);
                }

                public void remove() {
                    throw new RuntimeException("Don't support remove()");
                }
            };
        }
    }

    static String baseNs = null;

    static String stripNs(String s) {
        if (baseNs == null) {
            s = s.substring(s.lastIndexOf("/") + 1);
            return s;
        }
        if (!outputFullAboutNs && s.startsWith(baseNs)) {
            return s.substring(baseNs.length() + 1);
        }
        return s;
    }

    private static void readAndFormat(InputSource bis, PrintStream target) throws ParserConfigurationException, IOException, SAXException {
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
        Document doc = dBuilder.parse(bis);
        Element elem = doc.getDocumentElement();
        elem.normalize();
        baseNs = elem.getAttribute("xmlns");
        if (baseNs == "")
            baseNs = null;
        if (baseNs != null)
            baseNs = baseNs.replaceAll("/$", "");


        NodeList list = elem.getChildNodes();
        int skip = 0;
        List<String[]> lines = new ArrayList();
        List<String[]> members = new ArrayList();
        Map<String, String[]> mem = new HashMap();
        for(Node n : new NLList(list)) {
            if (n.getNodeType() != Node.ELEMENT_NODE)
                continue;

            if (!n.getNodeName().equals("rdf:Description")) {
                skip++;
                continue;
            }
            String about = attr(n, "rdf:about");

            List<String> labels = new ArrayList();
            List<Object[]> refs = new ArrayList();
            for(Node rel : new NLList(n.getChildNodes())) {
                if (rel.getNodeName() == "skos:prefLabel" || rel.getNodeName() == "skos:altLabel"
                        || rel.getNodeName() == "skos:hiddenLabel")
                    labels.add(rel.getTextContent());
                else if (rel.getNodeName() == "skos:exactMatch")
                    refs.add(new Object[] {exactMatchWeight, attr(rel, "rdf:resource")} );
                else if (rel.getNodeName() == "skos:related")
                    refs.add(new Object[] {relatedWeight, attr(rel, "rdf:resource") } );
                else if (rel.getNodeName() == "skos:broader")
                    refs.add(new Object[] {broaderWeight, attr(rel, "rdf:resource") } );
                else if (rel.getNodeName() == "skos:narrower")
                    refs.add(new Object[] {narrowerWeight, attr(rel, "rdf:resource") } );
                else if (rel.getNodeName() == "skos:member")
                    members.add(new String[] { stripNs(about), stripNs(attr(rel, "rdf:resource")) } );

            }
            for(Object[] ref : refs) {
                String refname = (String)ref[1];
                if (baseNs == null && !about.substring(0, about.lastIndexOf("/")).equals(
                        refname.substring(0, refname.lastIndexOf("/"))) ) {
                    continue;
                }
                refname = stripNs(refname);
                for (String label : labels) {
                    lines.add(new String[]{stripNs(about), label, ((Double) ref[0]).toString(), refname});
                }
            }
            for (String label : labels) {
                String key = stripNs(about);
                mem.put(key, new String[]{ key, label });
            }
        }

        for(String[] m : members) {
            String[] ref = mem.get(m[1]);
            lines.add(new String[] { ref[0], ref[1], ((Double) memberWeight).toString(), m[0] });
        }

        for(String[] line : lines) {
            String row = line[0] + DELIM + line[1] + DELIM + line[2] + DELIM + line[3] + ENDLINE;
            target.print(row);
        }
        System.out.println("skipped: " + skip);
    }
}
