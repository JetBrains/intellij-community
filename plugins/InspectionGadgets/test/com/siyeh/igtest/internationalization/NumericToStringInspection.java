package com.siyeh.igtest.internationalization;

public class NumericToStringInspection
{
    public NumericToStringInspection()
    {
    }

    public void foo()
    {
        final Integer j = new Integer(0);
        j.toString();
        final Short k = new Short((short) 0);
        k.toString();
        final Long m = new Long(0);
        m.toString();
        final Double d = new Double(0);
        d.toString();
        final Float f = new Float(0);
        f.toString();
        final Object g = new Float(0);
        g.toString();
    }
}