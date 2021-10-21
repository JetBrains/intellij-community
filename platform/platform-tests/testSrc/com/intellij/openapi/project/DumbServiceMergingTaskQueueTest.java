// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.idea.TestFor;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbServiceMergingTaskQueue.QueuedDumbModeTask;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.LeakHunter;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DumbServiceMergingTaskQueueTest extends BasePlatformTestCase {
  private final DumbServiceMergingTaskQueue myQueue = new DumbServiceMergingTaskQueue();

  private void runAllTasks() {
    while (true) {
      try (@Nullable QueuedDumbModeTask nextTask = myQueue.extractNextTask()) {
        if (nextTask == null) return;
        nextTask.executeTask();
      }
    }
  }

  static abstract class MyDumbModeTask extends DumbModeTask {
    private final @NotNull Object myEquivalenceObject;

    MyDumbModeTask(@NotNull Object object) { myEquivalenceObject = object; }

    @Override
    public @Nullable DumbModeTask tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
      if (taskFromQueue.getClass().equals(getClass()) && ((MyDumbModeTask)taskFromQueue).myEquivalenceObject.equals(myEquivalenceObject)) {
        return this;
      }
      return null;
    }
  }

  public void testEquivalentTasksAreMerged() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      int taskId = i;
      myQueue.addTask(new MyDumbModeTask("child") {
        @Override
        public void performInDumbMode(@NotNull ProgressIndicator indicator) {
          childLog.add(taskId);
        }

        @Override
        public void dispose() {
          disposeLog.add(taskId);
        }
      });
    }

    runAllTasks();

    Assert.assertEquals("Only one child task should run, but were: " + childLog, 1, childLog.size());
    Assert.assertEquals("All tasks must be disposed, but were: " + disposeLog, 100, disposeLog.size());
  }

  public void testTasksWithOverwrittenTryMergeAreMerged() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      int taskId = i;
      myQueue.addTask(new DumbModeTask() {
        @Override
        public void performInDumbMode(@NotNull ProgressIndicator indicator) {
          childLog.add(taskId);
        }

        @Override
        public DumbModeTask tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
          return this;//always merges
        }

        @Override
        public void dispose() {
          disposeLog.add(taskId);
        }
      });
    }

    runAllTasks();

    Assert.assertEquals("Only one child task should run, but were: " + childLog, 1, childLog.size());
    Assert.assertEquals("All tasks must be disposed, but were: " + disposeLog, 100, disposeLog.size());
  }

  public void testDifferentClassesWithSameEquivalentAreNotMerged() {
    List<Integer> childLog = new ArrayList<>();
    final String commonEquivalence = "child";
    DumbModeTask taskA = new MyDumbModeTask(commonEquivalence) {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        childLog.add(1);
      }
    };

    DumbModeTask taskB = new MyDumbModeTask(commonEquivalence) {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        childLog.add(-1);
      }
    };

    //both taskA and taskB submits the same equality object, it must run both
    myQueue.addTask(taskA);
    myQueue.addTask(taskB);
    runAllTasks();
    Assert.assertEquals("All tasks should run, but were: " + childLog, 2, childLog.size());
  }

  public void testNonEquivalentTasksAreNotMerged() {
    List<Integer> childLog = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      int taskId = i;
      myQueue.addTask(new MyDumbModeTask("child" + i) {
        @Override
        public void performInDumbMode(@NotNull ProgressIndicator indicator) {
          childLog.add(taskId);
        }
      });
    }
    runAllTasks();
    Assert.assertEquals("Every child task are not unique, all must be executed: " + childLog, 100, childLog.size());
  }

  public void testTasksAreNotMergedByDefault() {
    List<Integer> childLog = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      int taskId = i;
      myQueue.addTask(new DumbModeTask() {
        @Override
        public void performInDumbMode(@NotNull ProgressIndicator indicator) {
          childLog.add(taskId);
        }
      });
    }
    runAllTasks();
    Assert.assertEquals("Every child task are not unique, all must be executed: " + childLog, 100, childLog.size());
  }


  public void testNewTaskIsRunWhenMerged() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();

    for (int i = 0; i < 3; i++) {
      int taskId = i;
      myQueue.addTask(new MyDumbModeTask("child") {
        @Override
        public void performInDumbMode(@NotNull ProgressIndicator indicator) {
          childLog.add(taskId);
        }

        @Override
        public void dispose() {
          disposeLog.add(taskId);
        }
      });
    }

    runAllTasks();
    Assert.assertEquals("The last child task should run, but were: " + childLog, Collections.singletonList(2), childLog);
    Assert.assertEquals("All tasks must be disposed, but were: " + disposeLog, 3, disposeLog.size());
  }

  public void testMergedTaskIsRunWhenMerged() {
    List<String> disposeLog = new ArrayList<>();
    List<String> childLog = new ArrayList<>();

    class DumbModeTaskWithId extends DumbModeTask {
      protected final String taskId;

      DumbModeTaskWithId(String taskId) {
        this.taskId = taskId;
      }

      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        childLog.add(taskId);
      }

      @Override
      public void dispose() {
        disposeLog.add(taskId);
      }

      @Nullable
      @Override
      public DumbModeTask tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
        if (!(taskFromQueue instanceof DumbModeTaskWithId)) return null;
        String newId = ((DumbModeTaskWithId)taskFromQueue).taskId + " " + taskId;
        return new DumbModeTaskWithId(newId);
      }
    }
    for (int i = 0; i < 3; i++) {
      myQueue.addTask(new DumbModeTaskWithId(String.valueOf(i)));
    }

    runAllTasks();
    Assert.assertEquals("The last child task should run, but were: " + childLog, Collections.singletonList("0 1 2"), childLog);
    Assert.assertEquals("All tasks must be disposed, but were: " + disposeLog, 3, disposeLog.size());
  }

  public void testCancelledTask() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();

    DumbModeTask task = new DumbModeTask() {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        childLog.add(1);
      }

      @Override
      public void dispose() {
        disposeLog.add(1);
      }
    };

    myQueue.addTask(task);
    myQueue.cancelTask(task);

    runAllTasks();
    Assert.assertEquals("Cancelled task must not run " + childLog, Collections.emptyList(), childLog);
    Assert.assertEquals("Cancelled task must dispose " + disposeLog, Collections.singletonList(1), disposeLog);
  }

  public void testMergedTaskShouldDispose() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();

    for (int i = 0; i < 3; i++) {
      int taskId = i;
      myQueue.addTask(new MyDumbModeTask("child") {
        @Override
        public void performInDumbMode(@NotNull ProgressIndicator indicator) {
          childLog.add(taskId);
        }

        @Override
        public void dispose() {
          disposeLog.add(taskId);
        }
      });
    }

    Assert.assertEquals("No task should run by now " + childLog, Collections.emptyList(), childLog);
    Assert.assertEquals("older task must be disposed" + disposeLog, Arrays.asList(0,1), disposeLog);

    runAllTasks();

    Assert.assertEquals("The last task should only run " + childLog, Collections.singletonList(2), childLog);
    Assert.assertEquals("older task must be disposed" + disposeLog, Arrays.asList(0,1,2), disposeLog);
  }

  public void testTasksAreDisposed() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();

    DumbModeTask task = new MyDumbModeTask("child") {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        childLog.add(1);
      }

      @Override
      public void dispose() {
        disposeLog.add(1);
      }
    };

    myQueue.addTask(task);
    myQueue.disposePendingTasks();

    Assert.assertEquals("The last task should only run " + childLog, Collections.emptyList(), childLog);
    Assert.assertEquals("older task must be disposed" + disposeLog, Arrays.asList(1), disposeLog);

    runAllTasks();
    Assert.assertEquals("The last task should only run " + childLog, Collections.emptyList(), childLog);
    Assert.assertEquals("older task must be disposed" + disposeLog, Arrays.asList(1), disposeLog);
  }

  public void testMergedTasksShouldNotReserveEarlierSlots() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();

    for (int i = 1; i <= 3; i++) {
      int taskId = i;
      myQueue.addTask(new MyDumbModeTask("child") {
        @Override
        public void performInDumbMode(@NotNull ProgressIndicator indicator) {
          childLog.add(taskId);
        }

        @Override
        public void dispose() {
          disposeLog.add(taskId);
        }
      });

      myQueue.addTask(new MyDumbModeTask("boss-" + i) {
        @Override
        public void performInDumbMode(@NotNull ProgressIndicator indicator) {
          childLog.add(-taskId);
        }

        @Override
        public void dispose() {
          disposeLog.add(-taskId);
        }
      });
    }

    runAllTasks();
    Assert.assertEquals("The last task should only run " + childLog, Arrays.asList(-1, -2, 3, -3), childLog);
    Assert.assertEquals("older task must be disposed " + disposeLog, Arrays.asList(1, 2, -1, -2, 3, -3), disposeLog);
  }

  @TestFor(issues = "IDEA-241378")
  public void testRunningTaskShouldNotBeDisposed() {
    AtomicReference<Boolean> isDisposed = new AtomicReference<>();
    myQueue.addTask(new MyDumbModeTask("any") {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) { }

      @Override
      public void dispose() {
        isDisposed.set(true);
      }
    });

    QueuedDumbModeTask task = myQueue.extractNextTask();
    myQueue.disposePendingTasks();

    Assert.assertNull(isDisposed.get());
    try {
      task.executeTask();
      Assert.fail();
    } catch (ProcessCanceledException ignore) {
      //OK
    } finally {
      task.close();
    }
    Assert.assertEquals(Boolean.TRUE, isDisposed.get());
  }

  @TestFor(issues = "IDEA-241378")
  public void testRunningTaskIndicatorShouldBeCancelledOnDisposeRunningTasks() {
    CyclicBarrier b = new CyclicBarrier(2);
    AtomicReference<Boolean> isRun = new AtomicReference<>();
    myQueue.addTask(new MyDumbModeTask("any") {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        for (int i = 0; i < 2; i++) {
          await(b);
        }
        isRun.set(indicator.isCanceled());
      }
    });

    Thread th = new Thread(() -> {
      try (QueuedDumbModeTask nextTask = myQueue.extractNextTask()) {
        nextTask.executeTask();
      } catch (Exception e) {
        LOG.error(e);
      }
    }, getClass().getName() + "-thread");
    th.setDaemon(true);
    th.start();

    await(b);
    //now the task is in the middle
    myQueue.disposePendingTasks();
    await(b);

    //not it should complete
    try {
      th.join(5_000);
    }
    catch (InterruptedException e) {
      th.interrupt();
      Assert.fail();
    }

    Assert.assertEquals(Boolean.TRUE, isRun.get());
  }

  public void testNoLeaks() {
    for(int i = 0; i < 1000; i++) {
      DumbModeTask task = new MyDumbModeTask("q-" + i) {
        @Override
        public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        }
      };

      myQueue.addTask(task);
      if (i % 3 == 0) {
        Disposer.dispose(task);
      }

      if (i % 3 == 1) {
        myQueue.cancelTask(task);
      }
    }

    runAllTasks();

    LeakHunter.checkLeak(myQueue, ProgressIndicator.class);
    LeakHunter.checkLeak(myQueue, DumbModeTask.class);
  }

  public void testNoDisposeLeaksOnClose() {
    final AtomicBoolean myDisposeFlag = new AtomicBoolean(false);
    DumbModeTask task = new MyDumbModeTask(this) {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      }

      @Override
      public void dispose() {
        myDisposeFlag.set(true);
      }
    };

    myQueue.addTask(task);
    myQueue.disposePendingTasks();

    Assert.assertTrue(Disposer.isDisposed(task));
    Assert.assertTrue(myDisposeFlag.get());
  }

  public void testNoDisposeLeaksOnClose2() {
    final AtomicBoolean myDisposeFlag = new AtomicBoolean(false);
    DumbModeTask task = new MyDumbModeTask(this) {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      }

      @Override
      public void dispose() {
        myDisposeFlag.set(true);
      }
    };

    myQueue.addTask(task);
    myQueue.cancelAllTasks();
    myQueue.disposePendingTasks();

    Assert.assertTrue(Disposer.isDisposed(task));
    Assert.assertTrue(myDisposeFlag.get());
  }

  public void testNoDisposeLeaksOnClose3() {
    final AtomicBoolean myDisposeFlag = new AtomicBoolean(false);
    DumbModeTask task = new MyDumbModeTask(this) {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
      }

      @Override
      public void dispose() {
        myDisposeFlag.set(true);
      }
    };

    myQueue.addTask(task);
    myQueue.cancelTask(task);
    myQueue.disposePendingTasks();

    Assert.assertTrue(Disposer.isDisposed(task));
    Assert.assertTrue(myDisposeFlag.get());
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
