package com.siyeh.igtest.style.constant_on_rhs;

public class ConstantOnRHS
{
    private int m_bar = 4;
    private boolean m_foo = (<warning descr="'m_bar == 3': constant on right side of comparison">m_bar == 3</warning>);

    public void foo()
    {
        if(<warning descr="'m_bar == 3': constant on right side of comparison">m_bar == 3</warning>)
        {

        }
        if (m_bar ==<error descr="Expression expected"> </error>) {}
    }
}
