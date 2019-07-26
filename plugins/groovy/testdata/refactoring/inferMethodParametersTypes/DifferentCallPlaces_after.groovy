import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <T1 extends List<? extends Integer>> void foo(T1 self, @ClosureParams(value = FromString, options = ["T1,java.lang.Integer"]) Closure<?> closure) {
  closure(self, 0)
}


foo([1, 2, 3], {list, ind -> println(list[0])})