interface A {
  void foo(Collection x)
}

class B implements A {
  void foo<caret>(Collection x) {

  }
}