package com.siyeh.igtest.bugs;

public class SwitchStatementDensityInspection
{
    private int m_bar;

    public SwitchStatementDensityInspection()
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
                break;
            case 6:
                System.out.println("4");
                break;
            default:
                break;
        }


    }
}
