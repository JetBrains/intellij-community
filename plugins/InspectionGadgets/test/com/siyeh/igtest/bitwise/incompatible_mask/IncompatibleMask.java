package com.siyeh.igtest.bitwise.incompatible_mask;

public class IncompatibleMask {
    public static void main(String[] args) {
        final int i = foo();
        if(<warning descr="'(i & 0x1) == 0x2' is always false">(i & 0x1) == 0x2</warning>)
        {

        }if((i & 0x3) == 0x2) // this should be fine
        {

        }
        if(<warning descr="'(i & 0x1) != 0x2' is always true">(i & 0x1) != 0x2</warning>)
        {

        }

        if(<warning descr="'(i | 0x1) == 0x2' is always false">(i | 0x1) == 0x2</warning>)
        {

        }
        if((i | 0x1) == 0x3)   // this should be fine
        {

        }
        final boolean b = <warning descr="'(i | 0x1) != 0x2' is always true">(i | 0x1) != 0x2</warning>;
        if(b)
        {

        }
    }

    private static int foo() {
        return 6;
    }

}
