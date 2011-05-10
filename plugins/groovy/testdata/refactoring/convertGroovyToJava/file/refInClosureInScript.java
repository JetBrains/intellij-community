public class refInClosureInScript extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new refInClosureInScript(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {
final groovy.lang.Reference<java.lang.Integer> foo = new groovy.lang.Reference<java.lang.Integer>(2);
org.codehaus.groovy.runtime.DefaultGroovyMethods.times(3, new groovy.lang.Closure<java.lang.Void>(this, this) {
public void doCall(java.lang.Object it) {
foo.set(foo.get().next());
foo.set(foo.get().plus(2));
foo.set(foo.get() - 1);
foo.set(4);
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(refInClosureInScript.this, foo.get());
}

public void doCall() {
doCall(null);
}

});
return null;

}

public refInClosureInScript(groovy.lang.Binding binding) {
super(binding);
}
public refInClosureInScript() {
super();
}
}
