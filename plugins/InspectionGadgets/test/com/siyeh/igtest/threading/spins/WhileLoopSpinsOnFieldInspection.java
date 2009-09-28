package com.siyeh.igtest.threading.spins;

public class WhileLoopSpinsOnFieldInspection
{
    private Object test = null;
    private int testInt = 3;
    private volatile int testVolatileInt = 3;

    public  void foo()
    {
        while(test!=null);
        while(test!=null)
        {
            System.out.println("");
        }
        while(testInt!=3)
        {

        }
        while(testVolatileInt!=3);

    }
}
