import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static void foo(List<Integer> self, @ClosureParams(value = FromString, options = ["List<java.lang.Integer>,Integer"]) Closure<?> closure) {
  closure(self, 0)
}


foo([1, 2, 3], {list, ind -> println(list[0])})