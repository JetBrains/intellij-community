import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <V0 extends String> void foo(@ClosureParams(value = FromString, options = ["V0"]) Closure<?> c, V0 s) {
  c(s)
}

foo({ it.toUpperCase()}, 's')