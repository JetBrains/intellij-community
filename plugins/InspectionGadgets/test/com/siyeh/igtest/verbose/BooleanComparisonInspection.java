package com.siyeh.igtest.verbose;

public class BooleanComparisonInspection
{
    private boolean m_foo;
    private boolean m_bar;

    public BooleanComparisonInspection(boolean foo)
    {
        this.m_foo = foo;
    }

    public void foo()
    {
        if(m_foo == true)
        {

        }
        if(m_foo == false)
        {

        }
        if(true == m_foo)
        {

        }
        if(false == m_foo)
        {

        }
        if(m_foo != true)
        {

        }
        if(m_foo != false)
        {

        }
        if(true != m_foo)
        {

        }
        if(false != m_foo)
        {

        }
        if(m_bar == m_foo)
        {

        }
    }
}