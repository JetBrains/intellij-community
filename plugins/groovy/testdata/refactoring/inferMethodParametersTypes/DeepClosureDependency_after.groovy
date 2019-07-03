import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T0> void foo(@ClosureParams(value = FromString, options = ["T0"]) Closure<?> c, List<? extends T0> s) {
  c(s[0])
}

class A {void a() {}}

class B {void b() {}}

void m(List<A> a, List<B> b) {
  foo({ it.a() }, a)
  foo({ it.b() }, b)
}
