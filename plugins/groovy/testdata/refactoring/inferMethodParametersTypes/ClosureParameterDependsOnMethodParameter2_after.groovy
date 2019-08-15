import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <V1> void foo(V1 self, @ClosureParams(value = FromString, options = ["V1,? super java.lang.Integer"]) Closure<?> closure ) {
  closure(self, 0)
}


foo(1, {a, ind -> println(a)})
foo('q', {a, ind -> println(a)})