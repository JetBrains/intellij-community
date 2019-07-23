import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <W0 extends String> void foo(@ClosureParams(value = FromString, options = ["W0"]) Closure<?> c, W0 s) {
  c(s)
}

foo({ it.toUpperCase()}, 's')