import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

void foo(@ClosureParams(value = SimpleType, options = ['java.lang.Integer']) Closure<? extends Integer> c) {
  Integer e = c(2)
}

foo({it+1})
