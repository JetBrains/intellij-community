package com.siyeh.igtest.portability;

public class AutoUnboxingInspection {
    public void foo() {
        Integer bar = 3;
        int baz = new Integer(3);
        if (new Integer(3) == 3) {
            return;
        }
        if (new Integer(3) + 3 == 3) {
            return;
        }
        Integer x = 3;
    }
}
