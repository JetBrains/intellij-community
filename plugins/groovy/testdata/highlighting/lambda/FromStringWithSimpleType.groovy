import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

void foo(@ClosureParams(value=FromString,options="java.lang.String") Closure cl) { cl.call('foo') }

@CompileStatic
def test() {
  foo (String str) -> { println str.toUpperCase()}
}