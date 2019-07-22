import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <W0 extends List<Integer>> void foo(W0 self, @ClosureParams(value = FromString, options = ["W0,java.lang.Integer"]) Closure<?> closure) {
  closure(self, 0)
}


foo([1, 2, 3], {list, ind -> println(list[0])})