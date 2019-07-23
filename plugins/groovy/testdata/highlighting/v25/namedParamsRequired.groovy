import groovy.transform.CompileStatic
import groovy.transform.NamedParam
import groovy.transform.NamedParams

@CompileStatic
void namedParams(@NamedParams([
  @NamedParam(value = 'first', type = String, required = true),
  @NamedParam(value = 'last', type = Integer)
]) Map args, int i) {

  print args.last
}

@CompileStatic
def m() {
  <error descr="Missing required named parameter 'first'">namedParams(1, last: 1)</error>
}