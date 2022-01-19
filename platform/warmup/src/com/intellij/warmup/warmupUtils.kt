package com.intellij.indexing.shared.ultimate.project

import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.util.indexing.FileBasedIndex
import com.intellij.util.indexing.FileBasedIndexEx
import com.intellij.warmup.util.runTaskAndLogTime
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import java.time.Duration
import kotlin.system.exitProcess

fun waitIndexInitialization() = (FileBasedIndex.getInstance() as FileBasedIndexEx).waitUntilIndicesAreInitialized()

fun waitUntilProgressTasksAreFinishedOrFail() {
  try {
    waitUntilProgressTasksAreFinished()
  }
  catch (e: IllegalStateException) {
    println(e.message)
    exitProcess(2)
  }
}

private fun waitUntilProgressTasksAreFinished() = runBlocking {
  runTaskAndLogTime("Awaiting for progress tasks") {
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
      delay(Duration.ofMillis(100))
    }
  }
}