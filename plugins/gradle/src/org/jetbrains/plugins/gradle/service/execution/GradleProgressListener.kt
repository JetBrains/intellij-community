// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.google.gson.GsonBuilder
import com.intellij.build.events.impl.FileDownloadEventImpl
import com.intellij.build.events.impl.FileDownloadedEventImpl
import com.intellij.execution.process.ProcessOutputType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.text.StringUtil
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.ProgressListener
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.plugins.gradle.issue.GradleIssueFailure
import org.jetbrains.plugins.gradle.issue.GradleIssueFailure.Companion.createIssueFailure
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionReporter.GradleExecutionFailureReport
import org.jetbrains.plugins.gradle.service.execution.GradleProgressEventConverter.createTaskNotificationEvent
import org.jetbrains.plugins.gradle.service.execution.GradleProgressEventConverter.legacyConvertTaskNotificationEvent
import org.jetbrains.plugins.gradle.statistics.GradleModelBuilderMessageCollector.FailureGroup.Companion.findByMessageOrNull
import org.jetbrains.plugins.gradle.tooling.Message
import org.jetbrains.plugins.gradle.tooling.MessageReporter
import java.nio.file.Path

/**
 * @author Vladislav.Soroka
 */
@ApiStatus.Internal
class GradleProgressListener(
  private val myTaskId: ExternalSystemTaskId,
  private val myReporter: GradleExecutionReporter,
  private val myListener: ExternalSystemTaskNotificationListener,
  buildRootDir: String,
) : ProgressListener, org.gradle.tooling.ProgressListener {

  private val myDownloadProgressMapper: GradleDownloadProgressMapper = GradleDownloadProgressMapper()
  private val myProgressMapper: GradleExecutionProgressMapper = GradleExecutionProgressMapper()
  private val myStatusEventIds: MutableMap<Any, Long> = HashMap()
  private val myOperationId: String = myTaskId.hashCode().toString() + ":" + FileUtil.pathHashCode(buildRootDir)
  private var myLastStatusChange: ExternalSystemTaskNotificationEvent? = null
  private val sendProgressEventsToOutput: Boolean = `is`(SEND_PROGRESS_EVENTS_TO_OUTPUT_KEY, true)

  override fun statusChanged(event: ProgressEvent) {
    if (myDownloadProgressMapper.canMap(event)) {
      val downloadEvent: ExternalSystemTaskNotificationEvent? = myDownloadProgressMapper.map(myTaskId, event)
      if (downloadEvent != null) {
        myListener.onStatusChange(downloadEvent)
        if (sendProgressEventsToOutput) {
          sendProgressEventToOutput(downloadEvent)
        }
        return
      }
    }
    val progressBuildEvent = myProgressMapper.map(myTaskId, event)
    if (progressBuildEvent != null && progressBuildEvent != myLastStatusChange) {
      myListener.onStatusChange(progressBuildEvent)
      myLastStatusChange = progressBuildEvent
    }
    val taskNotificationEvent = createTaskNotificationEvent(myTaskId, myOperationId, event)
    if (taskNotificationEvent != null) {
      myListener.onStatusChange(taskNotificationEvent)
    }
  }

  override fun statusChanged(event: org.gradle.tooling.ProgressEvent) {
    val eventDescription = event.description
    if (!maybeReportModelBuilderMessage(eventDescription)) {
      val progressBuildEvent = myProgressMapper.mapLegacyEvent(myTaskId, eventDescription)
      if (progressBuildEvent != null && progressBuildEvent != myLastStatusChange) {
        myListener.onStatusChange(progressBuildEvent)
        myLastStatusChange = progressBuildEvent
      }

      val taskNotificationEvent = legacyConvertTaskNotificationEvent(myTaskId, eventDescription)
      myListener.onStatusChange(taskNotificationEvent)

      if (sendProgressEventsToOutput) {
        reportGradleDaemonStartingEvent(eventDescription)
      }
    }
  }

  private fun maybeReportModelBuilderMessage(eventDescription: String): Boolean {
    val message = parseModelBuilderMessage(eventDescription) ?: return false
    myReporter.failure(createGradleIssueFailure(message))
      .withSeverity(message.getSeverity())
      .withInternal(message.isInternal && message.kind == Message.Kind.ERROR)
      .withSuppressed(message.isInternal)
      .withGroup(findByMessageOrNull(message.group))
      .withTitle(message.title)
      .withText(message.text)
      .withTargetPath(message.targetPath?.let { Path.of(it) })
      .report()
    return true
  }

  private fun sendProgressEventToOutput(event: ExternalSystemTaskNotificationEvent) {
    if (event !is ExternalSystemBuildEvent) {
      return
    }
    val buildEvent = event.buildEvent
    if (buildEvent is FileDownloadedEventImpl) {
      val duration = buildEvent.duration
      val operationName = buildEvent.message
      val text = String.format("%s, took %s", operationName, StringUtil.formatDuration(duration))
      myListener.onTaskOutput(myTaskId, "\r" + text + "\n", ProcessOutputType.STDOUT)
    }
    if (buildEvent is FileDownloadEventImpl) {
      val progress = buildEvent.progress
      val total = buildEvent.total
      val operationName = buildEvent.message
      val text = String.format("%s (%s / %s)", operationName, formatFileSize(progress), formatFileSize(total))
      if (buildEvent.isFirstInGroup) {
        myListener.onTaskOutput(myTaskId, text, ProcessOutputType.STDOUT)
      }
      else {
        myListener.onTaskOutput(myTaskId, "\r" + text, ProcessOutputType.STDOUT)
      }
    }
  }

  /**
   * Report Gradle Daemon starting event based on the fact that multiple 'Starting Gradle Daemon' might be received when new
   * ProgressLoggerFactory.newOperation are nested within the 'Starting Gradle Daemon' operation reporting always the parent on
   * completion. Based on that, the Build event will be used to calculate when Daemon was started. Those are the events returned:
   * - Build
   * - Starting Gradle Daemon
   * - Discovering toolchains
   * - Starting Gradle Daemon
   * - Connecting to Gradle Daemon
   * - Starting Gradle Daemon
   * - Build
   */
  private fun reportGradleDaemonStartingEvent(eventDescription: String) {
    if (StringUtil.equals(STARTING_GRADLE_DAEMON_EVENT, eventDescription) && !myStatusEventIds.containsKey(STARTING_GRADLE_DAEMON_EVENT)) {
      myListener.onTaskOutput(myTaskId, "$STARTING_GRADLE_DAEMON_EVENT...\n", ProcessOutputType.STDOUT)
      myStatusEventIds[STARTING_GRADLE_DAEMON_EVENT] = System.currentTimeMillis()
    }
    else if (StringUtil.equals(EXECUTING_BUILD, eventDescription) && myStatusEventIds.containsKey(STARTING_GRADLE_DAEMON_EVENT)) {
      val startTime: Long = myStatusEventIds.remove(STARTING_GRADLE_DAEMON_EVENT)!!
      val duration = StringUtil.formatDuration(System.currentTimeMillis() - startTime)
      myListener.onTaskOutput(myTaskId, "\rGradle Daemon started in $duration\n", ProcessOutputType.STDOUT)
    }
  }

  private fun Message.getSeverity(): GradleExecutionFailureReport.Severity = when (kind) {
    Message.Kind.ERROR -> GradleExecutionFailureReport.Severity.ERROR
    Message.Kind.WARNING -> GradleExecutionFailureReport.Severity.WARNING
    Message.Kind.INFO -> GradleExecutionFailureReport.Severity.INFO
  }

  companion object {

    private val LOG = Logger.getInstance(GradleProgressListener::class.java)
    const val SEND_PROGRESS_EVENTS_TO_OUTPUT_KEY: String = "gradle.output.sync.progress.events"
    private const val EXECUTING_BUILD = "Build"
    private const val STARTING_GRADLE_DAEMON_EVENT = "Starting Gradle Daemon"

    private fun parseModelBuilderMessage(eventDescription: String): Message? {
      if (!eventDescription.startsWith(MessageReporter.MODEL_BUILDER_SERVICE_MESSAGE_PREFIX)) {
        return null
      }
      val messageString = StringUtil.substringAfter(eventDescription, MessageReporter.MODEL_BUILDER_SERVICE_MESSAGE_PREFIX)
      return try {
        GsonBuilder().create().fromJson(messageString, Message::class.java)
      }
      catch (e: Exception) {
        LOG.warn("Failed to report model builder message using event '$eventDescription'", e)
        null
      }
    }

    @VisibleForTesting
    fun createGradleIssueFailure(message: Message): GradleIssueFailure {
      val failure = message.failure
      return if (failure == null) {
        createIssueFailure(message.title, message.text)
      }
      else {
        createGradleIssueFailure(failure)
      }
    }

    private fun createGradleIssueFailure(failure: Message.Failure): GradleIssueFailure = createIssueFailure(
      failure.message,
      failure.description,
      failure.causes.map { createGradleIssueFailure(it) }
    )

    private fun formatFileSize(value: Long): String = StringUtil.formatFileSize(value, " ", -1, true)
  }
}
