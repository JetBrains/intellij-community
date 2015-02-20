package pkg;

import java.lang.Exception;
import java.lang.Override;
import java.lang.Runnable;

public abstract class TestAnonymousClass {
  void foo(int i)
    throws Exception {
    if (i > 0) {
      I r = new I() {
        public void foo() throws Exception {
          int a = 5;
          int b = 5;
        }
      };
      r.foo();
    }
    else {
      final int x =5;
      System.out.println(x);
    }
  }

  public static final Runnable R3 = new Runnable() {
    @Override
    public void run() {
      int a =5;
      int b =5;
    }
  };


  void boo() {
    int a =5;
  }

  void zoo() {
    int a =5;
  }

  public static final Runnable R = new Runnable() {
    @Override
    public void run() {
      int a =5;
      int b =5;
    }
  };

  public static final Runnable R1 = new Runnable() {
    @Override
    public void run() {
      int a =5;
      int b =5;
    }
  };

  interface I {
    void foo() throws Exception;
  }

  private static class Inner {
    private static Runnable R_I = new Runnable() {
      @Override
      public void run() {
        int a =5;
        int b =5;
      }
    };
  }

  private final InnerRecursive y = new InnerRecursive(new InnerRecursive(null) {
    @Override
    void foo() {
      int a =5;
      int b =5;
      int g =5;
    }
  }) {
    int v =5;
    int t =5;
    int j =5;
    int o =5;
  };


  private final InnerRecursive x = new InnerRecursive(new InnerRecursive(null) {
    @Override
    void foo() {
      int a =5;
      int b =5;
      int g =5;
    }
  }) {
    int v =5;
    int t =5;
    int j =5;
    int o =5;
  };

  static class InnerRecursive {
    InnerRecursive r;

    public InnerRecursive(InnerRecursive r) {
      this.r = r;
    }

    void foo() {

    }
  }
}
