package com.siyeh.igtest.style;

public class CStyleArrayDeclaration
{
    private int[] m_foo;
    private int m_bar<warning descr="Field 'm_bar' has C-style array type declaration"><caret>[]</warning>;

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
        final int foo<warning descr="Local variable 'foo' has C-style array type declaration">[]</warning> = new int[3];
        final int[] bar = new int[3];

        for(int i = 0; i < bar.length; i++)
        {
            foo[i] = bar[i];
        }
    }

    public void bar(int foo<warning descr="Parameter 'foo' has C-style array type declaration">[]</warning>, int[] bar)
    {

    }

    String ohGod(String[] a)<warning descr="Method 'ohGod()' has C-style array return type declaration">[]</warning> {
        return a;
    }

    record Record(int x<error descr="C-style record component declaration is not allowed">[]</error>) {
    }

    int methodWithoutBody()<warning descr="Method 'methodWithoutBody()' has C-style array return type declaration">[][]</warning><EOLError descr="'{' or ';' expected"></EOLError>
}
