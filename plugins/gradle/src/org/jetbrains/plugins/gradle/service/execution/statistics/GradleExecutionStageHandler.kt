// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution.statistics

interface GradleExecutionStageHandler {

  fun onGradleExecutionCompleted(duration: Long)

  fun onGradleBuildLoaded(duration: Long)

  fun onGradleSettingsEvaluated(duration: Long)

  fun onGradleProjectLoaded(duration: Long)

  fun onContainerCallbackExecuted(duration: Long)

  fun onTaskGraphCalculated(duration: Long)

  fun onTaskExecuted(task: AggregatedTaskReport)

  fun onTaskGraphExecuted(report: TaskGraphExecutionReport)

}