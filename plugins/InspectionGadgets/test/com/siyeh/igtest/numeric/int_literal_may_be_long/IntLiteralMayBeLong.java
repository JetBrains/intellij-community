package com.siyeh.igtest.numeric.int_literal_may_be_long;

public class IntLiteralMayBeLong {

    void foo() {
        System.out.println((long) 1);
        System.out.println((long) -/*yes, minus*/1);
        System.out.println((long)-(-(6)));
    }
}
