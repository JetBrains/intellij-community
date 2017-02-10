class T {
  static class A {
    final String n;

    A(String n) {
      this.n = n;
    }

    void f(String s) {
      System.out.println(s + " " + n);
    }
  }

  static class B extends A {
    B(String n) {
      super(n);
    }

    void f2(String s) {
      new Thread(new Runnable() {
          @Override
          public void run() {
              B.this.f(s + " " + B.super.n);
          }
      }).start();
    }
  }

  public static void main(String[] args) {
    new A("a").f("run");
    new B("b").f2("run");
  }
}