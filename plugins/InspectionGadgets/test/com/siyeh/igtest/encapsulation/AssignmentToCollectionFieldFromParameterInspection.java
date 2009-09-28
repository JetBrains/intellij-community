package com.siyeh.igtest.encapsulation;

import java.util.Set;
import java.util.HashSet;
import java.util.List;

public class AssignmentToCollectionFieldFromParameterInspection
{
    private Set m_foo;
    //private List<String> m_fooBar;
    private int[] m_bar;

    public static void main(String[] args)
    {
        new AssignmentToCollectionFieldFromParameterInspection(new HashSet(4));
    }
/*

    public AssignmentToCollectionFieldFromParameterInspection(List<String> fooBar)
    {
        m_fooBar = fooBar;
    }
*/

    public AssignmentToCollectionFieldFromParameterInspection(Set foo)
    {
        m_foo = foo;
    }

    public AssignmentToCollectionFieldFromParameterInspection(int[] bar)
    {
        m_bar = bar;
    }
}