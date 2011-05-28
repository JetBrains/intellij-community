public class IntCat extends groovy.lang.GroovyObjectSupport implements groovy.lang.GroovyObject {
public static void call(java.lang.Integer i) {
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(IntCat.this, i);
}

public static void call(java.lang.Integer i, java.lang.String s) {
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(IntCat.this, s);
}

}
public class closureInUse extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new closureInUse(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {
org.codehaus.groovy.runtime.DefaultGroovyMethods.use(closureInUse.this, IntCat.class, new groovy.lang.Closure<java.lang.Void>(this, this) {
public void doCall(java.lang.Object it) {
IntCat.call(2);
IntCat.call(2, "a");
IntCat.call(2);
IntCat.call(2, "2");
}

public void doCall() {
doCall(null);
}

});
return null;

}

public closureInUse(groovy.lang.Binding binding) {
super(binding);
}
public closureInUse() {
super();
}
}
