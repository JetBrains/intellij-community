package com.siyeh.igtest.numeric.bad_oddness;

public class BadOddness {
    public void foo(int i) {
        if (<warning descr="Test for oddness 'i % 2 == 1' will fail on negative values">i % 2 == 1</warning>) {
            System.out.println("odd");
        }
    }

    // IDEA-175559 Suspicious test for oddness reported when not needed
    public void check(final int sideLength) {
        if (sideLength < 4 || sideLength % 2 == 1) {
            throw new IllegalArgumentException("Illegal side length");
        }
    }

    public void check2(final int sideLength) {
        if (<warning descr="Test for oddness 'sideLength % 2 == 1' will fail on negative values">sideLength % 2 == 1</warning>) {
            throw new IllegalArgumentException("Illegal side length");
        }
    }
}
