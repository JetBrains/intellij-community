// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.update;

import com.intellij.concurrency.JobScheduler;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.util.Alarm;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.WaitFor;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

public class MergingUpdateQueueTest extends LightPlatformTestCase {
  public void testOnShowNotify() {
    final MyUpdate first = new MyUpdate("first");
    final MyUpdate second = new MyUpdate("second");

    final MyQueue queue = new MyQueue();

    queue.queue(first);
    queue.queue(second);

    assertFalse(first.isExecuted());
    assertFalse(second.isExecuted());

    queue.showNotify();

    waitForExecution(queue);

    assertAfterProcessing(first, true, true);
    assertAfterProcessing(second, true, true);
  }

  public void testPriority() {
    final boolean[] attempts = new boolean[3];

    final MyQueue queue = new MyQueue();

    final MyUpdate first = new MyUpdate("addedFirstButRunLast") { // default priority is 999
      @Override
      public void run() {
        super.run();
        attempts[0] = true;
        assertTrue(attempts[1]);
        assertTrue(attempts[2]);
      }
    };

    final MyUpdate second = new MyUpdate("addedSecondButRunFirst", Update.HIGH_PRIORITY) {
      @Override
      public void run() {
        super.run();
        assertFalse(attempts[0]);
        attempts[1] = true;
        assertFalse(attempts[2]);
      }
    };

    final MyUpdate third = new MyUpdate("addedThirdButRunSecond", 100) {
      @Override
      public void run() {
        super.run();
        assertFalse(attempts[0]);
        assertTrue(attempts[1]);
        attempts[2] = true;
      }
    };

    queue.queue(first);
    queue.queue(second);
    queue.queue(third);

    waitForExecution(queue);

    assertAfterProcessing(first, true, true);
    assertAfterProcessing(second, true, true);
    assertAfterProcessing(third, true, true);
  }

  public void testDoNoExecuteExpired() {

    final boolean[] expired = new boolean[1];

    final MyUpdate first = new MyUpdate("first") {
      @Override
      public boolean isExpired() {
        return expired[0];
      }
    };
    final MyUpdate second = new MyUpdate("second");

    final MyQueue queue = new MyQueue();

    queue.queue(first);
    queue.queue(second);

    assertFalse(first.isExecuted());
    assertFalse(second.isExecuted());

    expired[0] = true;

    queue.showNotify();
    waitForExecution(queue);

    assertAfterProcessing(first, false, true);
    assertAfterProcessing(second, true, true);
  }


  public void testOnShowNotifyMerging() {
    final MyUpdate twin1 = new MyUpdate("twin");
    final MyUpdate twin2 = new MyUpdate("twin");

    final MyQueue queue = new MyQueue();

    queue.queue(twin1);
    queue.queue(twin2);

    assertFalse(twin1.isExecuted());
    assertFalse(twin2.isExecuted());

    queue.showNotify();
    waitForExecution(queue);

    assertAfterProcessing(twin1, false, true);
    assertAfterProcessing(twin2, true, true);
  }

  public void testExecuteWhenActive() {
    final MyQueue queue = new MyQueue();

    queue.showNotify();

    final MyUpdate first = new MyUpdate("first");
    final MyUpdate second = new MyUpdate("second");

    queue.queue(first);
    queue.queue(second);

    waitForExecution(queue);

    assertAfterProcessing(first, true, true);
    assertAfterProcessing(second, true, true);
  }

  public void testMergeWhenActive() {
    final MyQueue queue = new MyQueue();

    queue.showNotify();

    final MyUpdate twin1 = new MyUpdate("twin");
    final MyUpdate twin2 = new MyUpdate("twin");

    queue.queue(twin1);
    queue.queue(twin2);

    waitForExecution(queue);

    assertAfterProcessing(twin1, false, true);
    assertAfterProcessing(twin2, true, true);
  }

  public void testEatByQueue() {
    executeEatingTest(false);
  }

  public void testEatUpdatesInQueue() {
    executeEatingTest(true);
  }

  private static void executeEatingTest(boolean foodFirst) {
    final MyQueue queue = new MyQueue();
    queue.showNotify();

    final MyUpdate food = new MyUpdate("food");
    MyUpdate hungry = new MyUpdate("hungry") {
      @Override
      public boolean canEat(@NotNull Update update) {
        return update == food;
      }
    };

    if (foodFirst) {
      queue.queue(food);
      queue.queue(hungry);
    } else {
      queue.queue(hungry);
      queue.queue(food);
    }

    waitForExecution(queue);

    assertAfterProcessing(hungry, true, true);
    assertAfterProcessing(food, false, false);
  }

  public void testConcurrentFlushing() {
    final MyQueue queue = new MyQueue();
    queue.showNotify();

    queue.queue(new MyUpdate("update") {
      @Override
      public boolean isExpired() {
        queue.flush();
        return false;
      }
    });

    waitForExecution(queue);
  }

  public void testBlockingFlush() throws Exception {
    MyQueue queue = new MyQueue();
    queue.showNotify();
    AtomicReference<Object> executed = new AtomicReference<>();
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        queue.queue(new MyUpdate("update"));
        queue.flush();
        executed.set(queue.wasExecuted());
      }
      catch (RuntimeException | Error th) {
        executed.set(th);
      }
    });
    while (executed.get() == null) {
      PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue();
      Thread.sleep(50);
    }
    Object result = executed.get();
    if (result instanceof Throwable) {
      ExceptionUtil.rethrowUnchecked((Throwable)result);
    }
    assertTrue(Boolean.TRUE.equals(executed.get()));
  }

  public void testConcurrentQueueing() {
    final MyQueue queue = new MyQueue();
    queue.showNotify();

    queue.queue(new MyUpdate("update") {
      @Override
      public boolean isExpired() {
        queue.queue(new MyUpdate("update again"));
        return false;
      }
    });

    waitForExecution(queue);
  }


  private static void assertAfterProcessing(MyUpdate update, boolean shouldBeExecuted, boolean shouldBeProcessed) {
    assertEquals(shouldBeExecuted, update.isExecuted());
    assertEquals(shouldBeProcessed, update.wasProcessed());
  }

  private static class MyUpdate extends Update {

    private boolean myExecuted;

    MyUpdate(String s) {
      super(s);
    }

    MyUpdate(Object identity, int priority) {
      super(identity, priority);
    }

    @Override
    public void run() {
      myExecuted = true;
    }

    private boolean isExecuted() {
      return myExecuted;
    }
  }

  private static final class MyQueue extends MergingUpdateQueue {
    private boolean isExecuted;

    private MyQueue() {
      this(400);
    }

    private MyQueue(int mergingTimeSpan) {
      super("Test", mergingTimeSpan, false, null);
    }

    @Override
    protected @NotNull Runnable getFlushTask() {
      return EmptyRunnable.getInstance();
    }

    private void onTimer() {
      super.getFlushTask().run();
    }

    @Override
    protected void execute(@NotNull List<? extends Update> updates) {
      super.execute(updates);
      isExecuted = true;
    }

    boolean wasExecuted() {
      return isExecuted;
    }

    @Override
    protected boolean isModalityStateCorrect() {
      return true;
    }
  }

  private static void waitForExecution(@NotNull MyQueue queue) {
    queue.onTimer();
    new WaitFor(5000) {
      @Override
      protected boolean condition() {
        return queue.wasExecuted();
      }
    }.assertCompleted();
  }

  public void testReallyMergeEqualIdentityEqualPriority() {
    MyQueue queue = new MyQueue();
    AtomicInteger count = new AtomicInteger();
    for (int i = 0; i < 100; i++) {
      for (int j = 0; j < 100; j++) {
        queue.queue(new Update("foo" + j) {
          @Override
          public void run() {
            count.incrementAndGet();
          }
        });
      }
    }
    queue.showNotify();
    waitForExecution(queue);

    assertEquals(100, count.get());
  }

  public void testMultiThreadedQueueing() throws ExecutionException, InterruptedException {
    final MyQueue queue = new MyQueue(20);
    queue.showNotify();

    final AtomicInteger count = new AtomicInteger();
    ScheduledExecutorService executor = JobScheduler.getScheduler();
    List<Future> futures = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      ScheduledFuture<?> future = executor.schedule(() -> {
        for (int j = 0; j < 100; j++) {
          TimeoutUtil.sleep(1);
          queue.queue(new Update(new Object()) {
            @Override
            public void run() {
              count.incrementAndGet();
            }
          });
        }
      }, 0, TimeUnit.MILLISECONDS);
      futures.add(future);
    }

    for (Future future : futures) {
      future.get();
    }

    waitForExecution(queue);

    assertEquals(1000, count.get());
  }

  public void testSamePriorityQueriesAreExecutedInAdditionOrder() {
    final MyQueue queue = new MyQueue();

    StringBuilder expected = new StringBuilder();
    final StringBuilder actual = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      expected.append(i);
      final int finalI = i;
      queue.queue(new Update(new Object()) {
        @Override
        public void run() {
          actual.append(finalI);
        }
      });
    }
    queue.showNotify();
    waitForExecution(queue);

    assertEquals(expected.toString(), actual.toString());
  }

  public void testAddRequestsInPooledThreadDoNotExecuteConcurrently() throws InterruptedException {
    int delay = 10;
    MergingUpdateQueue queue = new MergingUpdateQueue("x", delay, true, null, getTestRootDisposable(), null, Alarm.ThreadToUse.POOLED_THREAD);
    CountDownLatch startedExecuting1 = new CountDownLatch(1);
    CountDownLatch canContinue = new CountDownLatch(1);
    queue.queue(new Update("1") {
      @Override
      public void run() {
        startedExecuting1.countDown();
        try {
          canContinue.await();
        }
        catch (InterruptedException e) {
          throw new RuntimeException(e);
        }
      }
    });
    assertTrue(startedExecuting1.await(10, TimeUnit.SECONDS));
    CountDownLatch startedExecuting2 = new CountDownLatch(1);
    queue.queue(new Update("2") {
      @Override
      public void run() {
        startedExecuting2.countDown();
      }
    });
    TimeoutUtil.sleep(delay + 1000);
    canContinue.countDown();
    assertTrue(startedExecuting2.await(10, TimeUnit.SECONDS));
  }
  public void testQueueInsideQueueMustNotInterfereWithWaitForAllExecuted() throws Exception {
    MergingUpdateQueue queue = new MergingUpdateQueue(getTestName(false), 100, true, null, getTestRootDisposable(), null, Alarm.ThreadToUse.POOLED_THREAD);
    CountDownLatch latch = new CountDownLatch(1);
    AtomicBoolean secondExecuted = new AtomicBoolean();
    queue.queue(Update.create("first", () -> {
      queue.queue(Update.create("second", () -> {
        secondExecuted.set(true);
      }));
      TimeoutUtil.sleep(1000);
      latch.countDown();
    }));

    queue.waitForAllExecuted(10, TimeUnit.SECONDS);
    assertEquals(0, latch.getCount());
    assertTrue(secondExecuted.get());
  }

  public void testMustRejectOnDispose() {
    MergingUpdateQueue queue = new MergingUpdateQueue(getTestName(false), 1_000_000, true, null, getTestRootDisposable(), null, Alarm.ThreadToUse.POOLED_THREAD);
    Update update = new Update(this) {
      @Override
      public void run() {
      }
    };
    queue.queue(update);
    assertThat(update.isRejected()).isFalse();
    Disposer.dispose(queue);
    assertThat(update.isRejected()).isTrue();
  }
}
