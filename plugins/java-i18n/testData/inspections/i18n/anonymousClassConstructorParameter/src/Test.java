class Test {
  private class InnerTest {
    public InnerTest(String s) { }
    public abstract void run();
  }

  public void foo(String s) {
    bar(new InnerTest("Literal") { public void run() { } });
  }

  public void bar(InnerTest t) {

  }
}
