import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <U1> boolean foo(List<? extends U1> list, @ClosureParams(value = FromString, options = ["U1"]) Closure<? extends Boolean> cl) {
  list.every cl
}

foo([1]) {it % 2 == 0}
foo(['q']) {it.reverse() == it}

