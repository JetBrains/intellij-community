package com.siyeh.igtest.numeric;

public class IntegerMultiplicationCastToLong {
    public void foo() {
        int x = 65336;
        final long val = 65336 * x;
    }
}
