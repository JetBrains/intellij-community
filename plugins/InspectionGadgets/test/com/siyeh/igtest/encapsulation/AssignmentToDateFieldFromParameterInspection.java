package com.siyeh.igtest.encapsulation;

import java.util.Calendar;
import java.util.Date;

public class AssignmentToDateFieldFromParameterInspection
{
    private Date m_foo;
    private Calendar m_bar;

    public static void main(String[] args)
    {
        new AssignmentToDateFieldFromParameterInspection(new Date());
    }
/*

    public AssignmentToCollectionFieldFromParameterInspection(List<String> fooBar)
    {
        m_fooBar = fooBar;
    }
*/

    public AssignmentToDateFieldFromParameterInspection(Date foo)
    {
        m_foo = foo;
        Date bar;
        bar = foo;
    }

    public AssignmentToDateFieldFromParameterInspection(Calendar bar)
    {
        m_bar = bar;
    }
}