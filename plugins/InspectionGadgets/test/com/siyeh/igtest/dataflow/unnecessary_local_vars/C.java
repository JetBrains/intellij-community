class C {
  void m() throws Exception {
    String s1 = null;
    String s2 = s1, s3 = null;
    System.out.println(s2 + s3);

    AutoCloseable r1 = null;
    try (AutoCloseable r2 = r1, AutoCloseable r3 = null) {
      System.out.println(r2 + r3);
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

}