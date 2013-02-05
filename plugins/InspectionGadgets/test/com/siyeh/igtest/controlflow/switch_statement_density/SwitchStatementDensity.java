package com.siyeh.igtest.controlflow.switch_statement_density;

public class SwitchStatementDensity
{
    private int m_bar;

    public SwitchStatementDensity()
    {
        m_bar = 0;
    }

    public void fooBar()
    {
        final int bar = m_bar;
        switch(bar)
        {
            case 3:
            case 4:
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                System.out.println("3");
                break;
            case 6:
                System.out.println("4");
                break;
            default:
                break;
        }
        switch(bar) {}

    }
}
