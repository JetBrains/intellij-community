public class A {
public void x() {
java.lang.String b = """
this is "good\"""";
java.lang.String c = "\"\"";
java.lang.String d = " ";
java.util.LinkedHashMap<java.lang.String, java.lang.String> map = new java.util.LinkedHashMap<java.lang.String, java.lang.String>(1);
map.put("\"\\\\\"", "\"\\\\\"");
java.util.LinkedHashMap<java.lang.String, java.lang.String> data = map;
}

}
