import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <Y0> void foo(@ClosureParams(value = FromString, options = ["Y0"]) Closure<?> c, List<? extends Y0> s) {
  c(s[0])
}

class A {void a() {}}

class B {void b() {}}

void m(List<A> a, List<B> b) {
  foo({ it.a() }, a)
  foo({ it.b() }, b)
}
