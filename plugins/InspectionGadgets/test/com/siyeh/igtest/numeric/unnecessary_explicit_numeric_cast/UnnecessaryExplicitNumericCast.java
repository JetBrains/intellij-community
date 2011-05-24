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
}
