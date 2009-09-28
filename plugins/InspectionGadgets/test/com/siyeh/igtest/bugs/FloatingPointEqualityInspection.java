package com.siyeh.igtest.bugs;

public class FloatingPointEqualityInspection
{
    private double m_bar;
    private double m_baz;
    private float m_barf;
    private float m_bazf;

    public static final float TENTH = 0.1f;
    public static final float fifth = 0.2f;

    public FloatingPointEqualityInspection()
    {
        m_bar = 0.0;
        m_baz = 1.0;
        m_barf = TENTH;
        m_bazf = fifth;
    }

    public void foo()
    {
        if (m_bar == m_baz) {
            System.out.println("m_bar = " + m_bar);
        }
        if (m_barf == m_bazf) {
            System.out.println("m_barf = " + m_barf);
        }
        if (m_barf != m_bar) {
            System.out.println("m_barf = " + m_barf);
        }
       
    }


}
