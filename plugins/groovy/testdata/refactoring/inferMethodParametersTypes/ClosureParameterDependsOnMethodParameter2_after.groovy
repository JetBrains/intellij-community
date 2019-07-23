import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

static <X0 extends Integer> void foo(X0 self, @ClosureParams(value = FromString, options = ["X0,java.lang.Integer"]) Closure<?> closure ) {
  closure(self, 0)
}


foo(1, {a, ind -> println(a)})