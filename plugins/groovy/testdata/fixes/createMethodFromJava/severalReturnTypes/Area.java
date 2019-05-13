class Test {
  public void foo(A a) {
    Object x = a.<caret>bar();
    String s = a.bar();
  }
}