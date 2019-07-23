import groovy.transform.CompileStatic
import groovy.transform.NamedParam
import groovy.transform.NamedVariant

@CompileStatic
@NamedVariant
void namedParams(int i, @NamedParam(value = 'param', required = true) Integer n) {
  print i

}

@CompileStatic
def m() {
  <error descr="Missing required named parameter 'param'">namedParams(1, a :1)</error>
}
