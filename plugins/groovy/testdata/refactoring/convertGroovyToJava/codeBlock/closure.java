java.util.ArrayList<java.lang.Integer> list = new java.util.ArrayList<java.lang.Integer>(java.util.Arrays.asList(1, 2, 3));
org.codehaus.groovy.runtime.DefaultGroovyMethods.each(list, new groovy.lang.Closure<java.lang.Void>(this, this) {
public java.lang.Object doCall(java.lang.Object it) {
print(it);
}

public java.lang.Object doCall() {
return doCall(null);
}

});
