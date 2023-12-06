// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.project;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.Ref;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DumbServiceSyncTaskQueueTest extends BasePlatformTestCase {
  private DumbServiceSyncTaskQueue myService;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myService = new DumbServiceSyncTaskQueue(getProject(), new DumbServiceMergingTaskQueue());
  }

  @NotNull
  private DumbServiceSyncTaskQueue service() {
    return myService;
  }

  public void testCanceledTasksDoesNotTerminateFollowingTasks() {
    final AtomicBoolean secondTaskCompleted = new AtomicBoolean(false);

    // Run two tasks one after another. Then terminate the first (parent) task and check that the second (child) task complete successfully
    service().runTaskSynchronously(new DumbModeTask() {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        service().runTaskSynchronously(new DumbModeTask() {
          @Override
          public void performInDumbMode(@NotNull ProgressIndicator indicator) {
            secondTaskCompleted.set(true);
          }
        });

        // cancel parent task now. The child task should complete successfully
        throw new ProcessCanceledException();
      }
    });
    Assert.assertTrue("Cancellation of the first task should not terminate execution of the second task", secondTaskCompleted.get());
  }

  public void testRecursionIsBlocked() {
    final Ref<Boolean> myInnerRunning = new Ref<>(null);
    service().runTaskSynchronously(new DumbModeTask() {
      boolean myIsRunning = false;

      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        myIsRunning = true;
        try {
          service().runTaskSynchronously(new DumbModeTask() {
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
    service().runTaskSynchronously(new DumbModeTaskWithEquivalentObject("parent") {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        for (int i = 0; i < 100; i++) {
          int taskId = i;
          service().runTaskSynchronously(new DumbModeTaskWithEquivalentObject("child") {
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
    service().runTaskSynchronously(new DumbModeTaskWithEquivalentObject("parent") {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        final String commonEquivalence = "child";
        DumbModeTask taskA = new DumbModeTaskWithEquivalentObject(commonEquivalence) {
          @Override
          public void performInDumbMode(@NotNull ProgressIndicator indicator) {
            childLog.add(1);
          }
        };

        DumbModeTask taskB = new DumbModeTaskWithEquivalentObject(commonEquivalence) {
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
    service().runTaskSynchronously(new DumbModeTask() {
      @Override
      public void performInDumbMode(@NotNull ProgressIndicator indicator) {
        for (int i = 0; i < 100; i++) {
          int taskId = i;
          service().runTaskSynchronously(new DumbModeTask() {
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

  static abstract class DumbModeTaskWithEquivalentObject extends DumbModeTask {
    private final @NotNull Object myEquivalenceObject;

    DumbModeTaskWithEquivalentObject(@NotNull Object object) { myEquivalenceObject = object; }

    @Override
    public @Nullable DumbModeTask tryMergeWith(@NotNull DumbModeTask taskFromQueue) {
      if (taskFromQueue.getClass().equals(getClass()) && ((DumbModeTaskWithEquivalentObject)taskFromQueue).myEquivalenceObject.equals(myEquivalenceObject)) {
        return this;
      }
      return null;
    }
  }
}
