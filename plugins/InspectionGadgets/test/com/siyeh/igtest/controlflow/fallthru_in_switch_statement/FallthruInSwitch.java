package com.siyeh.igtest.controlflow.fallthru_in_switch_statement;

public class FallthruInSwitch
{
    private int m_bar;

    public FallthruInSwitch()
    {
        m_bar = 0;
    }

    public void foo()
    {
        final int bar = m_bar;
        switch(bar)
        {
            case 2:
                // fall-through
            case (3):
              System.out.println();
              // Falls through
            case (4):
                System.out.println("3");
            <warning descr="Fallthrough in 'switch' statement">case (5):</warning>
            case (6): // don't warn here
                System.out.println("4");
        }
    }
}
