package com.siyeh.igtest.threading;

public class SynchronizeOnThisInspection
{
    private Object m_lock = new Object();

    public void fooBar() throws InterruptedException {
        synchronized (this)
        {
            this.wait();
            this.notify();
            this.notifyAll();
            wait(1000L);
            notify();
            notifyAll();
            System.out.println("");
        }
    }
}
