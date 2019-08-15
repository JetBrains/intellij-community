import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <U1 extends List<Integer>> void foo(U1 self, @ClosureParams(value = FromString, options = ["U1,? super java.lang.Integer"]) Closure<?> closure) {
  closure(self, 0)
}


foo([1, 2, 3], {list, ind -> println(list[0])})