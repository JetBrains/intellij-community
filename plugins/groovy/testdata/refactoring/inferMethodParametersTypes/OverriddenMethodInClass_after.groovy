class A {
  void foo(Collection x) {

  }
}

class B extends A {
  @Override
  void foo<caret>(Collection x) {

  }
}