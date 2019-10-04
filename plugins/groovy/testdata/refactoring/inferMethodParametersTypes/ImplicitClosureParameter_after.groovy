import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

void foo(@ClosureParams(value = SimpleType, options = ['? super java.lang.Integer']) Closure<Integer> c) {
  Integer e = c(2)
}

foo({it+1})
