package com.siyeh.igtest.numeric;

public class BadOddnessInspection {
    public void foo() {
        int i = 0;
        if (i % 2 == 1) {

        }
    }
}
