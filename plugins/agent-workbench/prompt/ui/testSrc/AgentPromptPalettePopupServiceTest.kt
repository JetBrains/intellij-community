// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptContextItem
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.UI
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptPalettePopupServiceTest {
  @Test
  fun showCreatesPopupWhenNoneIsVisible() {
    val createdPopups = mutableListOf<FakePopupSession>()
    val service = service(createdPopups)

    runBlocking(Dispatchers.EDT) {
      service.show(invocationData(actionId = "AgentWorkbenchPrompt.OpenGlobalPalette"))
    }

    assertThat(createdPopups).hasSize(1)
    assertThat(createdPopups.single().invocationData.actionId).isEqualTo("AgentWorkbenchPrompt.OpenGlobalPalette")
    assertThat(createdPopups.single().initialAddContextRequest).isNull()
    assertThat(createdPopups.single().showCalls).isEqualTo(1)
    assertThat(createdPopups.single().focusCalls).isZero()
  }

  @Test
  fun showAddContextCreatesPopupWithInitialRequestWhenNoneIsVisible() {
    val createdPopups = mutableListOf<FakePopupSession>()
    val service = service(createdPopups)
    val request = addContextRequest()

    runBlocking(Dispatchers.EDT) {
      service.showAddContext(invocationData(actionId = "AgentWorkbenchPrompt.AddToAgentContext"), request)
    }

    assertThat(createdPopups).hasSize(1)
    assertThat(createdPopups.single().initialAddContextRequest).isSameAs(request)
    assertThat(createdPopups.single().showCalls).isEqualTo(1)
  }

  @Test
  fun showAddContextAppliesToVisiblePopupWithoutCreatingFreshPopup() {
    val createdPopups = mutableListOf<FakePopupSession>()
    val service = service(createdPopups)
    val request = addContextRequest()

    runBlocking(Dispatchers.EDT) {
      service.show(invocationData(actionId = "AgentWorkbenchPrompt.OpenGlobalPalette"))
      service.showAddContext(invocationData(actionId = "AgentWorkbenchPrompt.AddToAgentContext"), request)
    }

    val popup = createdPopups.single()
    assertThat(popup.appliedAddContextRequests).containsExactly(request)
    assertThat(popup.focusCalls).isZero()
    assertThat(popup.composerFocusCalls).isEqualTo(1)
    assertThat(popup.showCalls).isEqualTo(1)
  }

  @Test
  fun showAddContextFromUiDispatcherCreatesPopupWithModelAccessWhenNoneIsVisible(): Unit = timeoutRunBlocking {
    val createdPopups = mutableListOf<FakePopupSession>()
    var factoryCanRead = false
    val service = AgentPromptPalettePopupController { invocationData, initialAddContextRequest, onClosed ->
      runReadActionBlocking {
        factoryCanRead = true
      }
      FakePopupSession(invocationData, initialAddContextRequest, onClosed).also(createdPopups::add)
    }
    val request = addContextRequest()

    withContext(Dispatchers.UI) {
      service.showAddContextFromUiDispatcher(invocationData(actionId = "AgentWorkbenchPrompt.AddToAgentContext"), request)
    }

    assertThat(factoryCanRead).isTrue()
    assertThat(createdPopups).hasSize(1)
    assertThat(createdPopups.single().initialAddContextRequest).isSameAs(request)
    assertThat(createdPopups.single().showCalls).isEqualTo(1)
  }

  @Test
  fun showAddContextFromUiDispatcherAppliesToVisiblePopupWithoutCreatingFreshPopup(): Unit = timeoutRunBlocking {
    val createdPopups = mutableListOf<FakePopupSession>()
    val service = service(createdPopups)
    val request = addContextRequest()

    withContext(Dispatchers.EDT) {
      service.show(invocationData(actionId = "AgentWorkbenchPrompt.OpenGlobalPalette"))
    }

    withContext(Dispatchers.UI) {
      service.showAddContextFromUiDispatcher(invocationData(actionId = "AgentWorkbenchPrompt.AddToAgentContext"), request)
    }

    val popup = createdPopups.single()
    assertThat(popup.appliedAddContextRequests).containsExactly(request)
    assertThat(popup.composerFocusCalls).isEqualTo(1)
    assertThat(popup.showCalls).isEqualTo(1)
  }

  @Test
  fun showFocusesExistingVisiblePopupForRepeatShortcutWithoutResettingState() {
    val createdPopups = mutableListOf<FakePopupSession>()
    val service = service(createdPopups)

    runBlocking(Dispatchers.EDT) {
      service.show(invocationData(actionId = "AgentWorkbenchPrompt.OpenGlobalPalette"))
    }
    val popup = createdPopups.single()
    popup.promptText = "keep draft"

    runBlocking(Dispatchers.EDT) {
      service.show(invocationData(actionId = "AgentWorkbenchPrompt.OpenGlobalPaletteAutoSelect", preferExtensions = true))
    }

    assertThat(createdPopups).hasSize(1)
    assertThat(popup.showCalls).isEqualTo(1)
    assertThat(popup.focusCalls).isEqualTo(1)
    assertThat(popup.composerFocusCalls).isZero()
    assertThat(popup.promptText).isEqualTo("keep draft")
  }

  @Test
  fun showCreatesFreshPopupAfterActivePopupCloses() {
    val createdPopups = mutableListOf<FakePopupSession>()
    val service = service(createdPopups)

    runBlocking(Dispatchers.EDT) {
      service.show(invocationData(actionId = "AgentWorkbenchPrompt.OpenGlobalPalette"))
    }
    val firstPopup = createdPopups.single()
    firstPopup.close()

    runBlocking(Dispatchers.EDT) {
      service.show(invocationData(actionId = "AgentWorkbenchPrompt.OpenGlobalPalette"))
    }

    assertThat(createdPopups).hasSize(2)
    assertThat(createdPopups[0]).isSameAs(firstPopup)
    assertThat(createdPopups[1].invocationData.actionId).isEqualTo("AgentWorkbenchPrompt.OpenGlobalPalette")
    assertThat(createdPopups[1].showCalls).isEqualTo(1)
    assertThat(firstPopup.focusCalls).isZero()
  }

  private fun service(createdPopups: MutableList<FakePopupSession>): AgentPromptPalettePopupController {
    return AgentPromptPalettePopupController { invocationData, initialAddContextRequest, onClosed ->
      FakePopupSession(invocationData, initialAddContextRequest, onClosed).also(createdPopups::add)
    }
  }

  private fun addContextRequest(): AgentPromptAddContextRequest {
    return AgentPromptAddContextRequest(
      contextItems = listOf(
        AgentPromptContextItem(
          rendererId = "test",
          title = "Test",
          body = "snippet",
        )
      ),
      target = null,
    )
  }

  private fun invocationData(actionId: String, preferExtensions: Boolean = false): AgentPromptInvocationData {
    val attributes = if (preferExtensions) {
      mapOf(AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY to true)
    }
    else {
      emptyMap()
    }
    return AgentPromptInvocationData(
      project = ProjectManager.getInstance().defaultProject,
      actionId = actionId,
      actionText = null,
      actionPlace = null,
      invokedAtMs = 0L,
      attributes = attributes,
    )
  }

  private class FakePopupSession(
    val invocationData: AgentPromptInvocationData,
    val initialAddContextRequest: AgentPromptAddContextRequest?,
    private val onClosed: () -> Unit,
  ) : AgentPromptPalettePopupSession {
    var showCalls: Int = 0
    var focusCalls: Int = 0
    var composerFocusCalls: Int = 0
    var promptText: String = ""
    val appliedAddContextRequests: MutableList<AgentPromptAddContextRequest> = mutableListOf()
    private var visible: Boolean = false

    override fun show() {
      showCalls++
      visible = true
    }

    override fun requestFocus() {
      focusCalls++
    }

    override fun requestComposerFocus() {
      composerFocusCalls++
    }

    override fun isVisible(): Boolean = visible

    override fun applyAddContext(request: AgentPromptAddContextRequest): AgentPromptAddContextApplyResult {
      appliedAddContextRequests.add(request)
      return AgentPromptAddContextApplyResult.ADDED
    }

    fun close() {
      visible = false
      onClosed()
    }
  }
}
