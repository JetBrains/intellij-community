public class methodParamInClosureImplicitReturn extends groovy.lang.Script {
public static void main(java.lang.String[] args) {
new methodParamInClosureImplicitReturn(new groovy.lang.Binding(args)).run();
}

public java.lang.Object run() {

return null;

}

public void foo(int x) {final groovy.lang.Reference<java.lang.Integer> i = new groovy.lang.Reference<java.lang.Integer>(x);

org.codehaus.groovy.runtime.DefaultGroovyMethods.each(new java.util.ArrayList<java.lang.Integer>(java.util.Arrays.asList(1, 2, 3)), new groovy.lang.Closure<java.lang.Integer>(this, this) {
public java.lang.Integer doCall(java.lang.Integer it) {
print(i.get());
return setGroovyRef(i, i.get() + 1);
}

public java.lang.Integer doCall() {
return doCall(null);
}

});

org.codehaus.groovy.runtime.DefaultGroovyMethods.each(new java.util.ArrayList<java.lang.Integer>(java.util.Arrays.asList(1, 2, 3)), new groovy.lang.Closure<java.lang.Integer>(this, this) {
public java.lang.Integer doCall(java.lang.Integer it) {
print(i.get());
i.set(i.get()++);
return i.get();
}

public java.lang.Integer doCall() {
return doCall(null);
}

});

print(i.get());
}

public methodParamInClosureImplicitReturn(groovy.lang.Binding binding) {
super(binding);
}
public methodParamInClosureImplicitReturn() {
super();
}
private static <T> T setGroovyRef(groovy.lang.Reference<T> ref, T newValue) {
ref.set(newValue);
return newValue;
}}
