public class Foo extends groovy.lang.GroovyObjectSupport implements groovy.lang.GroovyObject {
public java.lang.Object print() {
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(Foo.this, CONST);
return null;
}

public static java.lang.Integer getCONST() {
 return CONST;
}
public static void setCONST(java.lang.Object CONST) {
Foo.CONST = CONST;
}
private static java.lang.Integer CONST = 5;
}
