import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

def m1(String o, @ClosureParams(value=SimpleType.class, options="java.lang.String") Closure c) {}
def m1(Double o, @ClosureParams(value=SimpleType.class, options="java.lang.Double") Closure c) {}

@CompileStatic
def m() {
  def a;
  m1<error descr="'m1' in 'SeveralSignatures' cannot be applied to '(java.lang.Object, groovy.lang.Closure<java.lang.Void>)'">(a, (double d) -> println(d))</error>
}
