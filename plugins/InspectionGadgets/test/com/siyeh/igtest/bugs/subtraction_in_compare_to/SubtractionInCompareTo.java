package com.siyeh.igtest.bugs.subtraction_in_compare_to;

public class SubtractionInCompareTo implements java.util.Comparator<SubtractionInCompareTo>
{
    private int m_foo = 3;

    public int compareTo(Object foo)
    {
        final int temp = m_foo + ((SubtractionInCompareTo)foo).m_foo;
        return <warning descr="Subtraction 'm_foo - ((SubtractionInCompareTo)foo).m_foo' in 'compareTo()' may result in overflow errors">m_foo - ((SubtractionInCompareTo)foo).m_foo</warning>;
    }


    @Override
    public int compare(SubtractionInCompareTo o1, SubtractionInCompareTo o2) {
        return <warning descr="Subtraction 'o1.m_foo - o2.m_foo' in 'compareTo()' may result in overflow errors">o1.m_foo - o2.m_foo</warning>;
    }

    {
        java.util.Comparator<String> c = (s1, s2) -> <warning descr="Subtraction 's1.length() - s2.length()' in 'compareTo()' may result in overflow errors">s1.length() - s2.length()</warning>;
    }
}
