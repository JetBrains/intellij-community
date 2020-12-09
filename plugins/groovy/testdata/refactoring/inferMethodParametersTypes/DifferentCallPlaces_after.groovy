import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <T extends ArrayList<Integer>> void foo(T self, @ClosureParams(value = FromString, options = ["T,? super java.lang.Integer"]) Closure<?> closure) {
  closure(self, 0)
}


foo([1, 2, 3], {list, ind -> println(list[0])})