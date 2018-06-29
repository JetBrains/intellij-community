class Test {
  public void foo() {
    new A().f<caret>(Test::foo);
  }
}