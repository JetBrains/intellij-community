// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.syncAction.impl

import com.intellij.build.FilePosition
import com.intellij.build.events.BuildIssueEvent
import com.intellij.build.events.FileMessageEvent
import com.intellij.gradle.toolingExtension.impl.modelAction.GradleModelFetchFailure
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationEvent
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListener
import com.intellij.openapi.externalSystem.model.task.event.ExternalSystemBuildEvent
import com.intellij.platform.testFramework.assertion.collectionAssertion.CollectionAssertion.Companion.assertCollectionOrdered
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.tempPathFixture
import org.gradle.util.GradleVersion
import org.jetbrains.plugins.gradle.issue.ConfigurableGradleBuildIssue
import org.jetbrains.plugins.gradle.issue.GradleIssueChecker
import org.jetbrains.plugins.gradle.issue.GradleIssueData
import org.jetbrains.plugins.gradle.testFramework.util.buildEnvironment
import org.jetbrains.plugins.gradle.testFramework.annotations.BaseGradleVersionSource
import org.jetbrains.plugins.gradle.testFramework.fixtures.gradleFixture
import org.jetbrains.plugins.gradle.testFramework.fixtures.projectFixture
import org.jetbrains.plugins.gradle.testFramework.projectModel.mock.GradleTestProjectResolverContext.Companion.projectResolverContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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

  @BeforeEach
  fun setUpIssueChecker(@TestDisposable disposable: Disposable) {
    GradleIssueChecker.EP_NAME.point.registerExtension(TestModelFetchFailureIssueChecker(), disposable)
  }

  @Test
  fun `test sync failure handler deduplicates same issue events`() {
    val buildFile = testRoot.resolve("build.gradle")
    val filePosition = FilePosition(buildFile, 12, 0)

    val buildEvents = reportSyncFailures(
      modelFetchFailure("title", "message", filePosition),
      modelFetchFailure("title", "message", filePosition),
    )

    assertCollectionOrdered(buildEvents) {
      assertElement { actual ->
        assertEquals("title", actual.issue.title)
        assertEquals("message\n", actual.issue.description)
        assertEquals(filePosition, (actual as FileMessageEvent).filePosition)
      }
    }
  }

  @Test
  fun `test sync failure handler keeps issue events with different descriptions`() {

    val buildEvents = reportSyncFailures(
      modelFetchFailure("title", "message (1)"),
      modelFetchFailure("title", "message (2)"),
    )

    assertCollectionOrdered(buildEvents) {
      assertElement { actual ->
        assertEquals("title", actual.issue.title)
        assertEquals("message (1)\n", actual.issue.description)
      }
      assertElement { actual ->
        assertEquals("title", actual.issue.title)
        assertEquals("message (2)\n", actual.issue.description)
      }
    }
  }

  @Test
  fun `test sync failure handler keeps issue events with different titles`() {
    val buildFile = testRoot.resolve("build.gradle")
    val filePosition = FilePosition(buildFile, 12, 0)

    val buildEvents = reportSyncFailures(
      modelFetchFailure("title (1)", "message", filePosition),
      modelFetchFailure("title (2)", "message", filePosition),
    )

    assertCollectionOrdered(buildEvents) {
      assertElement { actual ->
        assertEquals("title (1)", actual.issue.title)
        assertEquals("message\n", actual.issue.description)
        assertEquals(filePosition, (actual as FileMessageEvent).filePosition)
      }
      assertElement { actual ->
        assertEquals("title (2)", actual.issue.title)
        assertEquals("message\n", actual.issue.description)
        assertEquals(filePosition, (actual as FileMessageEvent).filePosition)
      }
    }
  }

  @Test
  fun `test sync failure handler keeps issue events with different file positions`() {
    val buildFile = testRoot.resolve("build.gradle")
    val firstPosition = FilePosition(buildFile, 12, 0)
    val secondPosition = FilePosition(buildFile, 34, 0)

    val buildEvents = reportSyncFailures(
      modelFetchFailure("title", "message", firstPosition),
      modelFetchFailure("title", "message", secondPosition),
    )

    assertCollectionOrdered(buildEvents) {
      assertElement { actual ->
        assertEquals("title", actual.issue.title)
        assertEquals("message\n", actual.issue.description)
        assertEquals(firstPosition, (actual as FileMessageEvent).filePosition)
      }
      assertElement { actual ->
        assertEquals("title", actual.issue.title)
        assertEquals("message\n", actual.issue.description)
        assertEquals(secondPosition, (actual as FileMessageEvent).filePosition)
      }
    }
  }

  @Test
  fun `test sync failure handler uses file position from nested failure stacktrace`() {
    val buildFile = testRoot.resolve("build.gradle")
    val nestedPosition = FilePosition(buildFile, 42, 0)

    val buildEvents = reportSyncFailures(
      modelFetchFailure("title", "message", causes = listOf(modelFetchFailure("nested title", "nested message", nestedPosition))),
    )

    assertCollectionOrdered(buildEvents) {
      assertElement { actual ->
        assertEquals("title", actual.issue.title)
        assertEquals("message\n", actual.issue.description)
        assertEquals(nestedPosition, (actual as FileMessageEvent).filePosition)
      }
    }
  }

  private fun reportSyncFailures(vararg failures: GradleModelFetchFailure): List<BuildIssueEvent> {
    val events = ArrayList<BuildIssueEvent>()

    val context = projectResolverContext(project) {
      it.buildEnvironment = gradle.buildEnvironment
      it.listener = object : ExternalSystemTaskNotificationListener {
        override fun onStatusChange(event: ExternalSystemTaskNotificationEvent) {
          if (event is ExternalSystemBuildEvent) {
            val buildEvent = event.buildEvent
            if (buildEvent is BuildIssueEvent) {
              events.add(buildEvent)
            }
          }
        }
      }
    }

    GradleSyncFailureHandler().reportSyncFailures(context, failures.toList())

    return events
  }

  private fun modelFetchFailure(
    title: String,
    message: String,
    filePosition: FilePosition? = null,
    causes: List<GradleModelFetchFailure> = emptyList(),
  ): GradleModelFetchFailure {
    val markedMessage = "$TEST_MODEL_FETCH_FAILURE_MARKER$title$TEST_MODEL_FETCH_FAILURE_PAYLOAD_SEPARATOR$message"
    return GradleModelFetchFailure(
      message = markedMessage,
      description = buildString {
        appendLine("java.lang.IllegalStateException: $markedMessage")
        if (filePosition != null) {
          appendLine(" at build_gradle.run(${filePosition.path}:${filePosition.startLine})")
        }
        appendLine(" at org.gradle.tooling.internal.consumer.DefaultBuildLauncher.run(DefaultBuildLauncher.java:89)")
      },
      causes = causes,
    )
  }

  private class TestModelFetchFailureIssueChecker : GradleIssueChecker {
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
