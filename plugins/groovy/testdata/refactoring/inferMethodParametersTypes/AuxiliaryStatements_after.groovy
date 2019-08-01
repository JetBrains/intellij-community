import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <T0 extends Integer> void foo(List<T0> self, @ClosureParams(value = FromString, options = ["T0,? super java.lang.Integer"]) Closure<?> closure) {
  final Object[] args = new Object[2]
  closure(self[0], 0)
}


foo([1, 2, 3], {list, ind -> println(list)})

def m(Closure cl) {
  foo([1, 2, 3], cl)
}