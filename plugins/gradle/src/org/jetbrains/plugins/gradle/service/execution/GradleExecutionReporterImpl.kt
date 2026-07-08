// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.FileNavigatable
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.MessageEvent
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.registry.Registry
import com.intellij.pom.Navigatable
import org.gradle.tooling.model.build.BuildEnvironment
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.issue.GradleIssueFailure
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionReporter.GradleExecutionFailureReport
import org.jetbrains.plugins.gradle.service.project.BaseProjectImportErrorHandler.getErrorFilePosition
import org.jetbrains.plugins.gradle.statistics.GradleModelBuilderMessageCollector
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.nio.file.Path
import kotlin.io.path.isRegularFile

private val LOG = logger<GradleExecutionReporter>()

internal class GradleExecutionReporterImpl(
  private val projectPath: String,
  private val taskId: ExternalSystemTaskId,
  private val listener: ExternalSystemTaskNotificationListener,
) : GradleExecutionReporter {

  var buildEnvironment: BuildEnvironment? = null

  private val projectRoot: Path
    get() = buildEnvironment?.buildIdentifier?.rootDir?.toPath() ?: Path.of(projectPath)

  private val processedMessageKeys = HashSet<MessageKey>()
  private val processedIssueKeys = HashSet<IssueKey>()

  override fun failure(failure: GradleIssueFailure): GradleExecutionFailureReport =
    GradleExecutionFailureReportImpl(this, failure)

  private fun reportFailure(report: GradleExecutionFailureReportImpl) {
    reportFusEvent(report)
    reportLoggerEvent(report)
    reportBuildEvent(report)
  }

  private fun reportFusEvent(report: GradleExecutionFailureReportImpl) {
    val group = report.group ?: return
    val kind = when (report.severity) {
      GradleExecutionFailureReport.Severity.ERROR -> GradleModelBuilderMessageCollector.FailureKind.ERROR
      GradleExecutionFailureReport.Severity.WARNING -> GradleModelBuilderMessageCollector.FailureKind.WARNING
      GradleExecutionFailureReport.Severity.INFO -> GradleModelBuilderMessageCollector.FailureKind.INFO
    }
    GradleModelBuilderMessageCollector.logFailure(taskId.project, taskId.id, kind, group)
  }

  private fun reportLoggerEvent(report: GradleExecutionFailureReportImpl) {
    val text = report.text ?: report.failure.description ?: report.failure.text
    when (report.isInternal) {
      true -> LOG.error(text)
      else -> LOG.debug(text)
    }
  }

  private fun reportBuildEvent(report: GradleExecutionFailureReportImpl) {
    when (Registry.`is`("gradle.show.suppressed.failure.events")) {
      true -> {
        reportMessageEvent(report)
        reportIssueEvent(report)
      }
      else -> {
        val isIssueEventReported = reportIssueEvent(report)
        if (!report.isSuppressed && !isIssueEventReported) {
          reportMessageEvent(report)
        }
      }
    }
  }

  private fun reportMessageEvent(report: GradleExecutionFailureReportImpl) {
    val title = report.title ?: report.failure.message ?: return
    val description = report.text ?: report.failure.description ?: report.failure.message ?: return
    val filePosition = getErrorFilePosition(report.failure, projectRoot)
    val messageEventKind = report.severity.getEventKind()
    if (processedMessageKeys.add(MessageKey(messageEventKind, title, description, filePosition))) {
      val messageEvent = MessageEvent.builder(title, messageEventKind)
        .withDescription(description)
        .withParentId(taskId)
        .withFilePosition(filePosition)
        .withNavigatable(createFileNavigatable(report) ?: createTargetNavigatable(report))
        .build()
      listener.onStatusChange(ExternalSystemBuildEvent(taskId, messageEvent))
    }
  }

  private fun GradleExecutionFailureReport.Severity.getEventKind(): MessageEvent.Kind {
    return when (this) {
      GradleExecutionFailureReport.Severity.ERROR -> MessageEvent.Kind.ERROR
      GradleExecutionFailureReport.Severity.WARNING -> MessageEvent.Kind.WARNING
      GradleExecutionFailureReport.Severity.INFO -> MessageEvent.Kind.INFO
    }
  }

  private fun reportIssueEvent(report: GradleExecutionFailureReportImpl): Boolean {
    val filePosition = getErrorFilePosition(report.failure, projectRoot)
    val issueData = GradleIssueData.createIssueData(projectRoot, report.failure, buildEnvironment, filePosition)
    val issues = GradleIssueChecker.getKnownIssuesCheckList().mapNotNull { it.check(issueData) }
    for (issue in issues) {
      if (processedIssueKeys.add(IssueKey(issue.title, issue.description, filePosition))) {
        val issueEvent = BuildIssueEvent.builder(issue, MessageEvent.Kind.ERROR)
          .withParentId(taskId)
          .withFilePosition(filePosition)
          .build()
        listener.onStatusChange(ExternalSystemBuildEvent(taskId, issueEvent))
      }
    }
    return issues.isNotEmpty()
  }

  private fun createFileNavigatable(report: GradleExecutionFailureReportImpl): Navigatable? {
    val filePosition = getErrorFilePosition(report.failure, projectRoot) ?: return null
    return FileNavigatable(taskId.project, filePosition)
  }

  private fun createTargetNavigatable(report: GradleExecutionFailureReportImpl): Navigatable? {
    val targetPath = report.targetPath ?: return null
    val buildFile = GradleConstants.KNOWN_GRADLE_FILES.asSequence()
                      .map { targetPath.resolve(it) }
                      .firstOrNull { it.isRegularFile() }
                    ?: return null
    return FileNavigatable(taskId.project, FilePosition(buildFile, 0, 0))
  }

  private data class MessageKey(
    val kind: MessageEvent.Kind,
    val title: @NlsSafe String,
    val description: String,
    val filePosition: FilePosition?,
  )

  private data class IssueKey(
    val title: String,
    val description: String,
    val filePosition: FilePosition?,
  )

  private data class GradleExecutionFailureReportImpl(
    private val failureHandler: GradleExecutionReporterImpl,
    val failure: GradleIssueFailure,
    val severity: GradleExecutionFailureReport.Severity = GradleExecutionFailureReport.Severity.ERROR,
    val isInternal: Boolean = false,
    val isSuppressed: Boolean = false,
    val group: String? = null,
    val title: @NlsSafe String? = null,
    val text: @NlsSafe String? = null,
    val targetPath: Path? = null,
  ) : GradleExecutionFailureReport {

    override fun withSeverity(severity: GradleExecutionFailureReport.Severity): GradleExecutionFailureReportImpl =
      copy(severity = severity)

    override fun withInternal(isInternal: Boolean): GradleExecutionFailureReportImpl =
      copy(isInternal = isInternal)

    override fun withSuppressed(isSuppressed: Boolean): GradleExecutionFailureReportImpl =
      copy(isSuppressed = isSuppressed)

    override fun withGroup(group: String?): GradleExecutionFailureReportImpl =
      copy(group = group)

    override fun withTitle(title: @NlsSafe String?): GradleExecutionFailureReportImpl =
      copy(title = title)

    override fun withText(text: @NlsSafe String?): GradleExecutionFailureReportImpl =
      copy(text = text)

    override fun withTargetPath(targetPath: Path?): GradleExecutionFailureReportImpl =
      copy(targetPath = targetPath)

    override fun report(): Unit =
      failureHandler.reportFailure(this)
  }
}
