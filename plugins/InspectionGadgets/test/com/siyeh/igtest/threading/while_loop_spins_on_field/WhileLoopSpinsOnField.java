package com.siyeh.igtest.threading.while_loop_spins_on_field;

import java.io.IOException;
import java.io.InputStream;

public class WhileLoopSpinsOnField
{
    private Object test = null;
    private int testInt = 3;
    private volatile int testVolatileInt = 3;

    public  void foo()
    {
        <warning descr="'while' loop spins on field">while</warning>(test!=null);
        <warning descr="'while' loop spins on field">while</warning>(test!=null)
        {
            System.out.println("");
        }
        <warning descr="'while' loop spins on field">while</warning>(testInt!=3)
        {

        }
        while(testVolatileInt!=3);

    }
}
class WhileLoopSpinsOnFieldFalsePositive {

    private InputStream source;

    private long remaining;

    WhileLoopSpinsOnFieldFalsePositive(InputStream source) {
        if (source == null) {
            throw new NullPointerException("source is null");
        }
        this.source = source;
        remaining = 0L;
    }

    public long nextElement() throws IOException {
        while (remaining > 0) {
            remaining -= source.skip(remaining);
        }
        return -1L;
    }
}
class WhileLoopSpinsOnField2
{
    private int quantity_;

    public static void main(final String[] args)
    {
        final WhileLoopSpinsOnField2 wlsof = new WhileLoopSpinsOnField2();
        wlsof.countdown();
    }

    private WhileLoopSpinsOnField2()
    {
        quantity_ = 10;
    }

    private void countdown()
    {
        while (quantity_ != 0)
        {
            System.out.println(quantity_);
            quantity_--;
        }
    }
}
class WhileLoopSpinsOnFieldFalsePosDemo {
    private boolean field = false;

    public synchronized void setAndNotify() {
        field = true;
        this.notifyAll();
    }

    public synchronized void waitForStuff() throws InterruptedException {
        // IDEA incorrectly reports "'while' loop spins on field" here:
        while (!field) {    // <— this line
            this.wait();    // this has the effect of synchronizing the field correctly
        }
    }
}
