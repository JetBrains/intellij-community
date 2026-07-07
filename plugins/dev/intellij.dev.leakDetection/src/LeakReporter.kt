// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dev.leakDetection

import com.intellij.ide.BrowserUtil
import com.intellij.ide.actions.RevealFileAction
import com.intellij.ide.logsUploader.LogUploader
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.util.MemoryDumpHelper
import com.intellij.util.SystemProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.datatransfer.StringSelection
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.nio.file.Path

private val LOG = logger<ProjectLeakDetector>()

internal const val NOTIFICATION_GROUP_ID = "Project Leak Detection"
private const val YOUTRACK_NEW_ISSUE_URL = "https://youtrack.jetbrains.com/newIssue"
private const val YOUTRACK_VISIBILITY_GROUP = "jetbrains-team"

// Snapshot upload target (same service used by Report Feedback / Collect Logs via LogUploader).
private const val UPLOADS_URL = "https://uploads.jetbrains.com"

// Action contributed by the performanceTesting plugin; invoked by id so we don't depend on that plugin's module.
private const val CAPTURE_MEMORY_SNAPSHOT_ACTION_ID = "CaptureMemorySnapShot"

// Volatile parts of a reference-path rendering ignored when deciding whether two paths are the same retention chain
private val IDENTITY_HASH_REGEX = Regex("@[0-9a-fA-F]+")
private val ARRAY_INDEX_REGEX = Regex("""\[\d+]""")
private val IDE_FRAME_REGEX = Regex("""IdeFrameImpl\[frame[0-9]""")

/** Surfaces [ProjectLeakDetector] results: a balloon notification (with actions) plus a `LOG.error` so Diogen collects them. */
@ApiStatus.Internal
class LeakReporter(private val coroutineScope: CoroutineScope) {
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
    // Squash leaks that share the same structural reference path (ignoring identity hashes and array indices), so a
    // report with many near-identical retention chains stays readable: each distinct path is printed only once.
    val groups = LinkedHashMap<String, MutableList<LeakInfo>>()
    for (leak in leaks) {
      val key = "${leak.kind}\n${leak.className}\n${pathSignature(leak.referencePath)}"
      groups.getOrPut(key) { ArrayList() }.add(leak)
    }

    val sb = StringBuilder()
    sb.append("Project leak detection found ").append(leaks.size).append(" leaked instance(s)")
    if (groups.size < leaks.size) {
      sb.append(" in ").append(groups.size).append(" distinct retention path(s)")
    }
    sb.append(":\n\n")

    groups.values.forEachIndexed { index, group ->
      val representative = group.first()
      sb.append('#').append(index + 1).append(' ').append(representative.kind).append(' ').append(representative.className)
      if (group.size == 1) {
        sb.append('@').append(Integer.toHexString(representative.identityHashCode)).append('\n')
        sb.append("  instance: ").append(representative.description).append('\n')
        representative.staleForMs?.let { sb.append("  retained for: ").append(it).append(" ms after disposal\n") }
        representative.creationTrace?.let { sb.append("  created at: ").append(it).append('\n') }
      }
      else {
        sb.append(" — ").append(group.size).append(" instances sharing this path\n")
        sb.append("  instances:\n")
        for (leak in group) {
          sb.append("    @").append(Integer.toHexString(leak.identityHashCode)).append(' ').append(leak.description)
          leak.staleForMs?.let { sb.append(" (retained ").append(it).append(" ms after disposal)") }
          sb.append('\n')
        }
      }
      sb.append("  reference path:\n").append(representative.referencePath.trimEnd().prependIndent("    ")).append("\n\n")
    }
    return sb.toString()
  }

  private fun pathSignature(referencePath: String): String =
    referencePath
      .replace(IDENTITY_HASH_REGEX, "@")
      .replace(ARRAY_INDEX_REGEX, "[]")
      .replace(IDE_FRAME_REGEX, "IdeFrameImpl[")

  private fun copyToClipboard(report: String) {
    CopyPasteManager.getInstance().setContents(StringSelection(report))
  }

  /**
   * Captures a memory snapshot and uploads it to [UPLOADS_URL],
   * then opens a prefilled YouTrack issue whose description links to the uploaded snapshot and copies the report to the clipboard.
   * The opened draft is restricted to [YOUTRACK_VISIBILITY_GROUP] visibility, since the report may contain internal data.
   *
   * If the upload fails or is cancelled, falls back to opening [UPLOADS_URL] and revealing the snapshot file so it can be
   * uploaded manually.
   */
  private fun createTicket(project: Project?, leaks: List<LeakInfo>, report: String) {
    copyToClipboard(report)
    coroutineScope.launch {
      val snapshot = captureUnderProgress(project, "progress.title.capturing.snapshot", "modal.progress.title.capturing.snapshot") {
        captureSnapshot()
      }

      // Upload the snapshot so it can be linked from the ticket; null if capture/upload failed or was cancelled.
      val browseUrl = if (snapshot != null) uploadSnapshotOrNull(project, snapshot) else null

      withContext(Dispatchers.EDT) {
        BrowserUtil.browse(createTicketUrl(leaks, snapshot, browseUrl))
        if (browseUrl == null && snapshot != null) {
          // Upload unavailable: let the reporter upload the snapshot manually.
          BrowserUtil.browse(UPLOADS_URL)
          RevealFileAction.openFile(snapshot)
        }
      }
    }
  }

  private suspend fun <T> captureUnderProgress(project: Project?, backgroundTitleKey: String, modalTitleKey: String, action: suspend () -> T): T =
    if (project != null) {
      withBackgroundProgress(project, DevLeakDetectionBundle.message(backgroundTitleKey), cancellable = true) {
        action()
      }
    }
    else {
      withModalProgress(ModalTaskOwner.guess(), DevLeakDetectionBundle.message(modalTitleKey), TaskCancellation.cancellable()) {
        action()
      }
    }

  /**
   * Uploads [snapshot] to [UPLOADS_URL] under a cancellable progress and returns its browse URL, or `null` if the upload
   * failed or the user cancelled it (in which case the caller falls back to a manual upload).
   */
  private suspend fun uploadSnapshotOrNull(project: Project?, snapshot: Path): String? =
    try {
      captureUnderProgress(project, "progress.title.uploading.snapshot", "modal.progress.title.uploading.snapshot") {
        // runInterruptible ties cancellation to thread interruption so the blocking upload aborts immediately;
        // LogUploader.uploadFile is a blocking JDK HttpClient.send call with no cancellation checks of its own.
        runInterruptible(Dispatchers.IO) {
          LogUploader.getBrowseUrl(LogUploader.uploadFile(snapshot))
        }
      }
    }
    catch (e: CancellationException) {
      // If the enclosing scope itself was cancelled, rethrow to preserve structured concurrency.
      if (!currentCoroutineContext().isActive) throw e

      // Only the upload progress was cancelled by the user: keep going with a manual-upload fallback.
      LOG.info("Memory snapshot upload cancelled; the ticket will point to a manual upload")
      null
    }
    catch (e: Exception) {
      // Rethrow if the enclosing scope was cancelled; otherwise fall back to a manual upload.
      currentCoroutineContext().ensureActive()

      // A cancelled upload surfaces here as an interrupted blocking call (LogUploader wraps InterruptedException).
      if (e.cause is InterruptedException) {
        LOG.info("Memory snapshot upload interrupted; the ticket will point to a manual upload")
      }
      else {
        LOG.warn("Failed to upload memory snapshot for the leak ticket", e)
      }

      null
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

  private fun createTicketUrl(leaks: List<LeakInfo>, snapshot: Path?, browseUrl: String?): String {
    val summary = "Project or Editor leak detected"
    val description = buildString {
      append("Detected ").append(leaks.size).append(" leaked instance(s) in a running IDE.\n")
      append("The full report (reference paths) has been copied to the clipboard and is also in the IDE log (idea.log).\n\n")
      leaks.take(5).forEach { append("- ").append(it.kind).append(' ').append(it.className).append('\n') }
      append('\n')
      when {
        browseUrl != null -> append("Memory snapshot: ").append(browseUrl).append('\n')
        snapshot != null -> append("Memory snapshot: upload the revealed file to ").append(UPLOADS_URL).append(" and paste the link here.\n")
        else -> append("Memory snapshot: capture was unavailable.\n")
      }
    }

    val visibilityCommand = "visible to $YOUTRACK_VISIBILITY_GROUP"
    return "$YOUTRACK_NEW_ISSUE_URL?project=IJPL&summary=${encode(summary)}" +
           "&description=${encode(description)}&c=${encode(visibilityCommand)}"
  }

  private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)
}
