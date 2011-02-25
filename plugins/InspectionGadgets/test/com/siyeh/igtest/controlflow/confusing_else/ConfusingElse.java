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
}
