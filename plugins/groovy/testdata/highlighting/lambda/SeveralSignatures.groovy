import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

def m1(String o, @ClosureParams(value=SimpleType.class, options="java.lang.String") Closure c) {}
def m1(Double o, @ClosureParams(value=SimpleType.class, options="java.lang.Double") Closure c) {}

@CompileStatic
def m() {
  def a;
  m1<warning descr="Method call is ambiguous">(a, (double d) -> println(d))</warning>
}
