package com.siyeh.igtest.bugs;

public class MisspelledHashcodeInspection
{
    private int m_bar;

    public MisspelledHashcodeInspection()
    {
        m_bar = 0;
    }

    public int hashcode()
    {
        return m_bar;
    }
}
