package com.siyeh.igtest.controlflow.confusing_else;

public class ConfusingElse {

    public static void main(String[] args) {
        if (foo()) {
            return;
        } <warning descr="'else' branch may be unwrapped, as the 'if' branch never completes normally">else</warning> {
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
                } <warning descr="'else' branch may be unwrapped, as the 'if' branch never completes normally">else</warning> {
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

    void lastElse(int i) {
        if (i == 1) {
            return;
        } <warning descr="'else' branch may be unwrapped, as the 'if' branch never completes normally">else</warning> {
            System.out.println("i = " + i);
        }
    }

    void elseIf(int i) {
        if (i == 1) {
            return;
        } <warning descr="'else' branch may be unwrapped, as the 'if' branch never completes normally">else</warning> if (i == 3) {
            System.out.println("i = " + i);
        }
        System.out.println();
    }

    void elseIfChain(int i) {
        while (true) {
            if (i == 0) {
                System.exit(i);
            }
            <warning descr="'else' branch may be unwrapped, as the 'if' branch never completes normally">else</warning> if (i == 1) {
                throw new RuntimeException();
            }
            <warning descr="'else' branch may be unwrapped, as the 'if' branch never completes normally">else</warning> if (i == 2) {
                return;
            }
            <warning descr="'else' branch may be unwrapped, as the 'if' branch never completes normally">else</warning> if (i == 3) {
                break;
            }
            <warning descr="'else' branch may be unwrapped, as the 'if' branch never completes normally">else</warning> if (i == 4) {
                continue;
            } <warning descr="'else' branch may be unwrapped, as the 'if' branch never completes normally">else</warning> {
              System.out.println(i);
            }
        }
    }
}
