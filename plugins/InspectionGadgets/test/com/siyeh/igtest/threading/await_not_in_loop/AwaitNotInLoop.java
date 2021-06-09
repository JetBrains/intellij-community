package com.siyeh.igtest.threading;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.TimeUnit;
import java.util.Date;

public class AwaitNotInLoop
{
    private Object lock;

    public  void foo(Condition condition)
    {
        try
        {
            lock.wait();
            condition.<warning descr="Call to 'await()' is not in loop">await</warning>();
            condition.<warning descr="Call to 'awaitUninterruptibly()' is not in loop">awaitUninterruptibly</warning>();
            condition.<warning descr="Call to 'awaitNanos()' is not in loop">awaitNanos</warning>(300);
            condition.<warning descr="Call to 'awaitUntil()' is not in loop">awaitUntil</warning>(new Date());
            condition.<warning descr="Call to 'await()' is not in loop">await</warning>(300, TimeUnit.MICROSECONDS);
        }
        catch(InterruptedException e)
        {
        }
    }

  public static Trippable boo(Condition condition) {
    return () -> condition.await();
  }

  interface Trippable {
      public void trip() throws Exception;
  }
}
