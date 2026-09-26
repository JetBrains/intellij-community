import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SecondParam

def <T> void foo(@ClosureParams(SecondParam) Closure<?> c, T s) {
  c(s)
}

foo({ it.toUpperCase()}, 's')
foo( {it.doubleValue()}, 1)