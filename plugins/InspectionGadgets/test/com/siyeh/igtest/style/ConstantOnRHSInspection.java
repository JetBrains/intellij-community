package com.siyeh.igtest.style;

public class ConstantOnRHSInspection
{
    private int m_bar = 4;
    private boolean m_foo = (m_bar == 3);

    public void foo()
    {
        if(m_bar == 3)
        {

        }
    }
}
