class C {
  void m() throws Exception {
    String s1 = null;
    String s2 = s1, s3 = null;
    System.out.println(s2 + s3);

    AutoCloseable r1 = null;
    try (AutoCloseable r2 = r1; AutoCloseable r3 = null) {
      System.out.println(r2.toString() + r3.toString());
    }
  }

  void n() throws Exception {
    String s1 = null;
    String s2 = s1, s3 = s1;
    System.out.println(s2 + s3);

    AutoCloseable r1 = null;
    try (AutoCloseable r2 = r1; AutoCloseable r3 = r1) {
      System.out.println(r2 + r3);
    }
  }

  int boxing(Long l) {
    long ll = l;
    return (int) ll;
  }

  public int foo() {
    int a = 2;
    int b = a;
    return b;
  }

  public int bar() {
    int b = 3;
    return b;
  }

  public int bar2() throws Exception{
    final Exception b = new Exception();
    throw b;
  }

  public int baz() {
    int a;
    int b = 3;
    a = b;
    return a;
  }

  public int bazoom() {
    final int i = foo();
    bar();
    final int value = i;
    System.out.println(value);
    return 3;
  }

  double time() {
    double time = 0.0, dt = time - 1.0;
    System.out.println(time);
    return dt;
  }

  double time2() {
    double time = 0.0, dt = time - 1.0;
    return time;
  }

  void time3() {
    double time =  0.0, dt = time - 1.0;
    double time2 = time;
    time2 += 1;
  }

  void through() throws Exception {
    Exception e2 = instance(), e3 = new RuntimeException(e2);
    throw e2;
  }

  Exception instance() {
    return null;
  }

  public void neededResourceVariable(java.io.InputStream in) throws java.io.IOException {
    try (java.io.InputStream inn = in) {
      final int read = inn.read();
      // do stuff with in
    }
  }

  int parenthesized() {
    final int  i = 1 + 2;
    return (i);
  }

  void parenthesized2() {
    final RuntimeException  t = new RuntimeException();
    throw (t);
  }

  void parenthesized3(int i) {
    int j = (i);
  }

  void parenthesized4(int k) {
    final int j = 1;
    k = (j);
  }

  void parenthesized5() {
    final int j = 1;
    int k = (j);
    System.out.println(k);
  }

}