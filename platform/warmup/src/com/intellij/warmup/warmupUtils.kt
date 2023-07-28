// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.warmup

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.warmup.util.ConsoleLog
import com.intellij.warmup.util.runTaskAndLogTime
import com.intellij.warmup.util.yieldThroughInvokeLater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.time.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import kotlin.system.exitProcess

suspend fun waitIndexInitialization() {
  val fileBasedIndex = serviceAsync<FileBasedIndex>() as FileBasedIndexEx
  fileBasedIndex.loadIndexes()
  fileBasedIndex.waitUntilIndicesAreInitialized()
}

suspend fun waitUntilProgressTasksAreFinishedOrFail() {
  try {
    waitUntilProgressTasksAreFinished()
  }
  catch (e: IllegalStateException) {
    ConsoleLog.info(e.message ?: e.toString())
    exitProcess(2)
  }
}

private suspend fun waitUntilProgressTasksAreFinished() {
  runTaskAndLogTime("Awaiting for progress tasks") {
    while (true) {
      withContext(Dispatchers.EDT) {
        blockingContext {
          MergingUpdateQueue.flushAllQueues()
        }
      }
      yieldThroughInvokeLater()
      if (CoreProgressManager.getCurrentIndicators().isEmpty()) {
        return@runTaskAndLogTime
      }
      waitCurrentProgressIndicators()
    }
  }
}

private suspend fun waitCurrentProgressIndicators() {
  val timeout = System.getProperty("ide.progress.tasks.awaiting.timeout.min", "60").toLongOrNull() ?: 60
  val startTime = System.currentTimeMillis()
  while (CoreProgressManager.getCurrentIndicators().isNotEmpty()) {
    if (System.currentTimeMillis() - startTime > Duration.ofMinutes(timeout).toMillis()) {
      val timeoutMessage = StringBuilder("Progress tasks awaiting timeout.\n")
      timeoutMessage.appendLine("Not finished tasks:")
      for (indicator in CoreProgressManager.getCurrentIndicators()) {
        timeoutMessage.appendLine("  - ${indicator.text}")
      }
      error(timeoutMessage)
    }
    delay(Duration.ofMillis(1000))
  }
}