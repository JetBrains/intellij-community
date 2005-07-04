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
        getContents();
    }

    private Object getContents()
    {
        getContents2();
        return m_contents;
    }

    private void getContents2() {
        getContents();
    }

}
