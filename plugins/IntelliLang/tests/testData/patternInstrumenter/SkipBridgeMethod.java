import org.intellij.lang.annotations.Pattern;

public class SkipBridgeMethod {
  public static void bridgeMethod() {
    A a = new B();
    a.get("-");
  }

  private static class A {
    Object get(String s) { return s; }
  }

  private static class B extends A {
    @Override
    String get(@Pattern("\\d+") String s) { return s; }
  }
}