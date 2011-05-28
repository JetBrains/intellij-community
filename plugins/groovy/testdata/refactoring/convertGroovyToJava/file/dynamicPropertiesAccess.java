public class A extends groovy.lang.GroovyObjectSupport implements groovy.lang.GroovyObject {
public void foo() {
setProperty("bar", 2);
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(A.this, setProperty0(A.this, "bar", 3));
java.lang.String s = "a";
s.setProperty("bar", 4);
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(A.this, setProperty0(s, "bar", 5));
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(A.this, getProperty("bar"));
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(A.this, s.getProperty("bar"));
}

private static <Value>Value setProperty0(groovy.lang.GroovyObject propOwner, java.lang.String s, Value o) {
propOwner.setProperty(s, o);
return o;
}
}
