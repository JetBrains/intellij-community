class C {
  void foo(String x) {}
  void foo(Integer x) {}
}

class D extends C {

  @Override
  void foo(y) {

  }
}

void m(D d) {
  d.foo(1)
}