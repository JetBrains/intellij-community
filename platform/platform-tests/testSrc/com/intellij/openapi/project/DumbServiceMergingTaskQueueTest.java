// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DumbServiceMergingTaskQueueTest extends BasePlatformTestCase {
  private final DumbServiceMergingTaskQueue myQueue = new DumbServiceMergingTaskQueue();

  private void runAllTasks() {
    while(true) {
      DumbServiceMergingTaskQueue.@Nullable QueuedDumbModeTask nextTask = myQueue.extractNextTask();
      if (nextTask == null) return;
      nextTask.executeTask();
    }
  }

  public void testEquivalentTasksAreMerged() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      int taskId = i;
      myQueue.addTask(new DumbModeTask("child") {
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

  public void testDifferentClassesWithSameEquivalentAreNotMerged() {
    List<Integer> childLog = new ArrayList<>();
    final String commonEquivalence = "child";
    DumbModeTask taskA = new DumbModeTask(commonEquivalence) {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        childLog.add(1);
      }
    };

    DumbModeTask taskB = new DumbModeTask(commonEquivalence) {
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
      myQueue.addTask(new DumbModeTask("child" + i) {
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
      myQueue.addTask(new DumbModeTask("child") {
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

  public void testCancelledTask() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();

    DumbModeTask task = new DumbModeTask("child") {
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
      myQueue.addTask(new DumbModeTask("child") {
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

    DumbModeTask task = new DumbModeTask("child") {
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
      myQueue.addTask(new DumbModeTask("child") {
        @Override
        public void performInDumbMode(@NotNull ProgressIndicator indicator) {
          childLog.add(taskId);
        }

        @Override
        public void dispose() {
          disposeLog.add(taskId);
        }
      });

      myQueue.addTask(new DumbModeTask("boss-" + i) {
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

}
