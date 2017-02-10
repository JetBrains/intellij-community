import java.util.Objects;

class T {
  String s;

  class A {
    T t;
  }

  class B extends A {
    void check(final String s) {
      new Runnable() {
        @Override
        public void run() {
          boolean b = Objects.equals(B.super.t.s, s);
          System.out.println(b);
        }
      }.run();
    }
  }
}