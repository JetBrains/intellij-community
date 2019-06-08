class X {

  void method(Object o) {
    ((X)o).foo(this));
  }

  void foo(X x) {

  }

}