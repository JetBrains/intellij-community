// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.configuration

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManagerListener
import com.intellij.openapi.progress.Task
import com.intellij.openapi.util.text.Formats
import com.intellij.openapi.wm.ex.ProgressIndicatorEx
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class HeadlessProgressListener : ProgressManagerListener {

  private val taskDurationMap = ConcurrentHashMap<Int, Long>()

  override fun beforeTaskStart(task: Task, indicator: ProgressIndicator) {
    if (indicator !is ProgressIndicatorEx) {
      return
    }
    HeadlessLogging.logMessage("[IDE]: Task '${task.title}' started")
    taskDurationMap[System.identityHashCode(task)] = System.currentTimeMillis()
    indicator.addStateDelegate(ChannelingProgressIndicator("IDE"))
    super.beforeTaskStart(task, indicator)
  }

  override fun afterTaskFinished(task: Task) {
    val currentTime = System.currentTimeMillis()
    val startTime = taskDurationMap.remove(System.identityHashCode(task))
    val elapsedTimeSuffix = if (startTime == null) "" else " in ${Formats.formatDuration(currentTime - startTime)}"
    HeadlessLogging.logMessage("[IDE]: Task '${task.title}' ended" + elapsedTimeSuffix)
  }
}