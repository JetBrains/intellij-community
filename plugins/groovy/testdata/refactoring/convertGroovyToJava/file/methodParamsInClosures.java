public class X {
public java.lang.Integer foo(int x) {final groovy.lang.Reference<java.lang.Integer> i1 = new groovy.lang.Reference<java.lang.Integer>(x);


return org.codehaus.groovy.runtime.DefaultGroovyMethods.each(1, new groovy.lang.Closure<java.lang.Void>(this, this) {
public void doCall(java.lang.Integer it) {
i1.set(2);
int i = 3;

}

public void doCall() {
doCall(null);
}

});
}

}
