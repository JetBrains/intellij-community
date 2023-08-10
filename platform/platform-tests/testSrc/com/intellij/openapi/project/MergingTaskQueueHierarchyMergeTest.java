// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.openapi.project.MergingTaskQueueTest.LoggingTask;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

// parent task may know nothing about children, so the following may happen (just like in case with `equals` with inheritance):
// parent.tryMergeWith(child) != parent.tryMergeWith(child)
// these tests are to verify that MergingQueue does not do unexpected merges
public class MergingTaskQueueHierarchyMergeTest extends BasePlatformTestCase {
  private static class ParentTask extends LoggingTask {
    private static final BiFunction<LoggingTask, LoggingTask, LoggingTask> DEFAULT_TRY_MERGE =
      (thiz, that) -> (that instanceof ParentTask) ? thiz : null;

    ParentTask(int taskId, List<Integer> executeLog) {
      super(taskId, executeLog, null, DEFAULT_TRY_MERGE);
    }

    ParentTask(int taskId, @Nullable List<Integer> performLog, @Nullable List<Integer> disposeLog,
               BiFunction<LoggingTask, LoggingTask, LoggingTask> tryMergeWithFn) {
      super(taskId, performLog, disposeLog, tryMergeWithFn);
    }
  }

  private static class ChildTask extends ParentTask {
    private static final BiFunction<LoggingTask, LoggingTask, LoggingTask> DEFAULT_TRY_MERGE =
      (thiz, that) -> (that instanceof ChildTask) ? thiz : null;

    ChildTask(int taskId, List<Integer> executeLog) {
      super(taskId, executeLog, null, DEFAULT_TRY_MERGE);
    }
  }

  private final MergingTaskQueue<LoggingTask> myQueue = new MergingTaskQueue<>();

  private void runAllTasks() {
    while (true) {
      try (MergingTaskQueue.QueuedTask<?> nextTask = myQueue.extractNextTask()) {
        if (nextTask == null) return;
        nextTask.executeTask();
      }
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    ParentTask parent = new ParentTask(1, null);
    ChildTask child = new ChildTask(2, null);

    assertTrue("Precondition failed", parent.tryMergeWith(child) == parent);
    assertTrue("Precondition failed", parent.tryMergeWith(parent) == parent);
    assertTrue("Precondition failed", child.tryMergeWith(parent) == null);
    assertTrue("Precondition failed", child.tryMergeWith(child) == child);
  }

  public void testTasksFromTheSameClassHierarchyAlwaysMergedFromChildSide() {
    ///////////////////      try order: parent,child      /////////////////////////
    List<Integer> executedTasks = new ArrayList<>();
    ParentTask parent = new ParentTask(1, executedTasks);
    ChildTask child = new ChildTask(2, executedTasks);
    myQueue.addTask(parent);
    myQueue.addTask(child);
    runAllTasks();
    // Parent should not be merged with child (for safety reasons) because they have different types
    assertSameElements(executedTasks, 1, 2);

    ///////////////////  try reverse order: child,parent  /////////////////////////
    executedTasks.clear();
    parent = new ParentTask(1, executedTasks);
    child = new ChildTask(2, executedTasks);
    myQueue.addTask(child);
    myQueue.addTask(parent);
    runAllTasks();
    // Parent should not be merged with child (for safety reasons) because they have different types
    assertSameElements(executedTasks, 1, 2);
  }
}
