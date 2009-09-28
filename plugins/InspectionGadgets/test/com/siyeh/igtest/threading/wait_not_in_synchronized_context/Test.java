package com.siyeh.igtest.threading.wait_not_in_synchronized_context;

public class Test {
	private final Object lock = new Object();
	private final Object otherLock = new Object();

	void foo() throws InterruptedException {
        otherLock.wait();
		synchronized (this) {
			lock.wait();
		}
        synchronized (lock) {
            wait();
        }
	}

    synchronized void bar() throws InterruptedException {
        wait();
    }

	public static void main(String[] args) throws InterruptedException {
		new Test().foo();
	}

}