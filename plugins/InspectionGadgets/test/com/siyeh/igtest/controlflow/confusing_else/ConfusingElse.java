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
          System.out.println(0);
        } else if (b) {
            return;
        } else {
          System.out.println(1);
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

    void elseIf(int i) {
        if (i == 1) {
            return;
        } else if (i == 3) {
            System.out.println("i = " + i);
        }
        System.out.println();
    }
}
