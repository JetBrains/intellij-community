import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T1> boolean foo(List<? extends T1> list, @ClosureParams(value = FromString, options = ["T1"]) Closure<?> cl) {
  list.every cl
}

foo([1]) {it % 2 == 0}
foo(['q']) {it.reverse() == it}

