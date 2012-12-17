java.util.ArrayList<java.lang.Integer> list = new java.util.ArrayList<java.lang.Integer>(java.util.Arrays.asList(1, 2, 3));
org.codehaus.groovy.runtime.DefaultGroovyMethods.each(list, new groovy.lang.Closure<java.lang.Void>(this, this) {
public void doCall(java.lang.Object it) {
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(closure.this, it);
}

public void doCall() {
doCall(null);
}

});
