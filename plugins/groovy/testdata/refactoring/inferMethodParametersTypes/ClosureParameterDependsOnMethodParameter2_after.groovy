import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <T0 extends Integer> void foo(T0 self, @ClosureParams(value = FromString, options = ["T0,? super java.lang.Integer"]) Closure<?> closure ) {
  closure(self, 0)
}


foo(1, {a, ind -> println(a)})