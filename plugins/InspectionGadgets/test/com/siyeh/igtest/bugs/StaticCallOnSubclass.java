package com.siyeh.igtest.bugs;

public class StaticCallOnSubclass {
                    
    public void foo() throws InterruptedException {
        Thread.sleep(1000L);
        InnerThread.sleep(1000L);
    }
    
    class InnerThread extends Thread
    {

    }
}
