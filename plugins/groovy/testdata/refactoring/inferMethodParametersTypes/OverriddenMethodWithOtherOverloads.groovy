class A {
  void foo(String x) {

  }

  void foo(Collection x) {

  }
}

class B extends A {
  @Override
  void foo(String x) {

  }

  @Override
  void foo<caret>(x) {

  }
}