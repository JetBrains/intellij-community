public class Foo {
public void print() {
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, CONST);
}

public static java.lang.Integer getCONST() {
 return CONST;
}
public static void setCONST(java.lang.Integer CONST) {
Foo.CONST = CONST;
}
private static java.lang.Integer CONST = 5;
}
