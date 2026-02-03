public class Abc {
public void foo(final int x) {
org.codehaus.groovy.runtime.DefaultGroovyMethods.times(2, new groovy.lang.Closure<java.lang.Integer>(this, this) {
public java.lang.Integer doCall(java.lang.Integer it) {return x;}

public java.lang.Integer doCall() {
return doCall(null);
}

});
}

}
