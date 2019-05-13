package com.siyeh.igtest.threading.synchronization_on_lock_object;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SynchronizationOnLockObjectInspection
{
    private final Lock lock = new ReentrantLock();
    private final ReadWriteLock lock2 = new ReentrantReadWriteLock();


    public  void barzoomb() throws InterruptedException {
        synchronized (<warning descr="Synchronization on a 'java.util.concurrent.locks.Lock' object is unlikely to be intentional">lock</warning>) {
        }
        synchronized (<warning descr="Synchronization on a 'java.util.concurrent.locks.ReadWriteLock' object is unlikely to be intentional">lock2</warning>) {
            
        }
    }
}
