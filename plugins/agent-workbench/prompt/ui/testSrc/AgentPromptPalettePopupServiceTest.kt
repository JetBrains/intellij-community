// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INVOCATION_PREFER_EXTENSIONS_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
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
    assertThat(createdPopups.single().showCalls).isEqualTo(1)
    assertThat(createdPopups.single().focusCalls).isZero()
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
    return AgentPromptPalettePopupController { invocationData, onClosed ->
      FakePopupSession(invocationData, onClosed).also(createdPopups::add)
    }
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
    private val onClosed: () -> Unit,
  ) : AgentPromptPalettePopupSession {
    var showCalls: Int = 0
    var focusCalls: Int = 0
    var promptText: String = ""
    private var visible: Boolean = false

    override fun show() {
      showCalls++
      visible = true
    }

    override fun requestFocus() {
      focusCalls++
    }

    override fun isVisible(): Boolean = visible

    fun close() {
      visible = false
      onClosed()
    }
  }
}
