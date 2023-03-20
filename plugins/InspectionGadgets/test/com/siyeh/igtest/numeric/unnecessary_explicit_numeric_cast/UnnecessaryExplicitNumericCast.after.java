package com.siyeh.igtest.numeric.unnecessary_explicit_numeric_cast;




public class UnnecessaryExplicitNumericCast {

    void a(byte b) {
        double d = <caret>1;
        d = 1.0f;
        d = b;
        char c = 1;
        b = (int)7;
    }

    double b(int a, byte b) {
        return (double)a * b;
    }

    public static void main(String[] args) {
        int i = 10;

        double d = 123.0 / (456.0 * i);
    }

    void unary() {
        byte b = 2;
        int a[] = new int[b];
        final int c = a[b];
        int[] a2 = new int[]{b};
        int[] a3 = {b};
        final int result = b << 1;
        c(b);
        new UnnecessaryExplicitNumericCast(b);
    }

    void c(int i) {}
    UnnecessaryExplicitNumericCast(long i) {}

    void c(int cols, int no) {
      int rows = (int) Math.ceil((double) no / cols);
    }

    void source() {
      target((int)'a');
      target2('b');
    }
    void target(int c) {}
    void target(char c) {}
    void target2(int d) {}

    void foo() {
        float x = 2;
        target((int) x);  // this line complains: 'x' unnecessarily cast to 'int'
    }

    void a(float angleFromTo) {
      float f = (float) Math.cos(0.5) * 1.0f; // necessary
      final long l = i() * 9L;
      float angle2 = angleFromTo + (float) (Math.PI / 2);
    }

    int i() {
      return 10;
    }

    boolean redundantTypeCast(long l) {
      return 0L == (long)l;
    }

  void necessary() {
    char[] keyChar = {'\t', '\n', '\r', '\f', 'a', '0'};
    for (char cc : keyChar) {
      String result;
      if (cc < 28) {
        result = "Ascii " + (int)cc;
      }
      else {
        result = "k " + cc + " (" + (int)cc + ')';
      }
      System.out.println(result);
    }
  }

  public static long negate(int x) {
    return -(long)x;
  }

  public static int negate2(int x) {
      return -x;
  }

}
enum Numeric {
  A((byte)10);

  Numeric(byte b) {}
}
class S {

  static void doSomething() {
    //   V --- this cast is reported as unnecessary
    if ( (int) whatever() < 0 ) {
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T whatever() {
    return (T) (Object) 0;
  }

  void polyadic() {
    int a=1;
    int b=2;
    System.out.println(((double) a) / b / 10.0);
    double c = 3.5;
    System.out.println(a / c / 10.0);
    System.out.println(19/ (double)a / c / 10.0);
  }

  private static void foo(int i) {
  }

  void bar(int i) {
    foo(0);
    foo((short)i);
    int bar = 123;
    boolean[] booleans = new boolean[6];
    byte[] bytes = new byte[(int) 2];
    var v = (short) 666;
  }

  void noWarnOnRedCode() {
    foo((long)0);
    int x = (long)0;
  }
}