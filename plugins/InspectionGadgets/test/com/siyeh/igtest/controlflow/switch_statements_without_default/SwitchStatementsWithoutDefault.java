package com.siyeh.igtest.controlflow.switch_statements_without_default;

public class SwitchStatementsWithoutDefault
{
    private int m_bar;

    public SwitchStatementsWithoutDefault()
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
        switch (var)
    }

    enum MyEnum {
        foo, bar, baz;
    }

    enum T {
        A,
        B;

        public static T C = A;
    }

    public void test() {
        T t = T.C;
        switch (t) {
            case A:
                break;
            case B:
                break;
        }
    }
}
