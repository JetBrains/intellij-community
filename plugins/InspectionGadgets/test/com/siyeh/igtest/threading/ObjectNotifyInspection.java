package com.siyeh.igtest.threading;

public class ObjectNotifyInspection
{
    public void foo()
    {
        final Object bar = new Object();
        bar.notify();
        notify();
    }
}
