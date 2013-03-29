package com.siyeh.igtest.numeric.implicit_numeric_conversion;

public class ImplicitNumericConversion
{
    public ImplicitNumericConversion()
    {
    }

    public void fooBar()
    {
        final int i = 0;
        final char ch = (char) 0;
        final long l = 0L;
        final double d = 0.0;
        float f = (float)1.0;

        f = f+1;
        f = i;

        useInt(i);
        useInt('c');
        useInt(ch);

        useDouble(0);
        useDouble(0.0F);
        useDouble(-0.0F);
        useDouble(i);
        useDouble(ch);
        useDouble(l);
        useDouble(d);
        useDouble(f);
        useDouble(1.0);
        useDouble(1.0F);

        useFloat(0);
        useFloat(0L);
        useFloat(0.0F);
        useFloat(i);
        useFloat(ch);
        useFloat(l);
        useFloat(f);
        useFloat(1.0F);

        useLong(i);
        useLong(ch);
        useLong(l);
        useLong(3L);
        useLong(3L);

        int j = 0;
        j|=l;
        System.out.println(j);
    }

    private int useLong(long l)
    {
        System.out.println(l);
        return (int) l;
    }

    private int useInt(int i)
    {
        System.out.println(i);
        return i;
    }

    private void useDouble(double d)
    {
        System.out.println(d);
    }

    private void useFloat(float d)
    {
        System.out.println(d);
    }

    private void unaryPromotion() {
        byte b = 2;
        int a[] = new int[b];
        a[b] = (byte)1;
    }

    private void polyadic() {
        long l = 1 + 2 + 3;
    }
}
