package com.siyeh.igtest.threading;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SafeLock {

    public void test1()
    {
        final Lock lock = new ReentrantLock();
        <warning descr="'Lock' should be locked in front of a 'try' block and unlocked in the corresponding 'finally' block">lock.lock()</warning>;
    }

    public void test2()
    {
        final Lock lock = new ReentrantLock();
        <warning descr="'Lock' should be locked in front of a 'try' block and unlocked in the corresponding 'finally' block">lock.lock()</warning>;
        lock.unlock();
    }

    public void test3()
    {
        final Lock lock = new ReentrantLock();
        lock.lock();
        String s;
        try{
        } finally{
            lock.unlock();
        }
    }

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private boolean active = false;

    public boolean isActive() throws Exception {
        <warning descr="'ReentrantReadWriteLock' should be locked in front of a 'try' block and unlocked in the corresponding 'finally' block">lock.readLock().lock()</warning>;
        boolean result = active;
        lock.readLock().unlock();
        return result;
    }

    boolean isActive2() throws Exception {
        lock.readLock().lock();
        try {
            return active;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void activate() {
        active = true;
    }
}
