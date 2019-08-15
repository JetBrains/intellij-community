import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <U1> void foo(@ClosureParams(value = FromString, options = ["U1"]) Closure<?> c, U1 s) {
  c(s)
}

foo({ it.toUpperCase()}, 's')
foo( {it.doubleValue()}, 1)