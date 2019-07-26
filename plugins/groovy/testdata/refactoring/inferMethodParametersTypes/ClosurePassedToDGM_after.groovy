import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <W1> boolean foo(List<? extends W1> list, @ClosureParams(value = FromString, options = ["W1"]) Closure<?> cl) {
  list.every cl
}

foo([1]) {it % 2 == 0}
foo(['q']) {it.reverse() == it}

