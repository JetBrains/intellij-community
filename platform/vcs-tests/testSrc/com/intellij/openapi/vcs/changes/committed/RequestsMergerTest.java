// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.committed;

import com.intellij.openapi.util.RequestsMerger;
import com.intellij.util.Consumer;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.concurrency.SequentialTaskExecutor;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class RequestsMergerTest extends TestCase {
  public void testManyIntoOne() {
    final MyTestVictim victim = new MyTestVictim();
    final RequestsMerger merger = new RequestsMerger(victim, victim);

    for (int i = 0; i < 50; i++) {
      merger.request();
      if (i == 0) {
        Assert.assertEquals(true, victim.isExecutionSubmitted());
      }
      Assert.assertEquals(false, victim.isExecuted());
    }
  }

  public void testStartsSecondWhenRequestInTheMiddle() throws Exception {
    final MyDelayableTestVictim victim = new MyDelayableTestVictim();
    final RequestsMerger merger = new RequestsMerger(victim, victim);

    for (int i = 0; i < 20; i++) {
      merger.request();
      if (i == 0) {
        Assert.assertEquals(true, victim.isExecutionSubmitted());
      }
      Assert.assertEquals(false, victim.isExecuted());
    }

    victim.doDelayedRun();
    synchronized (this) {
      try {
        wait(50);
      }
      catch (InterruptedException e) {
        //
      }
    }

    for (int i = 0; i < 20; i++) {
      merger.request();
      Assert.assertEquals(false, victim.isExecutionSubmitted());
      Assert.assertEquals(false, victim.isExecuted());  // not completed
    }

    victim.allowRun();
    synchronized (this) {
      try {
        wait(50);
      }
      catch (InterruptedException e) {
        //
      }
    }
    Assert.assertEquals(true, victim.isExecuted()); // 1st finished
    Assert.assertEquals(true, victim.isExecutionSubmitted()); // 2nd submitted
    Assert.assertTrue(victim.isChildExited());
    victim.myThread.get();
  }

  public void testAfterRefreshIsCalled() throws Exception {
    SimpleExecutor executor = null;
    try {
      executor = new SimpleExecutor();
      final SimpleRunnable runnable = new SimpleRunnable();
      final RequestsMerger merger = new RequestsMerger(runnable, executor);

      Assert.assertTrue(! runnable.isStarted());
      merger.request();
      TimeoutUtil.sleep(50);
      // after a while first request started execution, so other requests merges into one next
      for (int i = 0; i < 20; i++) {
        merger.request();
      }
      Assert.assertTrue(runnable.isStarted());
      Assert.assertTrue(! runnable.isFinished());
      Assert.assertEquals(0, runnable.getCnt());

      // this will complete first request
      runnable.letGo();
      TimeoutUtil.sleep(50);
      // but there's also second one
      Assert.assertTrue(runnable.isStarted());
      Assert.assertTrue(! runnable.isFinished());
      Assert.assertEquals(1, runnable.getCnt());
      // this will complete all 2. no more yet.
      runnable.letGo();
      TimeoutUtil.sleep(50);
      Assert.assertTrue(! runnable.isStarted());
      Assert.assertTrue(runnable.isFinished());
      Assert.assertEquals(2, runnable.getCnt());

      // now lets test after-execution
      final SimpleRunnable checker = new SimpleRunnable();
      // this will add waiter + 1 request
      merger.waitRefresh(checker);
      // still waiting
      TimeoutUtil.sleep(50);
      Assert.assertTrue(runnable.isStarted());
      Assert.assertTrue(! runnable.isFinished());
      Assert.assertEquals(2, runnable.getCnt());

      Assert.assertTrue(! checker.isStarted());
      Assert.assertTrue(! checker.isFinished());
      Assert.assertEquals(0, checker.getCnt());
      // this will start runnable and start checker but not finish checker
      runnable.letGo();
      TimeoutUtil.sleep(50);
      Assert.assertTrue(! runnable.isStarted());
      Assert.assertTrue(runnable.isFinished());
      Assert.assertEquals(3, runnable.getCnt());

      Assert.assertTrue(checker.isStarted());
      Assert.assertTrue(! checker.isFinished());
      Assert.assertEquals(0, checker.getCnt());
      checker.letGo();
      TimeoutUtil.sleep(50);
      Assert.assertTrue(! checker.isStarted());
      Assert.assertTrue(checker.isFinished());
      Assert.assertEquals(1, checker.getCnt());
    } finally {
      if (executor != null) {
        executor.dispose();
      }
    }
  }

  private static class SimpleExecutor implements Consumer<Runnable> {
    private final ExecutorService myExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("Req Merge Test");

    @Override
    public void consume(Runnable runnable) {
      myExecutor.execute(runnable);
    }

    public void dispose() throws InterruptedException {
      myExecutor.shutdownNow();
      assertTrue(myExecutor.awaitTermination(100, TimeUnit.SECONDS));
    }
  }

  private static final class SimpleRunnable implements Runnable {
    private int myCnt;
    private boolean myStarted;
    private boolean myFinished;
    private final Semaphore mySemaphore;
    private final Object myLock;

    private SimpleRunnable() {
      mySemaphore = new Semaphore();
      myLock = new Object();
    }

    private int getCnt() {
      synchronized (myLock) {
        return myCnt;
      }
    }

    private boolean isStarted() {
      synchronized (myLock) {
        return myStarted;
      }
    }

    private boolean isFinished() {
      synchronized (myLock) {
        return myFinished;
      }
    }

    public void letGo() {
      synchronized (myLock) {
        mySemaphore.up();
      }
    }

    @Override
    public void run() {
      synchronized (myLock) {
        assert ! myStarted;
        myStarted = true;
        myFinished = false;
      }
      mySemaphore.down();
      mySemaphore.waitFor();
      synchronized (myLock) {
        myStarted = false;
        myFinished = true;
        ++ myCnt;
      }
    }
  }

  private static final class MyDelayableTestVictim extends MyTestVictim {
    private final Semaphore mySemaphore;
    private volatile boolean myChildExited;
    private Future<?> myThread;

    private MyDelayableTestVictim() {
      mySemaphore = new Semaphore();
    }

    @Override
    public void doDelayedRun() {
      // another thread
      final Semaphore local = new Semaphore();
      local.down();
      myThread = AppExecutorUtil.getAppExecutorService().submit(() -> {
          try {
            local.up();
            myRunnable.run();
          }
          finally {
            myChildExited = true;
          }
        });
      local.waitFor();
      myExecutionSubmitted = false;   // hack: to check further submissions
    }

    @Override
    public void run() {
      mySemaphore.down();
      mySemaphore.waitFor();
      super.run();
    }

    public void allowRun() {
      mySemaphore.up();
    }

    public boolean isChildExited() {
      return myChildExited;
    }
  }

  private static class MyTestVictim implements Runnable, Consumer<Runnable> {
    protected boolean myExecutionSubmitted;
    private boolean myExecuted;
    protected Runnable myRunnable;

    @Override
    public void consume(Runnable runnable) {
      Assert.assertFalse(myExecutionSubmitted);
      myExecutionSubmitted = true;
      myRunnable = runnable;
    }

    @Override
    public void run() {
      myExecuted = true;
      myExecutionSubmitted = false;
    }

    public void doDelayedRun() {
      myRunnable.run();
    }

    public boolean isExecutionSubmitted() {
      return myExecutionSubmitted;
    }

    public boolean isExecuted() {
      return myExecuted;
    }

    public void reset() {
      myExecuted = false;
      myExecutionSubmitted = false;
    }
  }
}
