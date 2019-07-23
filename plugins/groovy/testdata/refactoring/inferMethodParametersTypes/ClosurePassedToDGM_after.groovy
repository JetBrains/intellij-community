import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <V1> boolean foo(List<? extends V1> list, @ClosureParams(value = FromString, options = ["V1"]) Closure<?> cl) {
  list.every cl
}

foo([1]) {it % 2 == 0}
foo(['q']) {it.reverse() == it}

