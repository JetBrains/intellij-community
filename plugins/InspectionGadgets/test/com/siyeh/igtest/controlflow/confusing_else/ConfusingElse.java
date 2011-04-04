package com.siyeh.igtest.controlflow.confusing_else;

public class ConfusingElse {

    public static void main(String[] args) {
        if (foo()) {
            return;
        } else {
            System.out.println("ConfusingElseInspection.main");
        }
        bar();
    }

    private static void bar() {
    }

    private static boolean foo() {
        return true;
    }

    void two(boolean b) {
        if (foo()) {

        } else if (b) {
            return;
        } else {

        }
        bar();
    }

    void three(boolean b) {
        switch (3) {
            case 2:
                if (foo()) {
                    return;
                } else {
                    return;
                }
            case 3:
        }
    }

    public int foo(int o) {
        if (o == 1) {
            o = 2;
        } else if (o == 2) {
            return 1;
        } else {
            o = 4;
        }
        return o;
    }
}
