public class X extends groovy.lang.GroovyObjectSupport implements groovy.lang.GroovyObject {
public groovy.lang.Closure<java.lang.Integer> getFoo() {
 return foo;
}
public void setFoo(groovy.lang.Closure<java.lang.Integer> foo) {
this.foo = foo;
}
private groovy.lang.Closure<java.lang.Integer> foo = new groovy.lang.Closure<java.lang.Integer>(this, this) {
public java.lang.Integer doCall(int x) {
final groovy.lang.Reference<java.lang.Integer> i1 = new groovy.lang.Reference<java.lang.Integer>(x);
return org.codehaus.groovy.runtime.DefaultGroovyMethods.each(1, new groovy.lang.Closure<java.lang.Void>(this, this) {
public void doCall(java.lang.Object it) {
i1.set(2);
int i = 3;
}

public void doCall() {
doCall(null);
}

});
}

};
}
