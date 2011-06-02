public class Foo extends groovy.lang.GroovyObjectSupport implements groovy.lang.GroovyObject {
public void print() {
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(Foo.this, CONST);
}

public static java.lang.Integer getCONST() {
 return CONST;
}
public static void setCONST(java.lang.Integer CONST) {
Foo.CONST = CONST;
}
private static java.lang.Integer CONST = 5;
}
