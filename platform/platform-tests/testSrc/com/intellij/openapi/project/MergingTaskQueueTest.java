// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.MergingTaskQueue.SubmissionReceipt;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.junit5.TestApplication;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@TestApplication
@SuppressWarnings("unchecked")
public class MergingTaskQueueTest {
  private static final Logger LOG = Logger.getInstance(MergingTaskQueueTest.class);

  private final MergingTaskQueue queue = new MergingTaskQueue<>();

  @AfterEach
  public void clearQueue() {
    queue.cancelAllTasks();
    queue.disposePendingTasks();
  }

  private void runAllTasks() {
    while (true) {
      try (MergingTaskQueue.QueuedTask<?> nextTask = queue.extractNextTask()) {
        if (nextTask == null) return;
        nextTask.executeTask();
      }
    }
  }

  static abstract class TaskWithEquivalentObject implements MergeableQueueTask<TaskWithEquivalentObject> {
    private final @NotNull Object equivalenceObject;

    TaskWithEquivalentObject(@NotNull Object object) { equivalenceObject = object; }

    @Override
    public @Nullable TaskWithEquivalentObject tryMergeWith(@NotNull TaskWithEquivalentObject taskFromQueue) {
      if (taskFromQueue.getClass().equals(getClass()) && taskFromQueue.equivalenceObject.equals(equivalenceObject)) {
        return this;
      }
      return null;
    }

    @Override
    public void dispose() {

    }
  }

  static class LoggingTask implements MergeableQueueTask<LoggingTask> {
    private final @Nullable Collection<@NotNull Integer> performLog;
    private final @Nullable Collection<@NotNull Integer> disposeLog;
    private final BiFunction<@NotNull LoggingTask, @NotNull LoggingTask, @Nullable LoggingTask> tryMergeWithFn;
    private final int taskId;

    LoggingTask(int taskId, @Nullable Collection<Integer> performLog, @Nullable Collection<Integer> disposeLog) {
      this(taskId, performLog, disposeLog, (thiz, other) -> null);
    }

    LoggingTask(int taskId, @NotNull BiFunction<@NotNull LoggingTask, @NotNull LoggingTask, @Nullable LoggingTask> tryMergeWithFn) {
      this(taskId, null, null, tryMergeWithFn);
    }

    LoggingTask(int taskId, @Nullable Collection<Integer> performLog, @Nullable Collection<Integer> disposeLog,
                @NotNull BiFunction<@NotNull LoggingTask, @NotNull LoggingTask, @Nullable LoggingTask> tryMergeWithFn) {
      this.performLog = performLog;
      this.disposeLog = disposeLog;
      this.taskId = taskId;
      this.tryMergeWithFn = tryMergeWithFn;
    }


    @Override
    public @Nullable LoggingTask tryMergeWith(@NotNull LoggingTask taskFromQueue) {
      return tryMergeWithFn.apply(this, taskFromQueue);
    }

    @Override
    public void perform(@NotNull ProgressIndicator indicator) {
      if (performLog != null) performLog.add(taskId);
    }

    @Override
    public void dispose() {
      if (disposeLog != null) disposeLog.add(taskId);
    }
  }

  @Test
  public void testEquivalentTasksAreMerged() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      int taskId = i;
      queue.addTask(new TaskWithEquivalentObject("child") {
        @Override
        public void perform(@NotNull ProgressIndicator indicator) {
          childLog.add(taskId);
        }

        @Override
        public void dispose() {
          disposeLog.add(taskId);
        }
      });
    }

    runAllTasks();

    assertEquals(1, childLog.size(), "Only one child task should run, but were: " + childLog);
    assertEquals(100, disposeLog.size(), "All tasks must be disposed, but were: " + disposeLog);
  }

  @Test
  public void testCanReturnThatAsResultOfTryMerge() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      queue.addTask(new LoggingTask(i, childLog, disposeLog, (thiz, other) -> other /* always merges */));
    }

    runAllTasks();

    assertEquals(1, childLog.size(), "Only one child task should run, but were: " + childLog);
    assertEquals(100, disposeLog.size(), "All tasks must be disposed, but were: " + disposeLog);
  }

  @Test
  public void testCanReturnThisAsResultOfTryMerge() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      queue.addTask(new LoggingTask(i, childLog, disposeLog, (thiz, other) -> thiz /* always merges */));
    }

    runAllTasks();

    assertEquals(1, childLog.size(), "Only one child task should run, but were: " + childLog);
    assertEquals(100, disposeLog.size(), "All tasks must be disposed, but were: " + disposeLog);
  }

  @Test
  public void testDifferentClassesWithSameEquivalentAreNotMerged() {
    List<Integer> childLog = new ArrayList<>();
    final String commonEquivalence = "child";
    TaskWithEquivalentObject taskA = new TaskWithEquivalentObject(commonEquivalence) {
      @Override
      public void perform(@NotNull ProgressIndicator indicator) {
        childLog.add(1);
      }
    };

    TaskWithEquivalentObject taskB = new TaskWithEquivalentObject(commonEquivalence) {
      @Override
      public void perform(@NotNull ProgressIndicator indicator) {
        childLog.add(-1);
      }
    };

    //both taskA and taskB submit the same equality object, it must run both
    queue.addTask(taskA);
    queue.addTask(taskB);
    runAllTasks();
    assertEquals(2, childLog.size(), "All tasks should run, but were: " + childLog);
  }

  @Test
  public void testNonEquivalentTasksAreNotMerged() {
    List<Integer> childLog = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      int taskId = i;
      queue.addTask(new TaskWithEquivalentObject("child" + i) {
        @Override
        public void perform(@NotNull ProgressIndicator indicator) {
          childLog.add(taskId);
        }
      });
    }
    runAllTasks();
    assertEquals(100, childLog.size(), "Every child task are not unique, all must be executed: " + childLog);
  }

  @Test
  public void testNewTaskIsRunWhenMerged() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();

    for (int i = 0; i < 3; i++) {
      int taskId = i;
      queue.addTask(new TaskWithEquivalentObject("child") {
        @Override
        public void perform(@NotNull ProgressIndicator indicator) {
          childLog.add(taskId);
        }

        @Override
        public void dispose() {
          disposeLog.add(taskId);
        }
      });
    }

    runAllTasks();
    assertEquals(Collections.singletonList(2), childLog, "The last child task should run, but were: " + childLog);
    assertEquals(3, disposeLog.size(), "All tasks must be disposed, but were: " + disposeLog);
  }

  @Test
  public void testMergedTaskIsRunWhenMerged() {
    List<String> disposeLog = new ArrayList<>();
    List<String> childLog = new ArrayList<>();

    class TaskWithId implements MergeableQueueTask<TaskWithId> {
      protected final String taskId;

      TaskWithId(String taskId) {
        this.taskId = taskId;
      }

      @Override
      public void perform(@NotNull ProgressIndicator indicator) {
        childLog.add(taskId);
      }

      @Override
      public void dispose() {
        disposeLog.add(taskId);
      }

      @Nullable
      @Override
      public TaskWithId tryMergeWith(@NotNull TaskWithId taskFromQueue) {
        String newId = taskFromQueue.taskId + " " + taskId;
        return new TaskWithId(newId);
      }
    }
    for (int i = 0; i < 3; i++) {
      queue.addTask(new TaskWithId(String.valueOf(i)));
    }

    runAllTasks();
    assertEquals(Collections.singletonList("0 1 2"), childLog, "The last child task should run, but were: " + childLog);
    assertEquals(List.of("0", "1", "0 1", "2", "0 1 2"), disposeLog, "All tasks must be disposed, but were: " + disposeLog);
  }

  @Test
  public void testCancelledTask() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();

    MergeableQueueTask<?> task = new LoggingTask(1, childLog, disposeLog);

    queue.addTask(task);
    queue.cancelTask(task);

    runAllTasks();
    assertEquals(Collections.emptyList(), childLog, "Cancelled task must not run " + childLog);
    assertEquals(Collections.singletonList(1), disposeLog, "Cancelled task must dispose " + disposeLog);
  }

  @Test
  public void testMergedTaskShouldDispose() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();

    for (int i = 0; i < 3; i++) {
      int taskId = i;
      queue.addTask(new TaskWithEquivalentObject("child") {
        @Override
        public void perform(@NotNull ProgressIndicator indicator) {
          childLog.add(taskId);
        }

        @Override
        public void dispose() {
          disposeLog.add(taskId);
        }
      });
    }

    assertEquals(Collections.emptyList(), childLog, "No task should run by now " + childLog);
    assertEquals(Arrays.asList(0, 1), disposeLog, "older task must be disposed" + disposeLog);

    runAllTasks();

    assertEquals(Collections.singletonList(2), childLog, "The last task should only run " + childLog);
    assertEquals(Arrays.asList(0, 1, 2), disposeLog, "older task must be disposed" + disposeLog);
  }

  @Test
  public void testTasksAreDisposed() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();

    TaskWithEquivalentObject task = new TaskWithEquivalentObject("child") {
      @Override
      public void perform(@NotNull ProgressIndicator indicator) {
        childLog.add(1);
      }

      @Override
      public void dispose() {
        disposeLog.add(1);
      }
    };

    queue.addTask(task);
    queue.disposePendingTasks();

    assertEquals(Collections.emptyList(), childLog, "The last task should only run " + childLog);
    assertEquals(Arrays.asList(1), disposeLog, "older task must be disposed" + disposeLog);

    runAllTasks();
    assertEquals(Collections.emptyList(), childLog, "The last task should only run " + childLog);
    assertEquals(Arrays.asList(1), disposeLog, "older task must be disposed" + disposeLog);
  }

  @Test
  public void testMergedTasksShouldNotReserveEarlierSlots() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();

    for (int i = 1; i <= 3; i++) {
      int taskId = i;
      queue.addTask(new TaskWithEquivalentObject("child") {
        @Override
        public void perform(@NotNull ProgressIndicator indicator) {
          childLog.add(taskId);
        }

        @Override
        public void dispose() {
          disposeLog.add(taskId);
        }
      });

      queue.addTask(new TaskWithEquivalentObject("boss-" + i) {
        @Override
        public void perform(@NotNull ProgressIndicator indicator) {
          childLog.add(-taskId);
        }

        @Override
        public void dispose() {
          disposeLog.add(-taskId);
        }
      });
    }

    runAllTasks();
    assertEquals(Arrays.asList(-1, -2, 3, -3), childLog, "The last task should only run " + childLog);
    assertEquals(Arrays.asList(1, 2, -1, -2, 3, -3), disposeLog, "older task must be disposed " + disposeLog);
  }

  // IDEA-241378
  @Test
  public void testRunningTaskShouldNotBeDisposed() {
    AtomicReference<Boolean> isDisposed = new AtomicReference<>();
    queue.addTask(new TaskWithEquivalentObject("any") {
      @Override
      public void perform(@NotNull ProgressIndicator indicator) { }

      @Override
      public void dispose() {
        isDisposed.set(true);
      }
    });

    try (MergingTaskQueue.QueuedTask<?> task = queue.extractNextTask()) {
      queue.disposePendingTasks();
      assertNull(isDisposed.get());
      task.executeTask();
      fail();
    }
    catch (ProcessCanceledException ignore) {
      //OK
    }
    assertEquals(Boolean.TRUE, isDisposed.get());
  }

  // IDEA-241378
  @Test
  public void testRunningTaskIndicatorShouldBeCancelledOnDisposeRunningTasks() {
    CyclicBarrier b = new CyclicBarrier(2);
    AtomicReference<Boolean> isRun = new AtomicReference<>();
    queue.addTask(new TaskWithEquivalentObject("any") {
      @Override
      public void perform(@NotNull ProgressIndicator indicator) {
        for (int i = 0; i < 2; i++) {
          await(b);
        }
        isRun.set(indicator.isCanceled());
      }
    });

    Thread th = new Thread(() -> {
      try (MergingTaskQueue.QueuedTask<?> nextTask = queue.extractNextTask()) {
        nextTask.executeTask();
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }, getClass().getName() + "-thread");
    th.setDaemon(true);
    th.start();

    await(b);
    //now the task is in the middle
    queue.disposePendingTasks();
    await(b);

    //not it should complete
    try {
      th.join(5_000);
    }
    catch (InterruptedException e) {
      th.interrupt();
      fail();
    }

    assertEquals(Boolean.TRUE, isRun.get());
  }

  @Test
  public void testNoLeaks() {
    for (int i = 0; i < 1000; i++) {
      TaskWithEquivalentObject task = new TaskWithEquivalentObject("q-" + i) {
        @Override
        public void perform(@NotNull ProgressIndicator indicator) {
        }
      };

      queue.addTask(task);
      if (i % 3 == 0) {
        Disposer.dispose(task);
      }

      if (i % 3 == 1) {
        queue.cancelTask(task);
      }
    }

    runAllTasks();

    LeakHunter.checkLeak(queue, ProgressIndicator.class);
    LeakHunter.checkLeak(queue, TaskWithEquivalentObject.class);
  }

  @Test
  public void testNoDisposeLeaksOnClose() {
    AtomicBoolean disposeFlag = new AtomicBoolean(false);
    TaskWithEquivalentObject task = new TaskWithEquivalentObject(this) {
      @Override
      public void perform(@NotNull ProgressIndicator indicator) {
      }

      @Override
      public void dispose() {
        disposeFlag.set(true);
      }
    };

    queue.addTask(task);
    queue.disposePendingTasks();

    assertTrue(Disposer.isDisposed(task));
    assertTrue(disposeFlag.get());
  }

  @Test
  public void testNoDisposeLeaksOnClose2() {
    final AtomicBoolean myDisposeFlag = new AtomicBoolean(false);
    TaskWithEquivalentObject task = new TaskWithEquivalentObject(this) {
      @Override
      public void perform(@NotNull ProgressIndicator indicator) {
      }

      @Override
      public void dispose() {
        myDisposeFlag.set(true);
      }
    };

    queue.addTask(task);
    queue.cancelAllTasks();
    queue.disposePendingTasks();

    assertTrue(Disposer.isDisposed(task));
    assertTrue(myDisposeFlag.get());
  }

  @Test
  public void testNoDisposeLeaksOnClose3() {
    final AtomicBoolean myDisposeFlag = new AtomicBoolean(false);
    TaskWithEquivalentObject task = new TaskWithEquivalentObject(this) {
      @Override
      public void perform(@NotNull ProgressIndicator indicator) {
      }

      @Override
      public void dispose() {
        myDisposeFlag.set(true);
      }
    };

    queue.addTask(task);
    queue.cancelTask(task);
    queue.disposePendingTasks();

    assertTrue(Disposer.isDisposed(task));
    assertTrue(myDisposeFlag.get());
  }

  @Test
  public void submittedTasksCounterDoesNotDecrease() {
    AtomicInteger counter = new AtomicInteger(0);
    Random random = new Random();

    Supplier<LoggingTask> taskFactory = () -> new LoggingTask(counter.incrementAndGet(), (thiz, other) -> {
      return switch (random.nextInt(4)) {
        case 0 -> thiz;
        case 1 -> other;
        case 2 -> new LoggingTask(counter.incrementAndGet(), thiz.tryMergeWithFn);
        default -> null;
      };
    });

    SubmissionReceipt lastSubmissionReceipt = queue.addTask(taskFactory.get());
    for (int i = 0; i < 100; i++) {
      SubmissionReceipt submissionReceipt = queue.addTask(taskFactory.get());
      assertThat(lastSubmissionReceipt.isAfter(submissionReceipt)).describedAs("old=" + lastSubmissionReceipt + " new=" + submissionReceipt).isFalse();
      assertEquals(submissionReceipt, queue.getLatestSubmissionReceipt());
      lastSubmissionReceipt = submissionReceipt;
    }
  }

  @Test
  public void testGetLatestSubmissionReceipt() {
    SubmissionReceipt receiptWhenAdded = queue.addTask(new LoggingTask(1, null, null));
    SubmissionReceipt receiptWhenQueried = queue.getLatestSubmissionReceipt();
    assertEquals(receiptWhenAdded, receiptWhenQueried);
    assertFalse(receiptWhenAdded.isAfter(receiptWhenQueried));
    assertFalse(receiptWhenQueried.isAfter(receiptWhenAdded));
  }

  private static void await(@NotNull CyclicBarrier b) {
    try {
      b.await();
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }
}
