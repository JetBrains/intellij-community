package com.siyeh.igtest.bugs;

public class SubtractionInComparetoInspection
{
    private int m_foo = 3;

    public int compareTo(Object foo)
    {
        final int temp = m_foo + ((SubtractionInComparetoInspection)foo).m_foo;
        return m_foo - ((SubtractionInComparetoInspection)foo).m_foo;
    }
}
