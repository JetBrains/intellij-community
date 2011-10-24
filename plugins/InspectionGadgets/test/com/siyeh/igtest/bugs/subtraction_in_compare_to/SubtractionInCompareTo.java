package com.siyeh.igtest.bugs.subtraction_in_compare_to;

public class SubtractionInCompareTo
{
    private int m_foo = 3;

    public int compareTo(Object foo)
    {
        final int temp = m_foo + ((SubtractionInCompareTo)foo).m_foo;
        return m_foo - ((SubtractionInCompareTo)foo).m_foo;
    }
}
