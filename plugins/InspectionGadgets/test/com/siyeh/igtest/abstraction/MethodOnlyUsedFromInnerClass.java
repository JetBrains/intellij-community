package com.siyeh.igtest.abstraction;

public class MethodOnlyUsedFromInnerClass {

    private class Inner {
        void foo() {
            add();
            add();
        }
    }


    private void add() {}
}