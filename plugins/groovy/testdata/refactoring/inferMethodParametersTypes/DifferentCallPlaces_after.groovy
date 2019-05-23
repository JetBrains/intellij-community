import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static def <U0 extends java.util.List<java.lang.Integer>> void foo(U0 self, @ClosureParams(value = FromString, options = ["U0,Integer"]) Closure<?> closure) {
  closure(self, 0)
}


foo([1, 2, 3], {list, ind -> println(list[0])})