package pkg;

public class TestInnerSignature<A,B,C> {
  A a;
  B b;
  C c;

  public TestInnerSignature(A a, @Deprecated B b,C c) {
    this.a = a;
    this.b = b;
    this.c = c;
  }

  public class Inner {
    A a;
    B b;
    C c;

    public Inner(A a, @Deprecated B b, C c) {
      this.a = a;
      this.b = b;
      this.c = c;
    }
  }

  public static class InnerStatic<A,B,C> {
    A a;
    B b;
    C c;

    public InnerStatic(A a, @Deprecated B b, C c) {
      this.a = a;
      this.b = b;
      this.c = c;
    }
  }
}