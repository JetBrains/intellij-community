// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project;

import com.intellij.internal.statistic.StructuredIdeActivity;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.intellij.openapi.project.DumbModeStatisticsCollector.DUMB_MODE_STAGE_ACTIVITY;
import static com.intellij.openapi.project.DumbModeStatisticsCollector.FINISH_TYPE;

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
  protected QueuedDumbModeTask wrapTask(DumbModeTask task, ProgressIndicatorBase indicator) {
    return new QueuedDumbModeTask(task, indicator);
  }

  final class QueuedDumbModeTask extends MergingTaskQueue.QueuedTask<DumbModeTask> {

    QueuedDumbModeTask(@NotNull DumbModeTask task,
                       @NotNull ProgressIndicatorEx progress) {
      super(task, progress);
    }

    @Override
    StructuredIdeActivity registerStageStarted(@NotNull StructuredIdeActivity activity, @NotNull Project project) {
      return DUMB_MODE_STAGE_ACTIVITY.startedWithParent(project, activity, () -> Collections.singletonList(
        DumbModeStatisticsCollector.STAGE_CLASS.with(getTask().getClass())));
    }

    @Override
    void registerStageFinished(@NotNull StructuredIdeActivity parentActivity,
                               @Nullable StructuredIdeActivity childActivity,
                               @NotNull DumbModeStatisticsCollector.IndexingFinishType finishType) {
      if (childActivity != null) {
        childActivity.finished(() -> {
          return Arrays.asList(FINISH_TYPE.with(finishType), DumbModeStatisticsCollector.STAGE_CLASS.with(getTask().getClass()));
        });
      }
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
