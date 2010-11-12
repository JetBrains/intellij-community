package com.siyeh.igtest.controlflow.infinite_loop_statement;

public class InfiniteLoopStatement {

    void bla() {
        int x = 0;
        c:
        b:
        while (true) {
            if (x == 0) {
                a:
                while (true) { // A warning issued here
                    x++;
                    continue b;
                }
            }
            System.out.println("Loop");
        }
    }

    void notInfinite1(String s) {
        while (true) {
            if (s.equals("exit")) {
                System.exit(1);
            }
        }
    }
}
