// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.execution

import com.intellij.build.FileNavigatable
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.toCanonicalPath
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertion
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertion.Companion.assertCollectionOrdered
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertions.assertEmpty
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.util.io.createDirectories
import org.gradle.tooling.GradleConnector
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.ConfigurableGradleBuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.issue.GradleIssueFailure
import org.jetbrains.plugins.gradle.service.execution.GradleExecutionReporter.GradleExecutionFailureReport
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.GradleTestFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.projectFixture
import org.jetbrains.plugins.gradle.testFramework.util.buildEnvironment
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.params.ParameterizedClass
import java.nio.file.Path
import kotlin.io.path.createFile

@TestApplication
@ParameterizedClass
@BaseGradleVersionSource
class GradleExecutionReporterTest(gradleVersion: GradleVersion) {

  private val projectRootFixture = tempPathFixture()
  private val projectRoot by projectRootFixture

  private val gradleFixture = gradleFixture(gradleVersion)

  private val projectFixture = gradleFixture.projectFixture(projectRootFixture, numProjectSyncs = 0)

  private val issueChecker by extensionPointFixture(GradleIssueChecker.EP_NAME, ::TestFailureIssueChecker)

  private val reporterFixture by testFixture {
    initialized(GradleExecutionReporterFixture(
      projectRootFixture.init(),
      projectFixture.init(),
      gradleFixture.init()
    )) {}
  }

  @Test
  fun `test failure handler reports message event`() {
    val failure = GradleIssueFailure.createIssueFailure("title", "description")

    reporterFixture.reporter.failure(failure)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      messageEvent("title", "description")
    }
  }

  @Test
  fun `test failure handler uses configured title for message event`() {
    val failure = GradleIssueFailure.createIssueFailure("failure message", "description")

    reporterFixture.reporter.failure(failure)
      .withTitle("event title")
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      messageEvent("event title", "description")
    }
  }

  @Test
  fun `test failure handler uses configured text for message event`() {
    val failure = GradleIssueFailure.createIssueFailure("title", "failure description")

    reporterFixture.reporter.failure(failure)
      .withText("event text")
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      messageEvent("title", "event text")
    }
  }

  @Test
  fun `test failure handler reports message event with file position`() {
    val buildFile = projectRoot.resolve("build.gradle")
    val filePosition = FilePosition(buildFile, 12, 0)
    val message = "title"
    val description = stackTrace("description", filePosition)
    val failure = GradleIssueFailure.createIssueFailure(message, description)

    reporterFixture.reporter.failure(failure)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      messageEvent(message, description, filePosition)
    }
  }

  @Test
  fun `test failure handler reports message event with target navigatable`() {
    val targetPath = projectRoot.resolve("module")
      .createDirectories()
    val buildFile = targetPath.resolve("build.gradle")
      .createFile()
    val failure = GradleIssueFailure.createIssueFailure("title", "description")

    reporterFixture.reporter.failure(failure)
      .withTargetPath(targetPath)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      messageEvent("title", "description", navigatableFile = buildFile)
    }
  }

  @Test
  fun `test failure handler reports internal failure events by default`() {
    LoggedErrorProcessor.executeAndReturnLoggedError {
      val failure = GradleIssueFailure.createIssueFailure("title", "description")

      reporterFixture.reporter.failure(failure)
        .withInternal(true)
        .report()

      assertCollectionOrdered(reporterFixture.events) {
        messageEvent("title", "description")
      }
    }
  }

  @Test
  @RegistryKey("gradle.show.suppressed.failure.events", true.toString())
  fun `test failure handler reports suppressed failure events when registry is enabled`() {
    val failure = GradleIssueFailure.createIssueFailure("title", "description")

    reporterFixture.reporter.failure(failure)
      .withSuppressed(true)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      messageEvent("title", "description")
    }
  }

  @Test
  fun `test failure handler hides suppressed failure events by default`() {
    val failure = GradleIssueFailure.createIssueFailure("title", "description")

    reporterFixture.reporter.failure(failure)
      .withSuppressed(true)
      .report()

    assertEmpty(reporterFixture.events)
  }

  @Test
  fun `test failure handler reports known issue for suppressed failure events by default`() {
    val message = issueChecker.markFailureMessage("title", "message")
    val failure = GradleIssueFailure.createIssueFailure(message, "description")

    reporterFixture.reporter.failure(failure)
      .withSuppressed(true)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      issueEvent("title", "message")
    }
  }

  @Test
  @RegistryKey("gradle.show.suppressed.failure.events", true.toString())
  fun `test failure handler reports message event for known issue when registry is enabled`() {
    val message = issueChecker.markFailureMessage("title", "message")
    val failure = GradleIssueFailure.createIssueFailure(message, "description")

    reporterFixture.reporter.failure(failure)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      messageEvent(message, "description")
      issueEvent("title", "message")
    }
  }

  @Test
  @RegistryKey("gradle.show.suppressed.failure.events", true.toString())
  fun `test failure handler reports message event and known issue for suppressed failure events when registry is enabled`() {
    val message = issueChecker.markFailureMessage("title", "message")
    val failure = GradleIssueFailure.createIssueFailure(message, "description")

    reporterFixture.reporter.failure(failure)
      .withSuppressed(true)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      messageEvent(message, "description")
      issueEvent("title", "message")
    }
  }

  @Test
  fun `test failure handler reports warning message as warning`() {
    val failure = GradleIssueFailure.createIssueFailure("title", "description")

    reporterFixture.reporter.failure(failure)
      .withSeverity(GradleExecutionFailureReport.Severity.WARNING)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      messageEvent("title", "description", kind = MessageEvent.Kind.WARNING)
    }
  }

  @Test
  fun `test failure handler reports known issues as errors`() {
    val message = issueChecker.markFailureMessage("title", "message")
    val failure = GradleIssueFailure.createIssueFailure(message, "description")

    reporterFixture.reporter.failure(failure)
      .withSeverity(GradleExecutionFailureReport.Severity.WARNING)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      issueEvent("title", "message")
    }
  }

  @Test
  fun `test failure handler deduplicates same failure events`() {
    val buildFile = projectRoot.resolve("build.gradle")
    val filePosition = FilePosition(buildFile, 12, 0)
    val message = issueChecker.markFailureMessage("title", "message")
    val description = stackTrace("description", filePosition)
    val failure1 = GradleIssueFailure.createIssueFailure(message, description)
    val failure2 = GradleIssueFailure.createIssueFailure(message, description)

    reporterFixture.reporter.failure(failure1)
      .report()
    reporterFixture.reporter.failure(failure2)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      issueEvent("title", "message", filePosition)
    }
  }

  @Test
  fun `test failure handler keeps issue events with different titles`() {
    val buildFile = projectRoot.resolve("build.gradle")
    val filePosition = FilePosition(buildFile, 12, 0)
    val message1 = issueChecker.markFailureMessage("title (1)", "message")
    val message2 = issueChecker.markFailureMessage("title (2)", "message")
    val description = stackTrace("description", filePosition)
    val failure1 = GradleIssueFailure.createIssueFailure(message1, description)
    val failure2 = GradleIssueFailure.createIssueFailure(message2, description)

    reporterFixture.reporter.failure(failure1)
      .report()
    reporterFixture.reporter.failure(failure2)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      issueEvent("title (1)", "message", filePosition)
      issueEvent("title (2)", "message", filePosition)
    }
  }

  @Test
  fun `test failure handler keeps issue events with different messages`() {
    val buildFile = projectRoot.resolve("build.gradle")
    val filePosition = FilePosition(buildFile, 12, 0)
    val message1 = issueChecker.markFailureMessage("title", "message (1)")
    val message2 = issueChecker.markFailureMessage("title", "message (2)")
    val description = stackTrace("description", filePosition)
    val failure1 = GradleIssueFailure.createIssueFailure(message1, description)
    val failure2 = GradleIssueFailure.createIssueFailure(message2, description)

    reporterFixture.reporter.failure(failure1)
      .report()
    reporterFixture.reporter.failure(failure2)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      issueEvent("title", "message (1)", filePosition)
      issueEvent("title", "message (2)", filePosition)
    }
  }

  @Test
  fun `test failure handler keeps message events and deduplicates issue events with same issue key`() {
    val buildFile = projectRoot.resolve("build.gradle")
    val filePosition = FilePosition(buildFile, 12, 0)
    val message = issueChecker.markFailureMessage("title", "message")
    val description1 = stackTrace("description1", filePosition)
    val description2 = stackTrace("description2", filePosition)
    val failure1 = GradleIssueFailure.createIssueFailure(message, description1)
    val failure2 = GradleIssueFailure.createIssueFailure(message, description2)

    reporterFixture.reporter.failure(failure1)
      .report()
    reporterFixture.reporter.failure(failure2)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      issueEvent("title", "message", filePosition)
    }
  }

  @Test
  fun `test failure handler keeps issue events with different file positions`() {
    val buildFile = projectRoot.resolve("build.gradle")
    val filePosition1 = FilePosition(buildFile, 12, 0)
    val filePosition2 = FilePosition(buildFile, 34, 0)
    val message = issueChecker.markFailureMessage("title", "message")
    val description1 = stackTrace("description", filePosition1)
    val description2 = stackTrace("description", filePosition2)
    val failure1 = GradleIssueFailure.createIssueFailure(message, description1)
    val failure2 = GradleIssueFailure.createIssueFailure(message, description2)

    reporterFixture.reporter.failure(failure1)
      .report()
    reporterFixture.reporter.failure(failure2)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      issueEvent("title", "message", filePosition1)
      issueEvent("title", "message", filePosition2)
    }
  }

  @Test
  fun `test failure handler uses file position from nested failure stacktrace`() {
    val buildFile = projectRoot.resolve("build.gradle")
    val nestedFilePosition = FilePosition(buildFile, 42, 0)
    val message = issueChecker.markFailureMessage("title", "message")
    val description = stackTrace("description")
    val nestedDescription = stackTrace("nested description", nestedFilePosition)
    val nestedFailure = GradleIssueFailure.createIssueFailure("nested message", nestedDescription)
    val failure = GradleIssueFailure.createIssueFailure(message, description, listOf(nestedFailure))

    reporterFixture.reporter.failure(failure)
      .report()

    assertCollectionOrdered(reporterFixture.events) {
      issueEvent("title", "message", nestedFilePosition)
    }
  }

  private fun stackTrace(
    message: String,
    filePosition: FilePosition? = null,
  ): String = buildString {
    appendLine("java.lang.IllegalStateException: $message")
    if (filePosition != null) {
      appendLine(" at build_gradle.run(${filePosition.path}:${filePosition.startLine})")
    }
    appendLine(" at org.gradle.tooling.internal.consumer.DefaultBuildLauncher.run(DefaultBuildLauncher.java:89)")
  }

  private fun CollectionAssertion<BuildEvent>.messageEvent(
    message: String,
    description: String,
    filePosition: FilePosition? = null,
    navigatableFile: Path? = null,
    kind: MessageEvent.Kind = MessageEvent.Kind.ERROR,
  ) {
    assertElement { actual ->
      assertInstanceOf<MessageEvent>(actual)
      assertEquals(kind, actual.kind)
      assertEquals(message, actual.message)
      assertEquals(description, actual.description)
      if (filePosition != null) {
        assertInstanceOf<FileMessageEvent>(actual)
        assertEquals(filePosition, actual.filePosition)
      }
      if (navigatableFile != null) {
        val navigatable = actual.getNavigatable(projectFixture.get())
        assertInstanceOf<FileNavigatable>(navigatable)
        assertEquals(FilePosition(navigatableFile, 0, 0), navigatable.filePosition)
      }
    }
  }

  private fun CollectionAssertion<BuildEvent>.issueEvent(
    title: String,
    description: String,
    filePosition: FilePosition? = null,
  ) {
    assertElement { actual ->
      assertInstanceOf<BuildIssueEvent>(actual)
      assertEquals(MessageEvent.Kind.ERROR, actual.kind)
      assertEquals(title, actual.issue.title)
      assertEquals(description + "\n", actual.issue.description)
      if (filePosition != null) {
        assertInstanceOf<FileMessageEvent>(actual)
        assertEquals(filePosition, actual.filePosition)
      }
    }
  }

  private class GradleExecutionReporterFixture(
    projectRoot: Path,
    project: Project,
    gradle: GradleTestFixture,
  ) {

    val events = ArrayList<BuildEvent>()

    val reporter: GradleExecutionReporter

    init {
      val listener = object : ExternalSystemTaskNotificationListener {
        override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
          assertInstanceOf<ExternalSystemBuildEvent>(event)
          events.add(event.buildEvent)
        }
      }
      val projectPath = projectRoot.toCanonicalPath()
      val taskId = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.RESOLVE_PROJECT, project)
      val cancellationToken = GradleConnector.newCancellationTokenSource().token()
      val settings = GradleExecutionSettings()
      val context = GradleExecutionContextImpl(projectPath, taskId, settings, listener, cancellationToken)
      context.buildEnvironment = gradle.buildEnvironment
        .withProjectRoot(projectRoot)

      reporter = context.reporter
    }
  }

  private class TestFailureIssueChecker : GradleIssueChecker {

    fun markFailureMessage(issueTitle: String, issueMessage: String): String =
      "$TEST_FAILURE_MARKER$issueTitle$TEST_FAILURE_PAYLOAD_SEPARATOR$issueMessage"

    override fun check(issueData: GradleIssueData): ConfigurableGradleBuildIssue? {
      val markedMessage = issueData.failure.message ?: return null
      if (!markedMessage.startsWith(TEST_FAILURE_MARKER)) return null
      val message = markedMessage.substringAfter(TEST_FAILURE_MARKER)
      return object : ConfigurableGradleBuildIssue() {}.apply {
        setTitle(message.substringBefore(TEST_FAILURE_PAYLOAD_SEPARATOR))
        addDescription(message.substringAfter(TEST_FAILURE_PAYLOAD_SEPARATOR))
      }
    }
  }

  companion object {
    private const val TEST_FAILURE_MARKER = "IDEA test failure issue: "
    private const val TEST_FAILURE_PAYLOAD_SEPARATOR = " | "
  }
}
