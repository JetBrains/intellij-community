package com.siyeh.igtest.style;

public class CStyleArrayDeclarationInspection
{
    private int[] m_foo;
    private int m_bar[];

    public CStyleArrayDeclarationInspection(int[] bar, int[] foo)
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
        final int foo[] = new int[3];
        final int[] bar = new int[3];

        for(int i = 0; i < bar.length; i++)
        {
            foo[i] = bar[i];
        }
    }

    public void bar(int foo[], int[] bar)
    {

    }
}
