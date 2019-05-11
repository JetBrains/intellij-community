def void foo(c, s) {
  c(s)
}

class A {void a() {}}

class B {void b() {}}

void m(A a, B b) {
  foo({ it.a() }, a)
  foo({ it.b() }, b)
}