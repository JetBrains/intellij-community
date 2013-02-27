class Test<T> {
  static void foo() {}
}

class Bar {
  void test() {
    Runnable runnable = Test:<caret>:foo;
  }
}