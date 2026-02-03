import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SecondParam

def <T> void foo(@ClosureParams(SecondParam) Closure<?> c, T s) {
  c(s)
}

class A {void a() {}}

class B {void b() {}}

void m(A a, B b) {
  foo({ it.a() }, a)
  foo({ it.b() }, b)
}