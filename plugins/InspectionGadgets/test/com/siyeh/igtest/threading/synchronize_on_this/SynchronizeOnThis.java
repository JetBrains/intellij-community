package com.siyeh.igtest.threading;

public class SynchronizeOnThis
{
    private Object m_lock = new Object();

    public void fooBar() throws InterruptedException {
        synchronized (<warning descr="Lock operations on 'this' may have unforeseen side-effects">this</warning>)
        {
            this.<warning descr="Lock operations on 'this' may have unforeseen side-effects">wait</warning>();
            this.<warning descr="Lock operations on 'this' may have unforeseen side-effects">notify</warning>();
            this.<warning descr="Lock operations on 'this' may have unforeseen side-effects">notifyAll</warning>();
            <warning descr="Lock operations on 'this' may have unforeseen side-effects">wait</warning>(1000L);
            <warning descr="Lock operations on 'this' may have unforeseen side-effects">notify</warning>();
            <warning descr="Lock operations on 'this' may have unforeseen side-effects">notifyAll</warning>();
            System.out.println("");
        }
    }
}
