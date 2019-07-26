import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <T1 extends Integer> void foo(List<? extends T1> self, @ClosureParams(value = FromString, options = ["T1,java.lang.Integer"]) Closure<?> closure) {
  final Object[] args = new Object[2]
  closure.call(self[0], 0)
}


foo([1, 2, 3], {list, ind -> println(list)})