// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project

import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsContexts.ProgressText
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.SystemProperties
import kotlinx.collections.immutable.PersistentList
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface UnindexedFilesScannerExecutor {
  val isRunning: StateFlow<Boolean>
  val taskQueue: MergingTaskQueue<FilesScanningTask>

  fun suspendScanningAndIndexingThenRun(activityName: @ProgressText String, runnable: Runnable)
  fun suspendQueue()
  fun resumeQueue(onFinish: () -> Unit)
  fun cancelAllTasksAndWait()
  fun getPauseReason(): StateFlow<PersistentList<String>>

  fun submitTask(task: FilesScanningTask)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): UnindexedFilesScannerExecutor = project.service<UnindexedFilesScannerExecutor>()

    @JvmStatic
    fun shouldScanInSmartMode(): Boolean {
      val registryValue = Registry.get("scanning.in.smart.mode")
      return if (registryValue.isChangedFromDefault) {
        registryValue.asBoolean()
      }
      else {
        SystemProperties.getBooleanProperty("scanning.in.smart.mode", true)
      }
    }
  }
}