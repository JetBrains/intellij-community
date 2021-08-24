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

        <warning descr="'switch' statement without 'default' branch">switch</warning>(bar)
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
        <warning descr="'switch' statement without 'default' branch">switch</warning>(var)
        {
            case foo:
            case bar:
            case baz:
                break;
        }
        <warning descr="'switch' statement without 'default' branch">switch</warning>(var)
        {
            case bar:
            case baz:
                break;
        }
        switch (var)<EOLError descr="'{' expected"></EOLError>
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
        <warning descr="'switch' statement without 'default' branch">switch</warning> (t) {
            case A:
                break;
            case B:
                break;
        }
    }
    
    public void testRules(T t, MyEnum my) {
        <warning descr="'switch' statement without 'default' branch">switch</warning> (t) {
            case A -> {}
            case B -> {}
        }
        <warning descr="'switch' statement without 'default' branch">switch</warning> (t) {
            case A, B -> {}
        }
        <warning descr="'switch' statement without 'default' branch">switch</warning> (my) {
            case foo, bar -> {}
        }
        <warning descr="'switch' statement without 'default' branch">switch</warning> (my) {
            case foo, bar -> {}
            case baz -> {}
        }
    }

    void empty(T t) {
        switch (t) {

        }
    }
}
