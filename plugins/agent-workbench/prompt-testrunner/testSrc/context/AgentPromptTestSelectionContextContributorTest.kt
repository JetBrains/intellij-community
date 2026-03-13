// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.testrunner.context

import com.intellij.agent.workbench.prompt.testrunner.AgentPromptTestRunnerBundle
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextRendererIds
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptContextTruncationReason
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptPayloadValue
import com.intellij.agent.workbench.sessions.core.prompt.array
import com.intellij.agent.workbench.sessions.core.prompt.number
import com.intellij.agent.workbench.sessions.core.prompt.objOrNull
import com.intellij.agent.workbench.sessions.core.prompt.string
import com.intellij.execution.Location
import com.intellij.execution.testframework.AbstractTestProxy
import com.intellij.execution.testframework.TestConsoleProperties
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.pom.Navigatable
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentPromptTestSelectionContextContributorTest {
  private val contributor = AgentPromptTestSelectionContextContributor()

  @Test
  fun returnsEmptyWhenInvocationHasNoDataContext() {
    val result = contributor.collect(invocationData(dataContext = null))

    assertThat(result).isEmpty()
  }

  @Test
  fun returnsEmptyWhenInvocationHasNoTestSelection() {
    val dataContext = SimpleDataContext.builder().build()

    val result = contributor.collect(invocationData(dataContext = dataContext))

    assertThat(result).isEmpty()
  }

  @Test
  fun includesAllSelectedTestsAndAssertionHints() {
    val dataContext = SimpleDataContext.builder()
      .add(
        AbstractTestProxy.DATA_KEYS,
        arrayOf(
          testProxy(name = "testPassed", locationUrl = "java:test://Suite.testPassed", isPassed = true),
          testProxy(
            name = "testFailedA",
            locationUrl = "java:test://Suite.testFailedA",
            isDefect = true,
            errorMessage = "expected:<1> but was:<2>",
          ),
          testProxy(
            name = "testFailedB",
            locationUrl = "java:test://Suite.testFailedB",
            isDefect = true,
            stacktrace = "AssertionError: boom\n  at Suite.testFailedB(Suite.kt:42)",
          ),
        )
      )
      .build()

    val result = contributor.collect(invocationData(dataContext = dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!
    val entries = payload.array("entries")!!.map { value -> value.objOrNull()!! }

    assertThat(item.rendererId).isEqualTo(AgentPromptContextRendererIds.TEST_FAILURES)
    assertThat(item.title).isEqualTo(AgentPromptTestRunnerBundle.message("context.tests.title"))
    assertThat(item.itemId).isEqualTo("testRunner.selection")
    assertThat(item.parentItemId).isNull()
    assertThat(item.source).isEqualTo("testRunner")
    assertThat(item.body.lineSequence().toList()).containsExactly(
      "passed: Suite#testPassed",
      "failed: Suite#testFailedA | assertion: expected:<1> but was:<2>",
      "failed: Suite#testFailedB | assertion: AssertionError: boom",
    )
    assertThat(payload.number("selectedCount")).isEqualTo("3")
    assertThat(payload.number("candidateCount")).isEqualTo("3")
    assertThat(payload.number("includedCount")).isEqualTo("3")
    assertThat(entries.map { entry -> entry.string("status") }).containsExactly("passed", "failed", "failed")
    assertThat(entries.map { entry -> entry.string("locationUrl") }).containsExactly(
      "java:test://Suite.testPassed",
      "java:test://Suite.testFailedA",
      "java:test://Suite.testFailedB",
    )
    assertThat(entries.map { entry -> entry.string("reference") }).containsExactly(
      "Suite#testPassed",
      "Suite#testFailedA",
      "Suite#testFailedB",
    )
    assertThat(entries.map { entry -> entry.string("assertionMessage") }).containsExactly(
      null,
      "expected:<1> but was:<2>",
      "AssertionError: boom",
    )
    assertThat(statusCounts(payload)).containsExactlyEntriesOf(mapOf("failed" to "2", "passed" to "1"))
    assertThat(item.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.NONE)
  }

  @Test
  fun fallsBackToSelectedTestsWhenNoFailingSelectionExists() {
    val dataContext = SimpleDataContext.builder()
      .add(
        AbstractTestProxy.DATA_KEYS,
        arrayOf(
          testProxy(name = "testPassed", locationUrl = "java:test://Suite.testPassed", isPassed = true),
          testProxy(name = "testIgnored", locationUrl = "java:test://Suite.testIgnored", isIgnored = true),
        )
      )
      .build()

    val result = contributor.collect(invocationData(dataContext = dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!
    val entries = payload.array("entries")!!.map { value -> value.objOrNull()!! }

    assertThat(item.body.lineSequence().toList()).containsExactly(
      "passed: Suite#testPassed",
      "ignored: Suite#testIgnored",
    )
    assertThat(payload.number("selectedCount")).isEqualTo("2")
    assertThat(payload.number("candidateCount")).isEqualTo("2")
    assertThat(payload.number("includedCount")).isEqualTo("2")
    assertThat(entries.map { entry -> entry.string("status") }).containsExactly("passed", "ignored")
    assertThat(entries.map { entry -> entry.string("reference") }).containsExactly("Suite#testPassed", "Suite#testIgnored")
    assertThat(statusCounts(payload)).containsExactlyEntriesOf(mapOf("passed" to "1", "ignored" to "1"))
  }

  @Test
  fun truncatesToConfiguredSelectionLimit() {
    val selection = (1..8).map { index ->
      testProxy(
        name = "test$index",
        locationUrl = "java:test://Suite.test$index",
        isDefect = true,
        errorMessage = "failure $index",
      )
    }.toTypedArray()
    val dataContext = SimpleDataContext.builder()
      .add(AbstractTestProxy.DATA_KEYS, selection)
      .build()

    val result = contributor.collect(invocationData(dataContext = dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!

    assertThat(item.body.lineSequence().toList()).hasSize(5)
    assertThat(payload.number("selectedCount")).isEqualTo("8")
    assertThat(payload.number("candidateCount")).isEqualTo("8")
    assertThat(payload.number("includedCount")).isEqualTo("5")
    assertThat(item.truncation.reason).isEqualTo(AgentPromptContextTruncationReason.SOURCE_LIMIT)
  }

  @Test
  fun fallsBackToSingleSelectionWhenArraySelectionIsMissing() {
    val dataContext = SimpleDataContext.builder()
      .add(
        AbstractTestProxy.DATA_KEY,
        testProxy(name = "testSingle", isDefect = true, errorMessage = "single failure"),
      )
      .build()

    val result = contributor.collect(invocationData(dataContext = dataContext))

    assertThat(result).hasSize(1)
    val item = result.single()
    val payload = item.payload.objOrNull()!!
    val entry = payload.array("entries")!!.single().objOrNull()!!

    assertThat(item.body).isEqualTo("failed: testSingle | assertion: single failure")
    assertThat(payload.number("selectedCount")).isEqualTo("1")
    assertThat(payload.number("candidateCount")).isEqualTo("1")
    assertThat(payload.number("includedCount")).isEqualTo("1")
    assertThat(entry.string("name")).isEqualTo("testSingle")
    assertThat(entry.string("locationUrl")).isNull()
    assertThat(entry.string("reference")).isEqualTo("testSingle")
    assertThat(entry.string("status")).isEqualTo("failed")
    assertThat(entry.string("assertionMessage")).isEqualTo("single failure")
    assertThat(statusCounts(payload)).containsExactlyEntriesOf(mapOf("failed" to "1"))
  }

  private fun statusCounts(payload: AgentPromptPayloadValue.Obj): Map<String, String> {
    return payload.fields["statusCounts"]
      ?.objOrNull()
      ?.fields
      ?.mapNotNull { (status, value) ->
        when (value) {
          is AgentPromptPayloadValue.Num -> status to value.value
          is AgentPromptPayloadValue.Str -> status to value.value
          else -> null
        }
      }
      ?.toMap()
      .orEmpty()
  }

  private fun invocationData(dataContext: DataContext?): AgentPromptInvocationData {
    val project = ProjectManager.getInstance().defaultProject
    val attributes = if (dataContext == null) {
      emptyMap()
    }
    else {
      mapOf(AGENT_PROMPT_TEST_RUNNER_INVOCATION_DATA_CONTEXT_KEY to dataContext)
    }
    return AgentPromptInvocationData(
      project = project,
      actionId = "AgentWorkbenchPrompt.OpenGlobalPalette",
      actionText = "Ask Agent",
      actionPlace = "RunView",
      invokedAtMs = 0L,
      attributes = attributes,
    )
  }

  private fun testProxy(
    name: String,
    locationUrl: String? = null,
    isDefect: Boolean = false,
    isPassed: Boolean = false,
    isIgnored: Boolean = false,
    isInProgress: Boolean = false,
    errorMessage: String? = null,
    stacktrace: String? = null,
  ): AbstractTestProxy {
    return TestProxy(
      name = name,
      locationUrl = locationUrl,
      isDefect = isDefect,
      isPassed = isPassed,
      isIgnored = isIgnored,
      isInProgress = isInProgress,
      errorMessage = errorMessage,
      stacktrace = stacktrace,
    )
  }

  private class TestProxy(
    private val name: String,
    private val locationUrl: String?,
    private val isDefect: Boolean,
    private val isPassed: Boolean,
    private val isIgnored: Boolean,
    private val isInProgress: Boolean,
    private val errorMessage: String?,
    private val stacktrace: String?,
  ) : AbstractTestProxy() {
    override fun isInProgress(): Boolean = isInProgress

    override fun isDefect(): Boolean = isDefect

    override fun shouldRun(): Boolean = true

    override fun getMagnitude(): Int = 0

    override fun isLeaf(): Boolean = true

    override fun isInterrupted(): Boolean = false

    override fun hasPassedTests(): Boolean = isPassed

    override fun isIgnored(): Boolean = isIgnored

    override fun isPassed(): Boolean = isPassed

    override fun getName(): String = name

    override fun isConfig(): Boolean = false

    override fun getLocation(project: Project, searchScope: GlobalSearchScope): Location<*>? = null

    override fun getDescriptor(location: Location<*>?, properties: TestConsoleProperties): Navigatable? = null

    override fun getParent(): AbstractTestProxy? = null

    override fun getChildren(): List<AbstractTestProxy> = emptyList()

    override fun getAllTests(): List<AbstractTestProxy> = listOf(this)

    override fun shouldSkipRootNodeForExport(): Boolean = false

    override fun getLocationUrl(): String? = locationUrl

    override fun getErrorMessage(): String? = errorMessage

    override fun getStacktrace(): String? = stacktrace
  }
}
