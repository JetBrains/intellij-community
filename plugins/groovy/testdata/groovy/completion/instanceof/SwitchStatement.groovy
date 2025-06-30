class Main {
  static class A{}
  static class B extends A{}
  void foo() {
    A a = new B()
    switch(a instanceof B someName) {
      case true:
         some<caret>
         break
      case false:
         break
    }
  }
}