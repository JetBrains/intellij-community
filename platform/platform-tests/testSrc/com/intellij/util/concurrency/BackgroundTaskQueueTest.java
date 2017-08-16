/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.concurrency;

import com.intellij.openapi.progress.BackgroundTaskQueue;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestCase;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.TimeoutUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntConsumer;

/**
 * <p>Test for {@link BackgroundTaskQueue}.</p>
 * <p>As BackgroundTaskQueue has different execution strategy for tests and for production, this test case pretends not to be a test,
 * but not in all cases. This is a bit hacky, but I didn't want to change {@link ProgressManagerImpl} logic.
 * <ul>
 * <li>Application IS in a unit test mode.</li>
 * <li>Application IS in the headless mode, because it's needed to avoid showing UI.</li>
 * <li>The executed {@link Task Tasks} are not headless.</li>
 * <li>The test is started not from UI thread.</li>
 * </ul></li></p>
 */
public class BackgroundTaskQueueTest extends PlatformTestCase {
  private BackgroundTaskQueue myQueue;
  private ThreadRunner myThreadRunner;
  private Random myRandom;

  @Override
  protected void setUp() {
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        super.setUp();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }

      myQueue = new BackgroundTaskQueue(myProject, "test queue");
      myQueue.setForceAsyncInTests(true, null);
    });
    myThreadRunner = new ThreadRunner();
    myRandom = new Random();
  }

  @Override
  protected void tearDown() {
    myThreadRunner.finish();

    EdtTestUtil.runInEdtAndWait((() -> {
      myQueue.clear();
      myQueue = null;

      try {
        super.tearDown();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    }));
  }

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  public void testSingleSuccessfullTask() throws InterruptedException {
    TestTask task = new TestTask();
    myQueue.run(task);
    waitForTasks(task);
    assertSucceeded(task);
  }

  public void testSingleCancelledTask() throws InterruptedException {
    TestTask task = new TestTask() {
      @Override
      protected void execute(ProgressIndicator indicator) {
        sleep50();
        indicator.cancel();
      }
    };
    myQueue.run(task);
    waitForTasks(task);
    assertEquals(TaskState.CANCELLED, task.getState());
  }

  public void testSingleExceptionTask() throws InterruptedException {
    TestTask task = new TestTask() {
      @Override
      protected void execute(ProgressIndicator indicator) {
        sleep50();
        throw new NullPointerException("NPE");
      }
    };
    myQueue.run(task);
    waitForTasks(task);
    assertEquals(TaskState.EXCEPTION, task.getState());
  }

  /**
   * Start one task several times from several threads.
   * Finally task should complete successfully.
   */
  public void testOneTaskRunSeveralTimes() throws InterruptedException {
    final int THREADS = 3;
    final int RUNS_PER_THREAD = 10;
    final int RUNS = THREADS * RUNS_PER_THREAD;

    Semaphore semaphore = new Semaphore(1 - RUNS);

    int[] succeeded = new int[]{0};
    final Task.Backgroundable task = new Task.Backgroundable(getProject(), "Test Task", true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        succeeded[0]++;
        semaphore.release();
      }
    };

    myThreadRunner.run(THREADS, (i) -> {
      for (int j = 0; j < RUNS_PER_THREAD; j++) {
        myQueue.run(task);
      }
    });

    semaphore.tryAcquire(RUNS, 1000, TimeUnit.MILLISECONDS);
    assertEquals(RUNS, succeeded[0]);
  }

  /**
   * Start several tasks from a single thread. Wait for all to successfully complete.
   */
  public void testSeveralTasksStartedFromSingleThread() throws InterruptedException {
    TestTask[] tasks = createSeveralTasks();
    for (TestTask task : tasks) {
      myQueue.run(task);
    }
    waitForTasks(tasks);
    assertTaskState(tasks, TaskState.SUCCEEDED);
  }

  /**
   * Start several tasks from different threads. All should successfully complete.
   */
  public void testSeveralSuccessfulTasksStartedFromDifferentThreads() throws InterruptedException {
    final TestTask[] tasks = createSeveralTasks();

    myThreadRunner.run(tasks.length, (i) -> myQueue.run(tasks[i]));

    waitForTasks(tasks);
    assertTaskState(tasks, TaskState.SUCCEEDED);
  }

  /**
   * Create 18 tasks: 6 successful, 6 cancelled, 6 throwing exception. Start them from different threads, so that each thread run
   * tasks with different result.
   */
  public void testSeveralDifferentlyEndingTasksStartedFromDifferentThreads() throws InterruptedException {
    final TestTask[] successful = new TestTask[6];
    final TestTask[] cancelled = new TestTask[6];
    final TestTask[] exceptioned = new TestTask[6];
    for (int i = 0; i < 6; i++) {
      successful[i] = new TestTask();
    }
    for (int i = 0; i < 6; i++) {
      cancelled[i] = new TestTask() {
        @Override
        protected void execute(ProgressIndicator indicator) {
          sleep50();
          throw new ProcessCanceledException();
        }
      };
    }
    for (int i = 0; i < 6; i++) {
      exceptioned[i] = new TestTask() {
        @Override
        protected void execute(ProgressIndicator indicator) {
          sleep50();
          throw new RuntimeException();
        }
      };
    }

    myThreadRunner.run(3, (i) -> {
      myQueue.run(successful[i]);
      myQueue.run(successful[i + 3]);
      myQueue.run(cancelled[i]);
      myQueue.run(cancelled[i + 3]);
      myQueue.run(exceptioned[i]);
      myQueue.run(exceptioned[i + 3]);
    });

    waitForTasks(successful);
    waitForTasks(cancelled);
    waitForTasks(exceptioned);

    assertTaskState(successful, TaskState.SUCCEEDED);
    assertTaskState(cancelled, TaskState.CANCELLED);
    assertTaskState(exceptioned, TaskState.EXCEPTION);
  }

  public void testTasksAreNotParallel() throws Exception {
    final int THREADS = 3;
    final int RUNS_PER_THREAD = 10;
    final int RUNS = THREADS * RUNS_PER_THREAD;

    final boolean[] bool = new boolean[]{false};
    final Semaphore semaphore = new Semaphore(1 - RUNS);

    final Task.Backgroundable task = new Task.Backgroundable(myProject, "Test", false) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Assert.assertFalse(bool[0]);
        bool[0] = true;
        sleepX(17);
        semaphore.release();
        Assert.assertTrue(bool[0]);
        bool[0] = false;
      }
    };

    final AtomicInteger cntThreads = new AtomicInteger(THREADS);
    myThreadRunner.run(THREADS, (i) -> {
      for (int j = 0; j < RUNS_PER_THREAD; j++) {
        sleepX(7);
        myQueue.run(task);
      }
      cntThreads.decrementAndGet();
    });

    semaphore.tryAcquire(RUNS, 5000, TimeUnit.MILLISECONDS);

    Assert.assertTrue(myQueue.isEmpty());
    Assert.assertEquals(0, cntThreads.get());
    myThreadRunner.finish();
  }


  private void assertSucceeded(TestTask task) {
    assertEquals(TaskState.SUCCEEDED, task.getState());
  }

  private void assertTaskState(TestTask[] tasks, TaskState state) {
    for (TestTask task : tasks) {
      assertEquals(state, task.getState());
    }
  }

  private TestTask[] createSeveralTasks() {
    final TestTask[] tasks = new TestTask[10];
    for (int i = 0; i < tasks.length; i++) {
      tasks[i] = new TestTask();
    }
    return tasks;
  }

  private void waitForTasks(TestTask... tasks) throws InterruptedException {
    for (TestTask task : tasks) {
      task.waitFor(1000);
    }
  }

  private static void sleep50() {
    TimeoutUtil.sleep(50);
  }

  private void sleepX(final int intervalMs) {
    TimeoutUtil.sleep(myRandom.nextInt(intervalMs) + 1);
  }

  private enum TaskState {
    CREATED, RUNNING, SUCCEEDED, EXCEPTION, CANCELLED;

    boolean isComplete() {
      return this == SUCCEEDED || this == EXCEPTION || this == CANCELLED;
    }
  }

  private class TestTask extends Task.Backgroundable {
    private final AtomicReference<TaskState> myState = new AtomicReference<>(TaskState.CREATED);
    private final Semaphore mySemaphore = new Semaphore(0);

    public TestTask() {
      super(BackgroundTaskQueueTest.this.getProject(), "Test Task", true);
    }

    protected void execute(ProgressIndicator indicator) {
      for (int i = 0; i < 10000; i++) {
        Math.sin(i);
      }
    }

    @NotNull
    public TaskState getState() {
      return myState.get();
    }

    public boolean isComplete() {
      return myState.get().isComplete();
    }


    @Override
    public final void run(@NotNull ProgressIndicator indicator) {
      myState.compareAndSet(TaskState.CREATED, TaskState.RUNNING);
      execute(indicator);
    }

    @Override
    public final void onCancel() {
      myState.compareAndSet(TaskState.RUNNING, TaskState.CANCELLED);
    }

    @Override
    public final void onSuccess() {
      myState.compareAndSet(TaskState.RUNNING, TaskState.SUCCEEDED);
    }

    @Override
    public final void onThrowable(@NotNull Throwable error) {
      myState.compareAndSet(TaskState.RUNNING, TaskState.EXCEPTION);
    }

    @Override
    public final void onFinished() {
      mySemaphore.release();
      assertTrue(myState.get() != TaskState.RUNNING);
      assertTrue(myState.get() != TaskState.CREATED);
    }

    public void waitFor(int timeout) throws InterruptedException {
      assertTrue(mySemaphore.tryAcquire(1, timeout, TimeUnit.MILLISECONDS));
      mySemaphore.release();
    }

    @Override
    public boolean isHeadless() {
      return false;
    }
  }

  private static class ThreadRunner {
    private final List<Thread> myThreads = new ArrayList<>();

    public void run(int count, IntConsumer task) {
      for (int i = 0; i < count; i++) {
        int threadIndex = i;
        Thread thread = new Thread("BTQ-" + threadIndex) {
          @Override
          public void run() {
            task.accept(threadIndex);
          }
        };
        thread.start();
        myThreads.add(thread);
      }
    }

    public void finish() {
      ConcurrencyUtil.joinAll(myThreads);
    }
  }
}
