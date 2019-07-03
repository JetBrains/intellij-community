import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <T0> void foo(@ClosureParams(value = FromString, options = ["T0"]) Closure<?> c, T0 s) {
  c(s)
}

class A {void a() {}}

class B {void b() {}}

void m(A a, B b) {
  foo({ it.a() }, a)
  foo({ it.b() }, b)
}