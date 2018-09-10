class C {
  void foo() {
    try {
      bar();
    } catch (Ex1 | Ex2 e) {
      // unique comment
    }
  }

  void bar() throws Ex1, Ex2 {}

  static class Ex1 extends Exception {}
  static class Ex2 extends Exception {}
}