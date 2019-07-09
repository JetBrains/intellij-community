import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <F extends T0, T0> boolean foo(Iterable<F> list, @ClosureParams(value = FromString, options = ["T0"]) Closure<?> cl) {
  list.every(cl)
}

foo([1]) {it % 2 == 0}
foo(['q']) {it.reverse() == it}

