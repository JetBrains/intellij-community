// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

public class DumbServiceSyncTaskQueueTest extends BasePlatformTestCase {
  private final DumbServiceMergingTaskQueue myQueue = new DumbServiceMergingTaskQueue();
  private final DumbServiceSyncTaskQueue myService = new DumbServiceSyncTaskQueue(myQueue);

  @NotNull
  private DumbServiceSyncTaskQueue service() {
    return myService;
  }

  public void testRecursionIsBlocked() {
    final Ref<Boolean> myInnerRunning = new Ref<>(null);
    service().runTaskSynchronously(new DumbServiceMergingTaskQueueTest.MyDumbModeTask("parent") {
      boolean myIsRunning = false;

      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        myIsRunning = true;
        try {
          service().runTaskSynchronously(new DumbServiceMergingTaskQueueTest.MyDumbModeTask("child") {
            @Override
            public void performInDumbMode(@NotNull ProgressIndicator indicator) {
              //the task execution must be postponed, so the captured closure will evaluate to `false`
              myInnerRunning.set(myIsRunning);
            }
          });
        }
        finally {
          myIsRunning = false;
        }
      }
    });
    Assert.assertFalse("One task should complete before the nested one is running", myInnerRunning.get());
  }

  public void testEquivalentTasksAreMerged() {
    List<Integer> disposeLog = new ArrayList<>();
    List<Integer> childLog = new ArrayList<>();
    service().runTaskSynchronously(new DumbServiceMergingTaskQueueTest.MyDumbModeTask("parent") {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        for (int i = 0; i < 100; i++) {
          int taskId = i;
          service().runTaskSynchronously(new DumbServiceMergingTaskQueueTest.MyDumbModeTask("child") {
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
      }
    });

    Assert.assertEquals("Only one child task should run, but were: " + childLog, 1, childLog.size());
    Assert.assertEquals("All tasks must be disposed, but were: " + disposeLog, 100, disposeLog.size());
  }

  public void testDifferentClassesWithSameEquivalentAreNotMerged() {
    List<Integer> childLog = new ArrayList<>();
    service().runTaskSynchronously(new DumbServiceMergingTaskQueueTest.MyDumbModeTask("parent") {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        final String commonEquivalence = "child";
        DumbModeTask taskA = new DumbServiceMergingTaskQueueTest.MyDumbModeTask(commonEquivalence) {
          @Override
          public void performInDumbMode(@NotNull ProgressIndicator indicator) {
            childLog.add(1);
          }
        };

        DumbModeTask taskB = new DumbServiceMergingTaskQueueTest.MyDumbModeTask(commonEquivalence) {
          @Override
          public void performInDumbMode(@NotNull ProgressIndicator indicator) {
            childLog.add(-1);
          }
        };

        //both taskA and taskB submits the same equality object, it must run both
        service().runTaskSynchronously(taskA);
        service().runTaskSynchronously(taskB);
      }
    });

    Assert.assertEquals("All tasks should run, but were: " + childLog, 2, childLog.size());
  }

  public void testNonEquivalentTasksAreNotMerged() {
    List<Integer> childLog = new ArrayList<>();
    service().runTaskSynchronously(new DumbServiceMergingTaskQueueTest.MyDumbModeTask("parent") {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        for (int i = 0; i < 100; i++) {
          int taskId = i;
          service().runTaskSynchronously(new DumbServiceMergingTaskQueueTest.MyDumbModeTask("child" + i) {
            @Override
            public void performInDumbMode(@NotNull ProgressIndicator indicator) {
              childLog.add(taskId);
            }
          });
        }
      }
    });

    Assert.assertEquals("Every child task are not unique, all must be executed: " + childLog, 100, childLog.size());
  }
}
