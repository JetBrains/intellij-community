class Test {
  private abstract class InnerTest {
    public InnerTest(String s) { }
    public abstract void run();
  }

  public void foo(String s) {
    bar(new InnerTest(<warning descr="Hard coded string literal: \"Literal\"">"Literal"</warning>) { public void run() { } });
  }

  public void bar(InnerTest t) {

  }
}
