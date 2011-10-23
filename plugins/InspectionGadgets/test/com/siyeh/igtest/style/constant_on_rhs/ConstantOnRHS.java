package com.siyeh.igtest.style.constant_on_rhs;

public class ConstantOnRHS
{
    private int m_bar = 4;
    private boolean m_foo = (m_bar == 3);

    public void foo()
    {
        if(m_bar == 3)
        {

        }
        if (m_bar == ) {}
    }
}
