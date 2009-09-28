package com.siyeh.igtest.controlflow.if_may_be_conditional;

public class IfMayBeConditional {

    void foo(int a, int b) {
        int c = 0;
        if (a < b) { c += a - b; } else { c -= b; }
    }

    void foo2(int a, int b) {
        int c = 0;
        if (a < b) { c += a - b; } else { c += b; }
    }
}
