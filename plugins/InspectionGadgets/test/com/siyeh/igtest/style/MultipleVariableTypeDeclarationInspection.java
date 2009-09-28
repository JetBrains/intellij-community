package com.siyeh.igtest.style;

public class MultipleVariableTypeDeclarationInspection
{
    private int m_fooBaz, m_fooBar[];

    public void fooBar()
    {
         int fooBaz,   // comment1
                 fooBar[];   //comment2
    }
}
