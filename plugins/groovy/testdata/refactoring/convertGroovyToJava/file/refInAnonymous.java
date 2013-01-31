public class A {
public void foo() {
final groovy.lang.Reference<java.lang.Integer> x = new groovy.lang.Reference<java.lang.Integer>(2);

new java.lang.Runnable(){
public void run() {
x.set(4);
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(this, x.get());
}

}.run();
}

}
