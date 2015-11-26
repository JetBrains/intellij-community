package com.siyeh.igtest.bugs.subtraction_in_compare_to;

import java.util.Map;
import java.util.HashMap;

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
        java.util.Comparator<Integer> c = (s1, s2) -> <warning descr="Subtraction 's1 - s2' in 'compareTo()' may result in overflow errors">s1 - s2</warning>;
    }
}
class A implements Comparable<A> {
    final String s = "";
    public int compareTo(A a) {
        return s.length() - a.s.length();
    }
}
class B implements Comparable<B> {
    final Map<String, String> map = new HashMap();
    public int compareTo(B b) {
        return map.size() - b.map.size();
    }
}
class C implements Comparable<C> {
    private short small = 1;
    public int compareTo(C c) {
        return small - c.small;
    }
}