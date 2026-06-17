// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.build.events.MessageEvent
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchFailure
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertion
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertion.Companion.assertCollectionOrdered
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.extensionPointFixture
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.ConfigurableGradleBuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.projectFixture
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestProjectResolverContext.Companion.projectResolverContext
import org.jetbrains.plugins.gradle.testFramework.util.buildEnvironment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertInstanceOf
import org.junit.jupiter.params.ParameterizedClass

@TestApplication
@ParameterizedClass
@BaseGradleVersionSource
class GradleSyncProjectConfiguratorTest(gradleVersion: GradleVersion) {

  private val testRootFixture = tempPathFixture()
  private val testRoot by testRootFixture

  private val gradleFixture = gradleFixture(gradleVersion)
  private val gradle by gradleFixture

  private val project by gradleFixture.projectFixture(testRootFixture, numProjectSyncs = 0)

  private val issueChecker by extensionPointFixture(GradleIssueChecker.EP_NAME, ::TestModelFetchFailureIssueChecker)

  @Test
  fun `test sync failure handler reports message event`() {
    val buildEvents = reportSyncFailures(
      GradleModelFetchFailure("title", "description", emptyList()),
    )

    assertCollectionOrdered(buildEvents) {
      messageEvent("title", "description", null)
    }
  }

  @Test
  fun `test sync failure handler reports message event with file filePosition`() {
    val buildFile = testRoot.resolve("build.gradle")
    val filePosition = FilePosition(buildFile, 12, 0)
    val message = "title"
    val description = stackTrace("description", filePosition)

    val buildEvents = reportSyncFailures(
      GradleModelFetchFailure(message, description, emptyList()),
    )

    assertCollectionOrdered(buildEvents) {
      messageEvent(message, description, filePosition)
    }
  }

  @Test
  fun `test sync failure handler deduplicates same issue events`() {
    val buildFile = testRoot.resolve("build.gradle")
    val filePosition = FilePosition(buildFile, 12, 0)
    val message = issueChecker.markFailureMessage("title", "message")
    val description = stackTrace("description", filePosition)

    val buildEvents = reportSyncFailures(
      GradleModelFetchFailure(message, description, emptyList()),
      GradleModelFetchFailure(message, description, emptyList()),
    )

    assertCollectionOrdered(buildEvents) {
      messageEvent(message, description, filePosition)
      issueEvent("title", "message\n", filePosition)
    }
  }

  @Test
  fun `test sync failure handler keeps issue events with different titles`() {
    val buildFile = testRoot.resolve("build.gradle")
    val filePosition = FilePosition(buildFile, 12, 0)
    val message1 = issueChecker.markFailureMessage("title (1)", "message")
    val message2 = issueChecker.markFailureMessage("title (2)", "message")
    val description = stackTrace("description", filePosition)

    val buildEvents = reportSyncFailures(
      GradleModelFetchFailure(message1, description, emptyList()),
      GradleModelFetchFailure(message2, description, emptyList()),
    )

    assertCollectionOrdered(buildEvents) {
      messageEvent(message1, description, filePosition)
      issueEvent("title (1)", "message\n", filePosition)
      messageEvent(message2, description, filePosition)
      issueEvent("title (2)", "message\n", filePosition)
    }
  }

  @Test
  fun `test sync failure handler keeps issue events with different messages`() {
    val buildFile = testRoot.resolve("build.gradle")
    val filePosition = FilePosition(buildFile, 12, 0)
    val message1 = issueChecker.markFailureMessage("title", "message (1)")
    val message2 = issueChecker.markFailureMessage("title", "message (2)")
    val description = stackTrace("description", filePosition)

    val buildEvents = reportSyncFailures(
      GradleModelFetchFailure(message1, description, emptyList()),
      GradleModelFetchFailure(message2, description, emptyList()),
    )

    assertCollectionOrdered(buildEvents) {
      messageEvent(message1, description, filePosition)
      issueEvent("title", "message (1)\n", filePosition)
      messageEvent(message2, description, filePosition)
      issueEvent("title", "message (2)\n", filePosition)
    }
  }

  @Test
  fun `test sync failure handler keeps issue events with different descriptions`() {
    val buildFile = testRoot.resolve("build.gradle")
    val filePosition = FilePosition(buildFile, 12, 0)
    val message = issueChecker.markFailureMessage("title", "message")
    val description1 = stackTrace("description1", filePosition)
    val description2 = stackTrace("description2", filePosition)

    val buildEvents = reportSyncFailures(
      GradleModelFetchFailure(message, description1, emptyList()),
      GradleModelFetchFailure(message, description2, emptyList()),
    )

    assertCollectionOrdered(buildEvents) {
      messageEvent(message, description1, filePosition)
      issueEvent("title", "message\n", filePosition)
      messageEvent(message, description2, filePosition)
    }
  }

  @Test
  fun `test sync failure handler keeps issue events with different file filePositions`() {
    val buildFile = testRoot.resolve("build.gradle")
    val filePosition1 = FilePosition(buildFile, 12, 0)
    val filePosition2 = FilePosition(buildFile, 34, 0)
    val message = issueChecker.markFailureMessage("title", "message")
    val description1 = stackTrace("description", filePosition1)
    val description2 = stackTrace("description", filePosition2)

    val buildEvents = reportSyncFailures(
      GradleModelFetchFailure(message, description1, emptyList()),
      GradleModelFetchFailure(message, description2, emptyList()),
    )

    assertCollectionOrdered(buildEvents) {
      messageEvent(message, description1, filePosition1)
      issueEvent("title", "message\n", filePosition1)
      messageEvent(message, description2, filePosition2)
      issueEvent("title", "message\n", filePosition2)
    }
  }

  @Test
  fun `test sync failure handler uses file filePosition from nested failure stacktrace`() {
    val buildFile = testRoot.resolve("build.gradle")
    val nestedFilePosition = FilePosition(buildFile, 42, 0)
    val message = issueChecker.markFailureMessage("title", "message")
    val description = stackTrace("description", null)
    val nestedDescription = stackTrace("nested description", nestedFilePosition)

    val buildEvents = reportSyncFailures(
      GradleModelFetchFailure(message, description, causes = listOf(
        GradleModelFetchFailure("nested message", nestedDescription, emptyList())
      )),
    )

    assertCollectionOrdered(buildEvents) {
      messageEvent(message, description, nestedFilePosition)
      issueEvent("title", "message\n", nestedFilePosition)
    }
  }

  private fun stackTrace(message: String, filePosition: FilePosition?): String = buildString {
    appendLine("java.lang.IllegalStateException: $message")
    if (filePosition != null) {
      appendLine(" at build_gradle.run(${filePosition.path}:${filePosition.startLine})")
    }
    appendLine(" at org.gradle.tooling.internal.consumer.DefaultBuildLauncher.run(DefaultBuildLauncher.java:89)")
  }

  private fun reportSyncFailures(vararg failures: GradleModelFetchFailure): List<BuildEvent> {
    val events = ArrayList<BuildEvent>()

    val context = projectResolverContext(project) {
      it.buildEnvironment = gradle.buildEnvironment
      it.listener = object : ExternalSystemTaskNotificationListener {
        override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
          assertInstanceOf<ExternalSystemBuildEvent>(event)
          events.add(event.buildEvent)
        }
      }
    }

    GradleSyncFailureHandler().reportSyncFailures(context, failures.toList())

    return events
  }

  private fun CollectionAssertion<BuildEvent>.messageEvent(message: String, description: String, filePosition: FilePosition?) {
    assertElement { actual ->
      assertInstanceOf<MessageEvent>(actual)
      assertEquals(MessageEvent.Kind.ERROR, actual.kind)
      assertEquals(message, actual.message)
      assertEquals(description, actual.description)
      if (filePosition != null) {
        assertInstanceOf<FileMessageEvent>(actual)
        assertEquals(filePosition, actual.filePosition)
      }
    }
  }

  private fun CollectionAssertion<BuildEvent>.issueEvent(title: String, description: String, filePosition: FilePosition?) {
    assertElement { actual ->
      assertInstanceOf<BuildIssueEvent>(actual)
      assertEquals(MessageEvent.Kind.ERROR, actual.kind)
      assertEquals(title, actual.issue.title)
      assertEquals(description, actual.issue.description)
      if (filePosition != null) {
        assertInstanceOf<FileMessageEvent>(actual)
        assertEquals(filePosition, actual.filePosition)
      }
    }
  }

  private class TestModelFetchFailureIssueChecker : GradleIssueChecker {

    fun markFailureMessage(issueTitle: String, issueMessage: String): String =
      "$TEST_MODEL_FETCH_FAILURE_MARKER$issueTitle$TEST_MODEL_FETCH_FAILURE_PAYLOAD_SEPARATOR$issueMessage"

    override fun check(issueData: GradleIssueData): ConfigurableGradleBuildIssue? {
      val markedMessage = issueData.failure.message ?: return null
      if (!markedMessage.startsWith(TEST_MODEL_FETCH_FAILURE_MARKER)) return null
      val message = markedMessage.substringAfter(TEST_MODEL_FETCH_FAILURE_MARKER)
      return object : ConfigurableGradleBuildIssue() {}.apply {
        setTitle(message.substringBefore(TEST_MODEL_FETCH_FAILURE_PAYLOAD_SEPARATOR))
        addDescription(message.substringAfter(TEST_MODEL_FETCH_FAILURE_PAYLOAD_SEPARATOR))
      }
    }
  }

  companion object {
    private const val TEST_MODEL_FETCH_FAILURE_MARKER = "IDEA test model fetch failure issue: "
    private const val TEST_MODEL_FETCH_FAILURE_PAYLOAD_SEPARATOR = " | "
  }
}
