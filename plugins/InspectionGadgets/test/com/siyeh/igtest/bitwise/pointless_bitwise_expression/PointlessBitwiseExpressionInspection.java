package com.siyeh.igtest.bitwise.pointless_bitwise_expression;

public class PointlessBitwiseExpressionInspection {
    private static final int ZERO = 0;

    public static void main(String[] args) {
        final int i = 1;
        int j = i & 0;
        System.out.println(j);
        j = 3;
        int k = j | 0;
        System.out.println(k);
        k = j ^ 0;
        System.out.println(k);
        k = j << 0;
        System.out.println(k);
        k = j >> 0;
        System.out.println(k);
        k = j >>> 0;
        System.out.println(k);
        k = j >>> ZERO;
        System.out.println(k);
    }

    public static void main3(String[] args) {
        final int i = 1;
        int j = 0 & i;
        System.out.println(j);
        j = 3;
        int k = 0 | j;
        System.out.println(k);
        k = 0 ^ j;
        System.out.println(k);

    }

    public static void main2(String[] args) {
        final int i = 1;
        int j = i & 0xffffffff;
        System.out.println(j);
        j = 3;
        int k = j | 0xffffffff;
        System.out.println(k);
        j = 6;
        k = j ^ 0xffffffff;
        System.out.println(k);


    }

    public static void main4(String[] args) {
        final int i = 1;
        int j = 0xffffffff & i;
        System.out.println(j);
        j = 3;
        int k = 0xffffffff | j;
        System.out.println(k);
        j = 6;
        k = 0xffffffff ^ j;
        System.out.println(k);
    }

    void longExpressions(int i, int j) {
        i = j & 0 & 100;
    }

    void m(int i) {
      int h  = 0 << 8;
      int b = i ^  i; // 0
      int c = i &  i; // i
      int d = i |  i; // i
    }
}
