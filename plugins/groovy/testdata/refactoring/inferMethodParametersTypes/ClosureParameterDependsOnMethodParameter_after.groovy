import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T0 extends String> void foo(@ClosureParams(value = FromString, options = ["T0"]) Closure<?> c, T0 s) {
  c(s)
}

foo({ it.toUpperCase()}, 's')