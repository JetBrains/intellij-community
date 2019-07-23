import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <X0 extends List<Integer>> void foo(X0 self, @ClosureParams(value = FromString, options = ["X0,java.lang.Integer"]) Closure<?> closure) {
  closure(self, 0)
}


foo([1, 2, 3], {list, ind -> println(list[0])})