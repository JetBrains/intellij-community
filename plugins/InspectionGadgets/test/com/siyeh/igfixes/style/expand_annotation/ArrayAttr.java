class T {
  @interface A {
    String[] value();
  }

  @A({<caret>"a"})
  void bar() {
  }
}