package com.siyeh.igtest.threading;

public class ThreadPriorityInspection
{
    public void foo()
    {
        final Thread thread = new Thread();
        thread.setPriority(3);
        thread.setName("bar");
    }
}
