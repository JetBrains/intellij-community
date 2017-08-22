public class Base {
public Base(int a) {}
}
public class Inheritor extends Base {
public Inheritor(int x, int y) {
super(x);
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, y);
}
}
