public abstract class Anon {
public Anon(java.lang.Object foo) {
this.foo = foo;
}
public abstract void run() ;
public java.lang.Object getFoo() {
 return foo;
}
public void setFoo(java.lang.Object foo) {
this.foo = foo;
}
private java.lang.Object foo;
}
public class anonymous2 extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new anonymous2(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {
Anon an = new Anon(3){
public void run() {
org.codehaus.groovy.runtime.DefaultGroovyMethods.println(this, getFoo());
}

};

an.run();

return null;

}

public anonymous2(groovy.lang.Binding binding) {
super(binding);
}
public anonymous2() {
super();
}
}
