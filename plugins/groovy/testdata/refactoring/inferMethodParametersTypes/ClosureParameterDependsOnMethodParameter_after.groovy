import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <X0> void foo(@ClosureParams(value = FromString, options = ["X0"]) Closure<?> c, X0 s) {
  c(s)
}

foo({ it.toUpperCase()}, 's')
foo( {it.doubleValue()}, 1)