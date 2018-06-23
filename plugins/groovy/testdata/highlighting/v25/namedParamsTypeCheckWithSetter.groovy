import groovy.transform.CompileStatic
import groovy.transform.NamedDelegate
import groovy.transform.NamedVariant

class E {
  private int a
  void setT(int t){}
}

@NamedVariant
String foo(@NamedDelegate E e) {
  null
}

@CompileStatic
def m() {
  foo(t: <error descr="Type of argument 't' can not be 'String'">''</error>)
}