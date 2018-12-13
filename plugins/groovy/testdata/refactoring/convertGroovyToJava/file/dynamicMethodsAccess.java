public class Abc {
public java.lang.Object foo() {
invokeMethod("bar", new java.lang.Object[]{2});
java.util.LinkedHashMap<java.lang.String, java.lang.Integer> map = new java.util.LinkedHashMap<java.lang.String, java.lang.Integer>(1);
map.put("s", 4);
invokeMethod("print", new java.lang.Object[]{invokeMethod("bar", new java.lang.Object[]{map, 3})});
java.lang.String s = "a";
org.codehaus.groovy.runtime.DefaultGroovyMethods.invokeMethod(s, "bar", new java.lang.Object[]{4});
invokeMethod("print", new java.lang.Object[]{org.codehaus.groovy.runtime.DefaultGroovyMethods.invokeMethod(s, "bar", new java.lang.Object[]{5})});

return org.codehaus.groovy.runtime.DefaultGroovyMethods.invokeMethod(s, "anme", new java.util.ArrayList<java.lang.Object>());
}

}
