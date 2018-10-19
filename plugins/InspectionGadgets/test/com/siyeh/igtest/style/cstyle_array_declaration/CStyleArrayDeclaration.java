package com.siyeh.igtest.style;

public class CStyleArrayDeclaration
{
    private int[] m_foo;
    private int <warning descr="C-style array declaration of field 'm_bar'">m_bar</warning>[];

    public CStyleArrayDeclaration(int[] bar, int[] foo)
    {
        m_bar = bar;
        m_foo = foo;
        for(int i = 0; i < bar.length; i++)
        {
            m_foo[i] = m_bar[i];
        }

    }

    public void foo()
    {
        final int <warning descr="C-style array declaration of local variable 'foo'">foo</warning>[] = new int[3];
        final int[] bar = new int[3];

        for(int i = 0; i < bar.length; i++)
        {
            foo[i] = bar[i];
        }
    }

    public void bar(int <warning descr="C-style array declaration of parameter 'foo'">foo</warning>[], int[] bar)
    {

    }

    String <warning descr="C-style array declaration of the return type of method 'ohGod()'">ohGod</warning>(String[] a)[] {
        return a;
    }
}
