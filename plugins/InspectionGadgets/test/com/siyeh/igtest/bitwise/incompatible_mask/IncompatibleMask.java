package com.siyeh.igtest.bitwise.incompatible_mask;

public class IncompatibleMask {
    public static void main(String[] args) {
        final int i = foo();
        if((i & 0x1) == 0x2)
        {

        }if((i & 0x3) == 0x2) // this should be fine
        {

        }
        if((i & 0x1) != 0x2)
        {

        }

        if((i | 0x1) == 0x2)
        {

        }
        if((i | 0x1) == 0x3)   // this should be fine
        {

        }
        final boolean b = (i | 0x1) != 0x2;
        if(b)
        {

        }
    }

    private static int foo() {
        return 6;
    }

}
