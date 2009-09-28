package com.siyeh.igtest.j2me;

public class MethodCallInLoopConditionInspection {
    public void foo() {
        for (int i = 0; i < bar(); i++) {

        }
        while (bar() != 4) {
            foo();
        }

        do {
            foo();
        }
        while (bar() != 4);

    }

    private int bar() {
        return 3;
    }
}
