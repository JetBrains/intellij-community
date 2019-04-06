import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

public <T> void foo(T t, @ClosureParams(value=FromString,options="T") Closure cl) { cl.call(t) }

@CompileStatic
def test() {
  foo('hey', (it) -> { println it.toUpperCase() })
}
