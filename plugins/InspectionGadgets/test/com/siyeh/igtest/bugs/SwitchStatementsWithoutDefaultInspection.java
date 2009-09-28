package com.siyeh.igtest.bugs;

public class SwitchStatementsWithoutDefaultInspection
{
    private int m_bar;

    public SwitchStatementsWithoutDefaultInspection()
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
                break;
            case 7:
            case5:
                    System.out.println("bar");
                break;
            case 6:
                System.out.println("4");
                break;
            default:
                break;
        }

        switch(bar)
        {
            case 3:
            case 4:
                System.out.println("3");
     
                break;
            case 7:
                case5:
                    System.out.println("bar");
                break;
            case 6:
                System.out.println("4");
                break;
        }
        MyEnum var = MyEnum.foo;
        switch(var)
        {
            case foo:
            case bar:
            case baz:
                break;
        }
        switch(var)
        {
            case bar:
            case baz:
                break;
        }
    }
}
