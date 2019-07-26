import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <Y0 extends List<? extends Integer>> void foo(Y0 self, @ClosureParams(value = FromString, options = ["Y0,java.lang.Integer"]) Closure<?> closure) {
  closure(self, 0)
}


foo([1, 2, 3], {list, ind -> println(list[0])})