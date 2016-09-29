package com.siyeh.igtest.threading.wait_not_in_synchronized_context;

public class WaitNotInSynchronizedContext {
  private final Object lock = new Object();
  private final Object otherLock = new Object();

  void foo() throws InterruptedException {
    <warning descr="Call to 'otherLock.wait()' while not synchronized on 'otherLock'">otherLock.wait()</warning>;
    synchronized (this) {
      <warning descr="Call to 'lock.wait()' while not synchronized on 'lock'">lock.wait()</warning>;
    }
    synchronized (lock) {
      <warning descr="Call to 'wait()' while not synchronized on 'this'">wait()</warning>;
    }
  }

  synchronized void bar() throws InterruptedException {
    wait();
  }

  public static void main(String[] args) throws InterruptedException {
    new WaitNotInSynchronizedContext().foo();
  }

}
class More {
  private final Object lock = new Object();

  public  synchronized void bar() {
    try {
      <warning descr="Call to 'lock.wait()' while not synchronized on 'lock'">lock.wait()</warning>;
    } catch(InterruptedException e) {}
  }

  public  void barzoomb() throws InterruptedException {
    synchronized (lock) {
      lock.wait();
    }
  }
}
