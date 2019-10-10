import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

Void foo(@ClosureParams(value = SimpleType, options = ['? super java.lang.Integer']) Closure<Void> a) {
  int x = 1
  a(x)
}

foo {}