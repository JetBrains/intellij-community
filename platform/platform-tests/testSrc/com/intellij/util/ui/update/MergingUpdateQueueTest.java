/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.util.ui.update;

import com.intellij.testFramework.UsefulTestCase;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.WaitFor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MergingUpdateQueueTest extends UsefulTestCase {
  public void testOnShowNotify() throws Exception {
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

  public void testPriority() throws Exception {
    final boolean[] attemps = new boolean[3];

    final MyQueue queue = new MyQueue();

    final MyUpdate first = new MyUpdate("addedFirstButRunLast") { // default priority is 999
      @Override
      public void run() {
        super.run();
        attemps[0] = true;
        assertTrue(attemps[1]);
        assertTrue(attemps[2]);
      }
    };

    final MyUpdate second = new MyUpdate("addedSecondButRunFirst", Update.HIGH_PRIORITY) {
      @Override
      public void run() {
        super.run();
        assertFalse(attemps[0]);
        attemps[1] = true;
        assertFalse(attemps[2]);
      }
    };

    final MyUpdate third = new MyUpdate("addedThirdButRunSecond", 100) {
      @Override
      public void run() {
        super.run();
        assertFalse(attemps[0]);
        assertTrue(attemps[1]);
        attemps[2] = true;
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

  public void testDoNoExecuteExpired() throws Throwable {

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


  public void testOnShowNotifyMerging() throws Exception {
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

  public void testExecuteWhenActive() throws Exception {
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

  public void testMergeWhenActive() throws Exception {
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

  public void testEatByQueue() throws Exception {
    executeEatingTest(false);
  }

  public void testEatUpdatesInQueue() throws Exception {
    executeEatingTest(true);
  }

  private void executeEatingTest(boolean foodFirst) throws Exception{
    final MyQueue queue = new MyQueue();
    queue.showNotify();

    final MyUpdate food = new MyUpdate("food");
    MyUpdate hungry = new MyUpdate("hungry") {
      @Override
      public boolean canEat(Update update) {
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

  public void testCuncurrentFlushing() throws Exception {
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

  public void testConcurrentQueing() throws Exception {
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

    public boolean isExecuted() {
      return myExecuted;
    }
  }

  private static class MyQueue extends MergingUpdateQueue {
    private boolean myExecuted;

    MyQueue() {
      this(400);
    }

    MyQueue(int mergingTimeSpan) {
      super("Test", mergingTimeSpan, false, null);
      setPassThrough(false);
    }

    @Override
    public void run() {

    }

    public void onTimer() {
      super.run();
    }

    @Override
    protected void execute(@NotNull final Update[] update) {
      super.execute(update);
      myExecuted = true;
    }

    public boolean wasExecuted() {
      return myExecuted;
    }

    @Override
    protected boolean isModalityStateCorrect() {
      return true;
    }
  }

  private void waitForExecution(final MyQueue queue) {
    queue.onTimer();
    new WaitFor(5000) {
      @Override
      protected boolean condition() {
        return queue.wasExecuted();
      }
    };
  }

  public void testReallyMergeEqualIdentityEqualPriority() {
    final MyQueue queue = new MyQueue();

    final AtomicInteger count = new AtomicInteger();
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
    ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(100);
    List<Future> futures = ContainerUtil.newArrayList();
    for (int i = 0; i < 10; i++) {
      ScheduledFuture<?> future = executor.schedule(new Runnable() {
        @Override
        public void run() {
          for (int j = 0; j < 100; j++) {
            TimeoutUtil.sleep(1);
            queue.queue(new Update(new Object()) {
              @Override
              public void run() {
                count.incrementAndGet();
              }
            });
          }
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

}
