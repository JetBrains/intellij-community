package com.siyeh.igtest.encapsulation.return_of_date_field;

import java.util.*;

public class ReturnOfDateField
{
    private Date m_foo;
 //   private List<String> m_fooBar;
    private Calendar m_bar;

    public static void main(String[] args)
    {
        new ReturnOfDateField(new Date());
    }

    public ReturnOfDateField(Date foo)
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

    private Date hidden() {
        return m_foo;
    }

    interface A {
        Date d();
    }
    
    private void p(){
        A a = () -> {
            return m_foo;
        };
    }
}