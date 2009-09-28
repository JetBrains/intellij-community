package com.siyeh.igtest.threading;

public class NakedNotifyInspection
{
    public void foo()
    {
        final Object bar = new Object();
        synchronized (this) {
            bar.toString();
            bar.notify();
        }
        synchronized (this) {
            notifyAll();
        }
        synchronized (this) {
            notify();
        }
    }

    public synchronized void bar()
    {
        notifyAll();
    }
}
