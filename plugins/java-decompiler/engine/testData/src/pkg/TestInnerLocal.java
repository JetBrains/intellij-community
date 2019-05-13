package pkg;

public class TestInnerLocal {
  public static void testStaticMethod() {
    class Inner {
      final String x;
      public Inner(@Deprecated String x) {
        this.x = x;
      }
    }
    new Inner("test");
    new Inner1Static("test");
    new Inner1Static.Inner2Static("test");
  }

  public void testMethod() {
    class Inner {
      final String x;
      public Inner(@Deprecated String x) {
        this.x = x;
      }
    }
    new Inner("test");
    new Inner1Static("test");
    new Inner1("test");
    new Inner1Static.Inner2Static("test");
  }

  class Inner1 {
    final String x;
    public Inner1(@Deprecated String x) {
      this.x = x;
    }
  }

  static class Inner1Static {
    final String x;
    public Inner1Static(@Deprecated String x) {
      this.x = x;
    }

    public static class Inner2Static {
      final String x;
      public Inner2Static(@Deprecated String x) {
        this.x = x;
      }
    }
  }
}