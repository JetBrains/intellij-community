import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

void foo(@ClosureParams(value=FromString,options="java.util.List<java.lang.String>") Closure cl) { cl.call(['foo']) }

@CompileStatic
def test() {
  foo (List<String> str) -> {  str.each (it) -> { println it.toUpperCase() } }
}
