import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T> boolean foo(ArrayList<? extends T> list, @ClosureParams(value = FromString, options = ["T"]) Closure<Boolean> cl) {
  list.every cl
}

foo([1]) {it % 2 == 0}
foo(['q']) {it.reverse() == it}

