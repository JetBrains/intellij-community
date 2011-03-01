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
}