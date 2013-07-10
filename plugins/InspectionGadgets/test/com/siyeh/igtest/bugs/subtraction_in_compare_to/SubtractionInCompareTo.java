package com.siyeh.igtest.bugs.subtraction_in_compare_to;

public class SubtractionInCompareTo implements java.util.Comparator<SubtractionInCompareTo>
{
    private int m_foo = 3;

    public int compareTo(Object foo)
    {
        final int temp = m_foo + ((SubtractionInCompareTo)foo).m_foo;
        return m_foo - ((SubtractionInCompareTo)foo).m_foo;
    }


    @Override
    public int compare(SubtractionInCompareTo o1, SubtractionInCompareTo o2) {
        return o1.m_foo - o2.m_foo;
    }
}
