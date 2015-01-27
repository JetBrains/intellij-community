package com.siyeh.igtest.numeric.unnecessary_explicit_numeric_cast;




public class UnnecessaryExplicitNumericCast {

    void a(byte b) {
        double d = (<warning descr="'1' unnecessarily cast to 'double'">double</warning>) 1;
        d = (<warning descr="'1.0f' unnecessarily cast to 'double'">double</warning>) 1.0f;
        d = (<warning descr="'b' unnecessarily cast to 'double'">double</warning>) b;
        char c = (<warning descr="'1' unnecessarily cast to 'char'">char</warning>) 1;
        b = (int)7;
    }

    double b(int a, byte b) {
        return (<warning descr="'a' unnecessarily cast to 'double'">double</warning>)a * (<warning descr="'b' unnecessarily cast to 'double'">double</warning>) b;
    }

    public static void main(String[] args) {
        int i = 10;

        double d = 123.0 / (456.0 * (<warning descr="'i' unnecessarily cast to 'double'">double</warning>) i);
    }

    void unary() {
        byte b = 2;
        int a[] = new int[(<warning descr="'b' unnecessarily cast to 'int'">int</warning>)b];
        final int c = a[((<warning descr="'b' unnecessarily cast to 'int'">int</warning>) b)];
        int[] a2 = new int[]{(<warning descr="'b' unnecessarily cast to 'int'">int</warning>)b};
        int[] a3 = {(<warning descr="'b' unnecessarily cast to 'int'">int</warning>)b};
        final int result = (<warning descr="'b' unnecessarily cast to 'int'">int</warning>) b << 1;
        c((<warning descr="'b' unnecessarily cast to 'int'">int</warning>)b);
        new UnnecessaryExplicitNumericCast((<warning descr="'b' unnecessarily cast to 'long'">long</warning>)b);
    }

    void c(int i) {}
    UnnecessaryExplicitNumericCast(long i) {}

    void c(int cols, int no) {
      int rows = (int) Math.ceil((double) no / cols);
    }

    void source() {
      target((int)'a');
      target2((<warning descr="''b'' unnecessarily cast to 'int'">int</warning>)'b');
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
      final long l = (<warning descr="'i()' unnecessarily cast to 'long'">long</warning>) i() * 9L;
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

}
