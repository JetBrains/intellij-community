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

    void foo3(int i, StringBuilder sb) {
        if (i == 0) {
            sb.append("type.getConstructor()", 0, 1);
        }
        else {
            sb.append("DescriptorUtils.getFQName(cd)",0, 1);
        }
    }
}
