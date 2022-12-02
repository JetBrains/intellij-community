// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.DumbServiceMergingTaskQueue.QueuedDumbModeTask;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.util.ArrayList;
import java.util.List;

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

  public void testDumbModeTasksAreNotMergedByDefault() {
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

}
