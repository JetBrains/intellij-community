import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

Object foo(@ClosureParams(value = SimpleType, options = ['? super java.lang.Integer', '? super java.lang.Integer']) Closure<?> cl) {
  cl(1, 2)
}

def m(Closure cl) {
  foo(cl)
}