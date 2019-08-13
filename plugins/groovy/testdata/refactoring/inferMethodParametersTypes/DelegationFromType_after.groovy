import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

Object foo(@DelegatesTo(Integer) @ClosureParams(value = SimpleType, options = ['?']) Closure<Double> cl) {
  (null as C<Integer>).m(cl)
}

class C<T> {
  def m(@DelegatesTo(type = "T") Closure cl) {}
}

foo {
  doubleValue()
}