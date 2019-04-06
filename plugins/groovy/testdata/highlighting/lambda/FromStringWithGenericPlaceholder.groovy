import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

public <T> void foo(T t, @ClosureParams(value=FromString,options="java.util.List<T>") Closure cl) { cl.call([t,t]) }

@CompileStatic
def test() {
  foo('hey', (List<String> str) -> {  str.each (it) -> { println it.toUpperCase() } })
}