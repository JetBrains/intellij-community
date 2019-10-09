import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam

def <T extends Closure<Void>> Void bar(T a, @ClosureParams(FirstParam) Closure<Void> b) {
  b(a)
}

bar({}, {})