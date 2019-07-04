def <T0 extends X> Object foo(List<? extends T0> a) {
  a[0].f()
}

void m(List<A> la, List<B> lb) {
  foo(la)
  foo(lb)
}

interface X {
  void f();
}

interface Y {
  void g();
}

class A implements X, Y {
  void f() {}

  void g() {}
}

class B implements X, Y {
  void f() {}

  void g() {}
}
