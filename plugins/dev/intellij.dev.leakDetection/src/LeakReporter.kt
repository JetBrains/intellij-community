// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.RevealFileAction
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.datatransfer.StringSelection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

private val LOG = logger<ProjectLeakDetector>()

internal const val NOTIFICATION_GROUP_ID = "Project Leak Detection"
private const val YOUTRACK_NEW_ISSUE_URL = "https://youtrack.jetbrains.com/newIssue"

// Action contributed by the performanceTesting plugin; invoked by id so we don't depend on that plugin's module.
private const val CAPTURE_MEMORY_SNAPSHOT_ACTION_ID = "CaptureMemorySnapShot"

/** Surfaces [ProjectLeakDetector] results: a balloon notification (with actions) plus a `LOG.error` so Diogen collects them. */
@ApiStatus.Internal
object LeakReporter {
  fun report(project: Project?, leaks: List<LeakInfo>) {
    val group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID)
    if (leaks.isEmpty()) {
      group.createNotification(DevLeakDetectionBundle.message("notification.title.no.leaks"),
                               DevLeakDetectionBundle.message("notification.content.no.leaks"),
                               NotificationType.INFORMATION)
        .notify(project)
      return
    }

    val report = buildReport(leaks)
    // Logged at error level on purpose: this is how the results are collected from internal builds (Diogen).
    LOG.error(report)

    val type = if (leaks.any { it.kind == LeakKind.PROJECT }) NotificationType.ERROR else NotificationType.WARNING
    val notification = group.createNotification(DevLeakDetectionBundle.message("notification.title.leaks.found"),
                                                DevLeakDetectionBundle.message("notification.content.leaks.found", leaks.size),
                                                type)
      .addAction(NotificationAction.createSimple(DevLeakDetectionBundle.message("notification.action.create.ticket")) {
        createTicket(project, leaks, report)
      })
      .addAction(NotificationAction.createSimple(DevLeakDetectionBundle.message("notification.action.copy.report")) {
        copyToClipboard(report)
      })

    // Offer "Capture memory snapshot" only when the performanceTesting plugin provides that action.
    val captureAction = ActionManager.getInstance().getAction(CAPTURE_MEMORY_SNAPSHOT_ACTION_ID)
    if (captureAction != null) {
      notification.addAction(NotificationAction.create(DevLeakDetectionBundle.message("notification.action.capture.memory.snapshot")) { e, _ ->
        val event = AnActionEvent.createEvent(captureAction, e.dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, e.inputEvent)
        ActionUtil.performAction(captureAction, event)
      })
    }

    notification.notify(project)
  }

  fun buildReport(leaks: List<LeakInfo>): String {
    val sb = StringBuilder()
    sb.append("Project leak detection found ").append(leaks.size).append(" leaked instance(s):\n\n")
    leaks.forEachIndexed { index, leak ->
      sb.append('#').append(index + 1).append(' ').append(leak.kind).append(' ')
        .append(leak.className).append('@').append(Integer.toHexString(leak.identityHashCode)).append('\n')
      sb.append("  instance: ").append(leak.description).append('\n')
      leak.staleForMs?.let { sb.append("  retained for: ").append(it).append(" ms after disposal\n") }
      leak.creationTrace?.let { sb.append("  created at: ").append(it).append('\n') }
      sb.append("  reference path:\n").append(leak.referencePath.trimEnd().prependIndent("    ")).append("\n\n")
    }
    return sb.toString()
  }

  private fun copyToClipboard(report: String) {
    CopyPasteManager.getInstance().setContents(StringSelection(report))
  }

  /**
   * Captures a memory snapshot (off the EDT, under progress), then opens a prefilled YouTrack issue whose description
   * references the snapshot, copies the report to the clipboard, and reveals the snapshot file so it can be attached.
   */
  private fun createTicket(project: Project?, leaks: List<LeakInfo>, report: String) {
    copyToClipboard(report)
    service<LeakDetectionCoroutineScopeHolder>().coroutineScope.launch {
      val snapshot = if (project != null) {
        withBackgroundProgress(project, DevLeakDetectionBundle.message("progress.title.capturing.snapshot"), cancellable = false) {
          captureSnapshot()
        }
      }
      else {
        withModalProgress(ModalTaskOwner.guess(),
                          DevLeakDetectionBundle.message("modal.progress.title.capturing.snapshot"),
                          TaskCancellation.nonCancellable()) {
          captureSnapshot()
        }
      }

      withContext(Dispatchers.EDT) {
        BrowserUtil.browse(createTicketUrl(leaks, snapshot))
        if (snapshot != null) {
          RevealFileAction.openFile(snapshot)
        }
      }
    }
  }

  private fun captureSnapshot(): Path? {
    if (!MemoryDumpHelper.memoryDumpAvailable()) {
      LOG.warn("Memory dump is not available; creating the leak ticket without a snapshot")
      return null
    }
    return try {
      val folder = System.getProperty("snapshots.path", SystemProperties.getUserHome())
      val file = Path.of(folder, "projectLeak-${System.currentTimeMillis()}.hprof")
      MemoryDumpHelper.captureMemoryDump(file.toString())
      file
    }
    catch (e: Exception) {
      LOG.warn("Failed to capture memory snapshot for the leak ticket", e)
      null
    }
  }

  private fun createTicketUrl(leaks: List<LeakInfo>, snapshot: Path?): String {
    val summary = "Project or Editor leak detected"
    val description = buildString {
      append("Detected ").append(leaks.size).append(" leaked instance(s) in a running IDE.\n")
      append("The full report (reference paths) has been copied to the clipboard and is also in the IDE log (idea.log).\n\n")
      leaks.take(5).forEach { append("- ").append(it.kind).append(' ').append(it.className).append('\n') }
      append('\n')
      if (snapshot != null) {
        append("Memory snapshot (please attach this file)").append('\n')
      }
      else {
        append("Memory snapshot: capture was unavailable.\n")
      }
    }
    return "$YOUTRACK_NEW_ISSUE_URL?project=IJPL&summary=${encode(summary)}&description=${encode(description)}"
  }

  private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
