package com.siyeh.igtest.threading;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class SafeLockInspection{

    public void test1()
    {
        final Lock lock = new ReentrantLock();
        lock.lock();
    }

    public void test2()
    {
        final Lock lock = new ReentrantLock();
        lock.lock();
        lock.unlock();
    }

    public void test3()
    {
        final Lock lock = new ReentrantLock();
        lock.lock();
        try{
        } finally{
            lock.unlock();
        }
    }
}
