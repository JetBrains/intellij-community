package com.siyeh.igtest.bitwise.pointless_bitwise_expression;

import java.util.*;

class PointlessBitwiseExpressionNoConstant {
    private static final int ZERO = 0;

    void test(int y) {
        int x = ZERO & y;
        int x1 = <warning descr="'0 & y' can be replaced with '0'">0 & y</warning>;
        int z = <warning descr="'-1 ^ y' can be replaced with '~y'">-1 ^ y</warning>;
    }

    static long get(long prefix) {
        long bits = <warning descr="'0xffffffff ^ (1 << 32 - prefix) - 1' can be replaced with '-(1 << 32 - prefix)'">0xffffffff ^ (1 << 32 - prefix) - 1</warning>;
        return bits;
    }

    static long get2(long prefix) {
        long bits = <warning descr="'0xffffffff ^ (1 << 32 - prefix) - 2' can be replaced with '~((1 << 32 - prefix) - 2)'">0xffffffff ^ (1 << 32 - prefix) - 2</warning>;
        return bits;
    }

    static void tilde(int x, int y) {
        int z = <warning descr="'~(x + y - 1)' can be replaced with '-(x + y)'">~(x + y - 1)</warning>;

    }
}
