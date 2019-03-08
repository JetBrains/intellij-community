import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam

public <T> T foo(T arg, @ClosureParams(FirstParam) Closure c) { c.call(arg) }

@CompileStatic
def test() {
  assert foo('a', (it) -> { it.toUpperCase() }) == 'A'
}
