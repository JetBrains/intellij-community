package com.siyeh.igtest.encapsulation;

import java.util.*;

public class ReturnOfCollectionFieldInspection
{
    private Set m_foo;
    private List<String> m_fooBar;
    private int[] m_bar;

    public static void main(String[] args)
    {
        new ReturnOfCollectionFieldInspection(new HashSet(4));
    }

    public ReturnOfCollectionFieldInspection(Set foo)
    {
        m_foo = Collections.unmodifiableSet(foo);
    }

    public Set foo()
    {
        return m_foo;
    }

    public List<String> fooBar()
    {
        return m_fooBar;
    }

    public List<String> fooBarEmpty()
    {
        return Collections.emptyList();
    }

    public int[] bar()
    {
        return m_bar;
    }


}
