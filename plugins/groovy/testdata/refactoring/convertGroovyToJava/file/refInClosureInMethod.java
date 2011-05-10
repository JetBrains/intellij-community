public class X extends groovy.lang.GroovyObjectSupport implements groovy.lang.GroovyObject {
public void foo() {
final groovy.lang.Reference<java.lang.Integer> ab = new groovy.lang.Reference<java.lang.Integer>(4);
org.codehaus.groovy.runtime.DefaultGroovyMethods.each(X.this, new groovy.lang.Closure<java.lang.Object>(this, this) {
public java.lang.Object doCall(java.lang.Object it) {
return org.codehaus.groovy.runtime.DefaultGroovyMethods.each(X.this, new groovy.lang.Closure<java.lang.Integer>(this, this) {
public java.lang.Integer doCall(java.lang.Object it) {
return ab.set(2);
}

public java.lang.Integer doCall() {
return doCall(null);
}

});
}

public java.lang.Object doCall() {
return doCall(null);
}

});
org.codehaus.groovy.runtime.DefaultGroovyMethods.print(X.this, ab.get());
}

}
