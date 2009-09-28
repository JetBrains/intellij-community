package com.siyeh.igtest.style;

public class ConstantOnLHSInspection
{
    private  int m_bar = 4;
    private boolean m_foo = (3 == m_bar);

    public void foo()
    {
        if(3 == m_bar)
        {

        }
    }
}
