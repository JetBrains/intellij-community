import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

void foo(@ClosureParams(value = FromString, options = ["Integer"]) Closure<? extends Integer> c) {
  Integer e = c(2)
}

foo({it+1})
