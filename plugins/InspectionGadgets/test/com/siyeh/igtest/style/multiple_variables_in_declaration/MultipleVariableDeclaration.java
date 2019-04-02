package com.siyeh.igtest.style;

public class MultipleVariableDeclaration
{
    private int foo;
    private int <warning descr="Multiple variables in one declaration">m_fooBaz</warning>, m_fooBar;
    private int m_fooBaz2;
    private int m_fooBar2;

    public void fooBar()
    {
        int <warning descr="Multiple variables in one declaration">fooBaz</warning>, fooBar;
        int fooBaz2;
        int fooBar2;

        for(int <warning descr="Multiple variables in one declaration">i</warning> =0, j=0;i<100;i++,j++)
        {

        }
    }
}
