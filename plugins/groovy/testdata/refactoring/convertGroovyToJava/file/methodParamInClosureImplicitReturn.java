public class methodParamInClosureImplicitReturn extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new methodParamInClosureImplicitReturn(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {
return null;

}

public void foo(int x) {
final groovy.lang.Reference<java.lang.Integer> i1 = new groovy.lang.Reference<java.lang.Integer>(x);
org.codehaus.groovy.runtime.DefaultGroovyMethods.each(new java.util.ArrayList<java.lang.Integer>(java.util.Arrays.asList(1, 2, 3)), new groovy.lang.Closure<java.lang.Number>(this, this) {
public java.lang.Number doCall(java.lang.Object it) {
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(methodParamInClosureImplicitReturn.this, i1.get());
i1.set(i1.get() + 1);
return i1.get();
}

public java.lang.Number doCall() {
return doCall(null);
}

});
org.codehaus.groovy.runtime.DefaultGroovyMethods.each(new java.util.ArrayList<java.lang.Integer>(java.util.Arrays.asList(1, 2, 3)), new groovy.lang.Closure<java.lang.Integer>(this, this) {
public java.lang.Integer doCall(java.lang.Object it) {
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(methodParamInClosureImplicitReturn.this, i1.get());
i1.set(i1.get()++);
return i1.get();
}

public java.lang.Integer doCall() {
return doCall(null);
}

});
print(i1.get());
}

public methodParamInClosureImplicitReturn(groovy.lang.Binding binding) {
super(binding);
}
public methodParamInClosureImplicitReturn() {
super();
}
}
