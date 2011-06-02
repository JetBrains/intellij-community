public class Abc extends groovy.lang.GroovyObjectSupport implements groovy.lang.GroovyObject {
public java.lang.Object foo() {
invokeMethod("bar", new java.lang.Object[]{2});
java.util.Map<java.lang.String, java.lang.Integer> map = new java.util.Map<java.lang.String, java.lang.Integer>(1);
map.put("s", 4);
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(Abc.this, invokeMethod("bar", new java.lang.Object[]{map, 3}));
java.lang.String s = "a";
org.codehaus.groovy.runtime.DefaultGroovyMethods.invokeMethod(s, "bar", new java.lang.Object[]{4});
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(Abc.this, org.codehaus.groovy.runtime.DefaultGroovyMethods.invokeMethod(s, "bar", new java.lang.Object[]{5}));
return org.codehaus.groovy.runtime.DefaultGroovyMethods.invokeMethod(s, "anme", new java.util.ArrayList<java.lang.Object>(java.util.Arrays.asList()));
}

}
