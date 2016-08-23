package com.siyeh.igtest.bitwise.pointless_bitwise_expression;

import java.util.*;

public class PointlessBitwiseExpression {
    private static final int ZERO = 0;

    public static void main(String[] args) {
        final int i = 1;
        int j = <warning descr="'i & 0' can be replaced with '0'">i & 0</warning>;
        System.out.println(j);
        j = 3;
        int k = <warning descr="'j | 0' can be replaced with 'j'">j | 0</warning>;
        System.out.println(k);
        k = <warning descr="'j ^ 0' can be replaced with 'j'">j ^ 0</warning>;
        System.out.println(k);
        k = <warning descr="'j << 0' can be replaced with 'j'">j << 0</warning>;
        System.out.println(k);
        k = <warning descr="'j >> 0' can be replaced with 'j'">j >> 0</warning>;
        System.out.println(k);
        k = <warning descr="'j >>> 0' can be replaced with 'j'">j >>> 0</warning>;
        System.out.println(k);
        k = <warning descr="'j >>> ZERO' can be replaced with 'j'">j >>> ZERO</warning>;
        System.out.println(k);
    }

    public static void main3(String[] args) {
        final int i = 1;
        int j = <warning descr="'0 & i' can be replaced with '0'">0 & i</warning>;
        System.out.println(j);
        j = 3;
        int k = <warning descr="'0 | j' can be replaced with 'j'">0 | j</warning>;
        System.out.println(k);
        k = <warning descr="'0 ^ j' can be replaced with 'j'">0 ^ j</warning>;
        System.out.println(k);

    }

    public static void main2(String[] args) {
        final int i = 1;
        int j = <warning descr="'i & 0xffffffff' can be replaced with 'i'">i & 0xffffffff</warning>;
        System.out.println(j);
        j = 3;
        int k = <warning descr="'j | 0xffffffff' can be replaced with '0xffffffff'">j | 0xffffffff</warning>;
        System.out.println(k);
        j = 6;
        k = <warning descr="'j ^ 0xffffffff' can be replaced with '~j'">j ^ 0xffffffff</warning>;
        System.out.println(k);


    }

    public static void main4(String[] args) {
        final int i = 1;
        int j = <warning descr="'0xffffffff & i' can be replaced with 'i'">0xffffffff & i</warning>;
        System.out.println(j);
        j = 3;
        int k = <warning descr="'0xffffffff | j' can be replaced with '0xffffffff'">0xffffffff | j</warning>;
        System.out.println(k);
        j = 6;
        k = <warning descr="'0xffffffff ^ j' can be replaced with '~j'">0xffffffff ^ j</warning>;
        System.out.println(k);
    }

    public static void main5(String[] args) {
        Random in = new Random();
        System.out.println(in.nextInt() & in.nextInt());
    }

    void longExpressions(int i, int j) {
        i = <warning descr="'j & 0 & 100' can be replaced with '0'">j & 0 & 100</warning>;
    }

    void m(int i) {
      int h  = <warning descr="'0 << 8' can be replaced with '0'">0 << 8</warning>;
      int b = <warning descr="'i ^ i' can be replaced with '0'">i ^  i</warning>; // 0
      int c = <warning descr="'i & i' can be replaced with 'i'">i &  i</warning>; // i
      int d = <warning descr="'i | i' can be replaced with 'i'">i |  i</warning>; // i
    }
}
