public class return2 extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new return2(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {

return null;

}

public boolean foo() {
java.lang.Integer a = 5;

org.codehaus.groovy.runtime.DefaultGroovyMethods.times(a, new groovy.lang.Closure<java.lang.Integer>(this, this) {
public java.lang.Integer doCall(java.lang.Integer it) {
if (it == 2)return 5;
}

public java.lang.Integer doCall() {
return doCall(null);
}

});

}

public return2(groovy.lang.Binding binding) {
super(binding);
}
public return2() {
super();
}
}
