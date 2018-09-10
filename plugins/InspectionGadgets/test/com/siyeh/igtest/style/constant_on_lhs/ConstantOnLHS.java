package com.siyeh.igtest.style.constant_on_lhs;

public class ConstantOnLHS
{
    private  int m_bar = 4;
    private boolean m_foo = (<warning descr="'3 == m_bar': constant on left side of comparison">3 == m_bar</warning>);

    public void foo()
    {
        if(<warning descr="'3 == m_bar': constant on left side of comparison">3 == m_bar</warning>)
        {

        }
        if (4 ==<error descr="Expression expected"> </error>) {}
    }
}
