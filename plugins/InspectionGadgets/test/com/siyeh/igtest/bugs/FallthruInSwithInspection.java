package com.siyeh.igtest.bugs;

public class FallthruInSwithInspection
{
    private int m_bar;

    public FallthruInSwithInspection()
    {
        m_bar = 0;
    }

    public void foo()
    {
        final int bar = m_bar;
        switch(bar)
        {
            case (3):
            case (4):
                System.out.println("3");
            case (5):
            case (6):
                System.out.println("4");
        }
    }
}
