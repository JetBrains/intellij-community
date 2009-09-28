package com.siyeh.igtest.encapsulation;

import java.util.*;

public class ReturnOfDateFieldInspection
{
    private Date m_foo;
 //   private List<String> m_fooBar;
    private Calendar m_bar;

    public static void main(String[] args)
    {
        new ReturnOfDateFieldInspection(new Date());
    }

    public ReturnOfDateFieldInspection(Date foo)
    {
        m_foo = new Date(foo.getTime());
    }

    public Date foo()
    {
        return m_foo;
    }
/*

    public List<String> fooBar()
    {
        return m_fooBar;
    }
*/

    public Calendar bar()
    {
        return m_bar;
    }


}