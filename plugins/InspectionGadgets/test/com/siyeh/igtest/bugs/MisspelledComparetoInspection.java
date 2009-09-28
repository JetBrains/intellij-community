package com.siyeh.igtest.bugs;

public class MisspelledComparetoInspection
{
    private int m_bar;

    public MisspelledComparetoInspection()
    {
        m_bar = 0;
    }

    public int compareto(int foo)
    {
        return m_bar;
    }
}
