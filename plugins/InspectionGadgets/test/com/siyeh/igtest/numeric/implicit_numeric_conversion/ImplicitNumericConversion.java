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

        f = f+<warning descr="Implicit numeric conversion of '1' from 'int' to 'float'">1</warning>;
        f = <warning descr="Implicit numeric conversion of 'i' from 'int' to 'float'">i</warning>;

        useInt(i);
        useInt(<warning descr="Implicit numeric conversion of ''c'' from 'char' to 'int'">'c'</warning>);
        useInt(<warning descr="Implicit numeric conversion of 'ch' from 'char' to 'int'">ch</warning>);

        useDouble(<warning descr="Implicit numeric conversion of '0' from 'int' to 'double'">0</warning>);
        useDouble(<warning descr="Implicit numeric conversion of '0.0F' from 'float' to 'double'">0.0F</warning>);
        useDouble(<warning descr="Implicit numeric conversion of '-0.0F' from 'float' to 'double'">-0.0F</warning>);
        useDouble(<warning descr="Implicit numeric conversion of 'i' from 'int' to 'double'">i</warning>);
        useDouble(<warning descr="Implicit numeric conversion of 'ch' from 'char' to 'double'">ch</warning>);
        useDouble(<warning descr="Implicit numeric conversion of 'l' from 'long' to 'double'">l</warning>);
        useDouble(d);
        useDouble(<warning descr="Implicit numeric conversion of 'f' from 'float' to 'double'">f</warning>);
        useDouble(1.0);
        useDouble(<warning descr="Implicit numeric conversion of '1.0F' from 'float' to 'double'">1.0F</warning>);

        useFloat(<warning descr="Implicit numeric conversion of '0' from 'int' to 'float'">0</warning>);
        useFloat(<warning descr="Implicit numeric conversion of '0L' from 'long' to 'float'">0L</warning>);
        useFloat(0.0F);
        useFloat(<warning descr="Implicit numeric conversion of 'i' from 'int' to 'float'">i</warning>);
        useFloat(<warning descr="Implicit numeric conversion of 'ch' from 'char' to 'float'">ch</warning>);
        useFloat(<warning descr="Implicit numeric conversion of 'l' from 'long' to 'float'">l</warning>);
        useFloat(f);
        useFloat(1.0F);

        useLong(<warning descr="Implicit numeric conversion of 'i' from 'int' to 'long'">i</warning>);
        useLong(<warning descr="Implicit numeric conversion of 'ch' from 'char' to 'long'">ch</warning>);
        useLong(l);
        useLong(3L);
        useLong(3L);

        int j = 0;
        j|=<warning descr="Implicit numeric conversion of 'l' from 'long' to 'int'">l</warning>;
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
        byte b = <warning descr="Implicit numeric conversion of '2' from 'int' to 'byte'">2</warning>;
        int a[] = new int[<warning descr="Implicit numeric conversion of 'b' from 'byte' to 'int'">b</warning>];
        a[<warning descr="Implicit numeric conversion of 'b' from 'byte' to 'int'">b</warning>] = <warning descr="Implicit numeric conversion of '(byte)1' from 'byte' to 'int'">(byte)1</warning>;
    }

    private void polyadic() {
        long l = <warning descr="Implicit numeric conversion of '1 + 2 + 3' from 'int' to 'long'">1 + 2 + 3</warning>;
    }

    void equality(int i, long l, byte b, double d, float f) {
        if (<warning descr="Implicit numeric conversion of 'i' from 'int' to 'long'">i</warning> == l) {
        } else if (i == <warning descr="Implicit numeric conversion of 'b' from 'byte' to 'int'">b</warning>) {}
        if (<warning descr="Implicit numeric conversion of 'i' from 'int' to 'double'">i</warning> == d) {}
        if (<warning descr="Implicit numeric conversion of 'l' from 'long' to 'float'">l</warning> == f) {}
        if (true == true == (Boolean)new Object()) {}
        if (null == null) {}
    }
}
