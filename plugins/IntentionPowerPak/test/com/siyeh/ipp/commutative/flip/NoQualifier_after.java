class X {

  void foo(X x) {
    /*1*/new X().foo(this);
  }

}