import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <U0 extends Integer> void foo(U0 self, @ClosureParams(value = FromString, options = ["U0,Integer"]) Closure<?> closure ) {
  closure(self, 0)
}


foo(1, {a, ind -> println(a)})