import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <T> void foo(T self, @ClosureParams(value = FromString, options = ["T,? super java.lang.Integer"]) Closure<?> closure ) {
  closure(self, 0)
}


foo(1, {a, ind -> println(a)})
foo('q', {a, ind -> println(a)})