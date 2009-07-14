package pack1;

public class C {
  public static void foo() {
    bar();
  }

  private static void bar() {
    foo();
    ourField = 11;
  }

  public static int ourField = 10
}