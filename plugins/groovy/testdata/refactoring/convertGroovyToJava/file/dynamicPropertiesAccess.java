public class A {
public void foo() {
setProperty("bar", 2);
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, setProperty0(this, "bar", 3));
java.lang.String s = "a";
s.bar = 4;
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, s.bar = 5);

org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, getProperty("bar"));
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, s.bar);
}

private static <Value>Value setProperty0(A propOwner, java.lang.String s, Value o) {
propOwner.setProperty(s, o);
return o;
}
}
