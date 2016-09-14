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
          boolean b = (B.super.t.s) == s || (B.super.t.s) != null && (B.super.t).s.<caret>equals(s);
          System.out.println(b);
        }
      }.run();
    }
  }
}