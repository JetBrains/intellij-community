package com.siyeh.igtest.serialization;

public class NonserializableGrandarent
{
    private int m_arg;
    public NonserializableGrandarent(int arg)
    {
        super();
        m_arg = arg;
        bar(m_arg);
    }

    private void bar(int arg)
    {
    }
}
