package com.siyeh.igtest.threading;

public class FieldAccessedSynchronizedAndUnsynchronized
{
    private final Object m_lock = new Object();
    private Object m_contents = new Object();

    public void foo()
    {
        synchronized(m_lock)
        {
            m_contents = new Object();
        }
    }

    public Object getContents()
    {
        return m_contents;
    }

}
