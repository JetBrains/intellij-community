class Test {
  void foo() {
    new A().f<caret>(Test::foo)
  }
}