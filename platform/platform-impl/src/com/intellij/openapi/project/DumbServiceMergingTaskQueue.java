// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.concurrency.ChildContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DumbServiceMergingTaskQueue extends MergingTaskQueue<DumbModeTask> {

  private static final ExtensionPointName<DumbServiceInitializationCondition> DUMB_SERVICE_INITIALIZATION_CONDITION_EXTENSION_POINT_NAME =
    ExtensionPointName.create("com.intellij.dumbServiceInitializationCondition");

  private final AtomicBoolean myFirstExecution = new AtomicBoolean(true);

  private void waitRequiredTasksToStartIndexing() {
    if (myFirstExecution.compareAndSet(true, false)) {
      var logger = Logger.getInstance(DumbServiceMergingTaskQueue.class);
      logger.info("Initializing DumbServiceMergingTaskQueue...");
      for (DumbServiceInitializationCondition condition : DUMB_SERVICE_INITIALIZATION_CONDITION_EXTENSION_POINT_NAME.getExtensionList()) {
        logger.info("Running initialization condition: " + condition);
        condition.waitForInitialization();
        logger.info("Finished: " + condition);
      }
    }
  }

  @Override
  public @Nullable QueuedDumbModeTask extractNextTask() {
    return (QueuedDumbModeTask)super.extractNextTask();
  }

  @Override
  protected QueuedDumbModeTask wrapTask(DumbModeTask task, ProgressIndicatorBase indicator, @Nullable ChildContext childContext) {
    return new QueuedDumbModeTask(task, indicator, childContext);
  }

  final class QueuedDumbModeTask extends MergingTaskQueue.QueuedTask<DumbModeTask> {

    QueuedDumbModeTask(@NotNull DumbModeTask task,
                       @NotNull ProgressIndicatorEx progress,
                       @Nullable ChildContext childContext) {
      super(task, progress, childContext);
    }

    @Override
    void registerStageStarted(@NotNull StructuredIdeActivity activity) {
      activity.stageStarted(DumbModeStatisticsCollector.DUMB_MODE_STAGE,
                            () -> Collections.singletonList(DumbModeStatisticsCollector.STAGE_CLASS.with(getTask().getClass())));
    }

    @Override
    public void beforeTask() {
      waitRequiredTasksToStartIndexing();
    }

    @Override
    String getInfoString() {
      return "(dumb mode task) " + super.getInfoString();
    }
  }
}
