package com.siyeh.igtest.style;

public class MultipleVariableDeclarationInspection
{
    private int foo;
    private int m_fooBaz, m_fooBar;
    private int m_fooBaz2;
    private int m_fooBar2;

    public void fooBar()
    {
        int fooBaz, fooBar;
        int fooBaz2;
        int fooBar2;

        for(int i =0, j=0;i<100;i++,j++)
        {

        }
    }
}
