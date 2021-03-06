public class resolveMethodInsideClosure extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new resolveMethodInsideClosure(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {

return null;

}

public java.util.ArrayList<java.lang.Integer> foo() {
java.util.ArrayList<java.lang.Integer> list = new java.util.ArrayList<java.lang.Integer>(java.util.Arrays.asList(1, 2, 3));
return org.codehaus.groovy.runtime.DefaultGroovyMethods.each(list, new groovy.lang.Closure<java.util.ArrayList<java.lang.Integer>>(this, this) {
public java.util.ArrayList<java.lang.Integer> doCall(java.lang.Integer it) {
return foo();
}

public java.util.ArrayList<java.lang.Integer> doCall() {
return doCall(null);
}

});
}

public resolveMethodInsideClosure(groovy.lang.Binding binding) {
super(binding);
}
public resolveMethodInsideClosure() {
super();
}
}
