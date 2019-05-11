def void foo(c, s) {
  c(s[0])
}

class A {void a() {}}

class B {void b() {}}

void m(List<A> a, List<B> b) {
  foo({ it.a() }, a)
  foo({ it.b() }, b)
}
