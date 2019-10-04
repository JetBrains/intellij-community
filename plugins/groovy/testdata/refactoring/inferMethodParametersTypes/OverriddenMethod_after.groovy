interface A {
  void foo(Collection x)
}

class B implements A {
  @Override
  void foo<caret>(Collection x) {

  }
}