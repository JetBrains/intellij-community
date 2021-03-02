package com.siyeh.igtest.bitwise.pointless_bitwise_expression;

import java.util.*;

public class PointlessBitwiseExpression {
    private static final int ZERO = 0;

    public static void main(String[] args) {
        final int i = 1;
        int j = 0;
        System.out.println(j);
        j = 3;
        int k = j;
        System.out.println(k);
        k = j;
        System.out.println(k);
        k = j;
        System.out.println(k);
        k = j;
        System.out.println(k);
        k = j;
        System.out.println(k);
        k = j;
        System.out.println(k);
    }

    public static void main3(String[] args) {
        final int i = 1;
        int j = 0;
        System.out.println(j);
        j = 3;
        int k = j;
        System.out.println(k);
        k = j;
        System.out.println(k);

    }

    public static void main2(String[] args) {
        final int i = 1;
        int j = i;
        System.out.println(j);
        j = 3;
        int k = 0xffffffff;
        System.out.println(k);
        j = 6;
        k = ~j;
        System.out.println(k);


    }

    public static void main4(String[] args) {
        final int i = 1;
        int j = i;
        System.out.println(j);
        j = 3;
        int k = 0xffffffff;
        System.out.println(k);
        j = 6;
        k = ~j;
        System.out.println(k);
    }

    public static void main5(String[] args) {
        Random in = new Random();
        System.out.println(in.nextInt() & in.nextInt());
    }

    void longExpressions(int i, int j) {
        i = 0;
    }

    void m(int i) {
      int h  = 0;
      int b = 0; // 0
      int c = i; // i
      int d = i; // i
    }

    void testChar(int value) {
        int res = value & '\uFFFF';
    }

    void testTilde(int x, long y) {
        int r1 = 0;
        int r2 = 0;
        int r3 = 1 & 0;
        int r4 = 0 & 1;
        long r5 = 0L;
        long r6 = 0L;
        long r7 = ~y;
        int r8 = -1;
        long r9 = -1L;
    }

    void testDoubleTilde(int x, long y) {
        int r1 = x;
        long r2 = ~y;
        int r3 = (x);
        long r4 = ~(~(y));
    }

    void testComments(int length) {
        // comment
        final int bits = length// 1
                | 3;
    }
}
