import groovy.transform.CompileStatic
import groovy.transform.NamedParam
import groovy.transform.NamedParams

@CompileStatic
void namedParams(@NamedParams([
  @NamedParam(value = 'first', type = String, required = true),
  @NamedParam(value = 'last', type = Integer, required = true)
]) Map args, int i) {

  print args.last
}

@CompileStatic
def m() {
  <error descr="Missing required named parameter 'first'"><error descr="Missing required named parameter 'last'">namedParams(1, param: 1)</error></error>
}