import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString

def <W0> void foo(@ClosureParams(value = FromString, options = ["W0"]) Closure<?> c, W0 s) {
  c(s)
}

class A {void a() {}}

class B {void b() {}}

void m(A a, B b) {
  foo({ it.a() }, a)
  foo({ it.b() }, b)
}