package com.siyeh.igtest.confusing;

public class CastThatLosesPrecisionInspection
{
    public CastThatLosesPrecisionInspection()
    {
    }

    public void fooBar()
    {
        byte b;
        int i;
        char ch;
        long l = 0L;
        double d = 0.0;
        float f = 0.0f;

        i = (int) f;
        System.out.println("i = " + i);
        ch = (char) d;
        System.out.println("ch = " + ch);
        i = (int) d;
        System.out.println("i = " + i);
        i = (int) l;
        System.out.println("i = " + i);
        b = (byte) l;
        System.out.println("b = " + b);

        l = (long) d;
        System.out.println("l = " + l);
        l = (long) f;
        System.out.println("l = " + l);

        d = (double) f;
        System.out.println("d = " + d);

        f = (float) d;
        System.out.println("f = " + f);
    }

}
