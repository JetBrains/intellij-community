package com.siyeh.igtest.threading;

public class SynchronizedMethodInspection
{
    public synchronized void fooBar()
    {

    }
    
    public synchronized native void fooBaz();
}
