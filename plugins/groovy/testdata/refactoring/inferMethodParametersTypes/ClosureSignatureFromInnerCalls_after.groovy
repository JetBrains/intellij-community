import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

Object foo(@ClosureParams(value = FromString, options = ["java.lang.Integer,java.lang.Integer"]) Closure<?> cl) {
  cl(1, 2)
}

def m(Closure cl) {
  foo(cl)
}