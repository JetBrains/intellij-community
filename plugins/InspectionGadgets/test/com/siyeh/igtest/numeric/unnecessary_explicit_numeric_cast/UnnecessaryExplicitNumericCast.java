package com.siyeh.igtest.numeric.unnecessary_explicit_numeric_cast;




public class UnnecessaryExplicitNumericCast {

    void a(byte b) {
        double d = (double) 1;
        d = (double) 1.0f;
        d = (double) b;
        char c = (char) 1;
        b = (int)7;
    }

    double b(int a, byte b) {
        return (double)a * (double) b;
    }

    public static void main(String[] args) {
        int i = 10;

        double d = 123.0 / (456.0 * (double) i);
    }

    void unary() {
        byte b = 2;
        int a[] = new int[(int)b];
        final int c = a[((int) b)];
        int[] a2 = new int[]{(int)b};
        int[] a3 = {(int)b};
        final int result = (int) b << 1;
        c((int)b);
        new UnnecessaryExplicitNumericCast((long)b);
    }

    void c(int i) {}
    UnnecessaryExplicitNumericCast(long i) {}
}
