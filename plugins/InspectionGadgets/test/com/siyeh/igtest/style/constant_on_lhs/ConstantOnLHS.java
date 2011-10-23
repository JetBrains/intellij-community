package com.siyeh.igtest.style.constant_on_lhs;

public class ConstantOnLHS
{
    private  int m_bar = 4;
    private boolean m_foo = (3 == m_bar);

    public void foo()
    {
        if(3 == m_bar)
        {

        }
        if (4 == ) {}
    }
}
