package com.siyeh.igtest.threading;

public class ThreadRunInspection
{
    public void foo()
    {
        final Runnable runnable = new Runnable()
        {
            public void run()
            {

            }
        };
        final Thread thread = new Thread(runnable);
        thread.run();

        final MyThread thread2 = new MyThread(runnable);
        thread2.run();
    }
}
