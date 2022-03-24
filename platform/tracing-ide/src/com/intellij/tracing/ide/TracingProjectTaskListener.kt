// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.tracing.ide

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.task.ProjectTaskContext
import com.intellij.task.ProjectTaskListener
import com.intellij.task.ProjectTaskManager
import com.intellij.tracing.Tracer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.io.exists
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText
import kotlin.jvm.Throws

internal class TracingProjectTaskListener : ProjectTaskListener {
  companion object {
    private val log = logger<TracingProjectTaskListener>()
  }

  @Volatile
  private var span: Tracer.Span? = null

  override fun started(context: ProjectTaskContext) {
    val tracingService = TracingService.getInstance()
    if (!tracingService.isTracingEnabled()) return
    try {
      val filePath = TracingService.createPath(TracingService.TraceKind.Ide)
      tracingService.registerIdeTrace(filePath)
      tracingService.bindJpsTraceIfExistsToCurrentSession()
      Tracer.runTracer(0, filePath, 1) { exception ->
        handleException(tracingService, exception)
      }
      span = Tracer.start("Build")
    } catch (e: IOException) {
      handleException(tracingService, e)
    }
  }

  override fun finished(result: ProjectTaskManager.Result) {
    val tracingService = TracingService.getInstance()
    if (!tracingService.isTracingEnabled()) return
    span?.complete()
    Tracer.finishTracer { exception ->
      handleException(tracingService, exception)
    }
    val filesToMerge = tracingService.drainFilesToMerge()
    AppExecutorUtil.getAppExecutorService().execute {
      try {
        val mergedText = mergeFiles(filesToMerge)
        val mergedFilePath = TracingService.createPath(TracingService.TraceKind.Merged)
        Files.createDirectories(mergedFilePath.parent)
        mergedFilePath.writeText(mergedText)
        showNotificationNotification(mergedFilePath.parent)
      }
      catch (e: IOException) {
        handleException(tracingService, e)
      }
    }
  }

  private fun handleException(tracingService: TracingService, e: Exception) {
    tracingService.clearPathsToMerge()
    log.warn(e)
  }

  private fun showNotificationNotification(mergedFile: Path) {
    val notification = Notification("BuildTracing", TracingBundle.message("notification.content.tracing.file.was.created"), NotificationType.INFORMATION)
    notification.addAction(object : AnAction(TracingBundle.message("action.open.trace.directory.in.file.manager.text")) {
      override fun actionPerformed(e: AnActionEvent) {
        PsiNavigationSupport.getInstance().openDirectoryInSystemFileManager(mergedFile.parent.toFile())
      }
    })
    notification.notify(null)
  }

  @Throws(IOException::class)
  fun mergeFiles(files: List<Path>) : String {
    return buildString {
      appendLine("[\n")
      for (filePath in files) {
        if (filePath.exists()) {
          val entries = readEntries(filePath)
          for (entry in entries) {
            appendLine(entry)
          }
        }
      }
      appendLine("]\n")
    }
  }

  private fun readEntries(trace: Path) = trace.toFile().bufferedReader().lineSequence().drop(1).toList().dropLast(1)
}