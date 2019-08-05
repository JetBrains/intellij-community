import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <V0> void foo(V0 self, @ClosureParams(value = FromString, options = ["V0,? super java.lang.Integer"]) Closure<?> closure ) {
  closure(self, 0)
}


foo(1, {a, ind -> println(a)})
foo('q', {a, ind -> println(a)})