import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FirstParam
import groovy.transform.stc.FromString

def <T extends U, U extends Closure<Void>> Void bar(@ClosureParams(value = FromString, options = ["U"]) T a, @ClosureParams(FirstParam) Closure<Void> b) {
  b(a)
}

bar({}, {})