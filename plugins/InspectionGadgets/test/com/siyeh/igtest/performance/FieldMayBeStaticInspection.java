package com.siyeh.igtest.performance;

public class FieldMayBeStaticInspection
{
    private final int m_fooBar = 3;
    private final int m_fooBaz = m_fooBar;

    {
        System.out.println("m_fooBaz = " + m_fooBaz);
    }
}