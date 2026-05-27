// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.popup.ActionPopupOptions
import com.intellij.ui.popup.ActionPopupStep
import com.intellij.ui.popup.PopupFactoryImpl
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentPromptLibraryActionTest {
  @Test
  fun inlineSaveAndRemoveActionsTrackUpdatedSavedPromptState() {
    val uiStateService = AgentPromptUiSessionStateService()
    val promptLibraryState = PromptLibraryState(
      promptFiles = emptyList(),
      savedPromptEntries = emptyList(),
      historyEntries = listOf(historyEntry()),
    )
    val action = promptLibraryAction(promptLibraryState, uiStateService)

    assertThat(action.currentEntry()).isInstanceOf(PromptLibraryEntry.RecentPrompt::class.java)

    val saveAction = singleInlineAction(action)
    assertActionText(saveAction, AgentPromptBundle.message("popup.prompt.library.save"))
    saveAction.actionPerformed(TestActionEvent.createTestEvent(saveAction))

    assertThat(uiStateService.loadSavedPrompts().map { entry -> entry.promptText }).containsExactly("3+3?")
    assertThat(action.currentEntry()).isInstanceOf(PromptLibraryEntry.SavedPrompt::class.java)

    val removeAction = singleInlineAction(action)
    assertActionText(removeAction, AgentPromptBundle.message("popup.prompt.library.remove"))
    removeAction.actionPerformed(TestActionEvent.createTestEvent(removeAction))

    assertThat(uiStateService.loadSavedPrompts()).isEmpty()
    assertThat(action.currentEntry()).isInstanceOf(PromptLibraryEntry.RecentPrompt::class.java)
  }

  @Test
  fun popupActionStepInlineSaveRebuildsAsRemoveAction() {
    runInEdtAndWait {
      val uiStateService = AgentPromptUiSessionStateService()
      val promptLibraryState = PromptLibraryState(
        promptFiles = emptyList(),
        savedPromptEntries = emptyList(),
        historyEntries = listOf(historyEntry()),
      )
      val action = promptLibraryAction(promptLibraryState, uiStateService)
      val presentationFactory = PresentationFactory()
      val saveItem = singlePopupInlineActionItem(action, presentationFactory)

      assertThat(saveItem.text).isEqualTo(AgentPromptBundle.message("popup.prompt.library.save"))

      val step = popupStep(action, presentationFactory)
      step.performActionItem(saveItem, null)

      assertThat(uiStateService.loadSavedPrompts().map { entry -> entry.promptText }).containsExactly("3+3?")
      assertThat(action.currentEntry()).isInstanceOf(PromptLibraryEntry.SavedPrompt::class.java)
      assertThat(singlePopupInlineActionItem(action, presentationFactory).text)
        .isEqualTo(AgentPromptBundle.message("popup.prompt.library.remove"))
    }
  }

  @Test
  fun rowActionDoesNotRequestKeepPopupOpen() {
    val uiStateService = AgentPromptUiSessionStateService()
    val promptLibraryState = PromptLibraryState(
      promptFiles = emptyList(),
      savedPromptEntries = emptyList(),
      historyEntries = listOf(historyEntry()),
    )
    val action = promptLibraryAction(promptLibraryState, uiStateService)
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.keepPopupOnPerform).isEqualTo(KeepPopupOnPerform.Never)
    assertThat(event.presentation.getClientProperty(ActionUtil.INLINE_ACTIONS)).hasSize(1)
    assertThat(event.presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNull()
  }

  @Test
  fun savedPromptRowDoesNotShowSavedTextOrIcon() {
    val uiStateService = AgentPromptUiSessionStateService()
    val promptLibraryState = PromptLibraryState(
      promptFiles = emptyList(),
      savedPromptEntries = listOf(savedPromptEntry()),
      historyEntries = emptyList(),
    )
    val action = promptLibraryAction(promptLibraryState, uiStateService)
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)

    assertThat(event.presentation.getClientProperty(ActionUtil.SECONDARY_TEXT)).isNull()
    assertThat(event.presentation.getClientProperty(ActionUtil.SECONDARY_ICON)).isNull()
  }

  private fun promptLibraryAction(
    promptLibraryState: PromptLibraryState,
    uiStateService: AgentPromptUiSessionStateService,
  ): PromptLibraryEntryAction {
    return PromptLibraryEntryAction(
      row = promptLibraryState.rows().single(),
      loadSavedPromptEntries = { promptLibraryState.savedPromptEntries },
      onChoose = {},
      onSave = { recentEntry ->
        uiStateService.savePersistentPrompt(recentEntry.insertText)
          ?.let(promptLibraryState::markSaved)
      },
      onRemove = { savedEntry ->
        uiStateService.removePersistentPrompt(savedEntry.insertText)
        promptLibraryState.markRemoved(savedEntry.insertText)
      },
    )
  }

  private fun singleInlineAction(action: PromptLibraryEntryAction): AnAction {
    val event = TestActionEvent.createTestEvent(action)
    action.update(event)
    val inlineActions: List<AnAction> = event.presentation.getClientProperty(ActionUtil.INLINE_ACTIONS) ?: emptyList()
    assertThat(inlineActions).hasSize(1)
    return inlineActions.single()
  }

  private fun assertActionText(action: AnAction, expectedText: String) {
    val event = TestActionEvent.createTestEvent(action)
    action.update(event)
    assertThat(event.presentation.text).isEqualTo(expectedText)
  }

  private fun singlePopupInlineActionItem(
    action: PromptLibraryEntryAction,
    presentationFactory: PresentationFactory,
  ): PopupFactoryImpl.ActionItem {
    val rowItem = popupActionItems(action, presentationFactory).single()
    val inlineItems = rowItem.inlineItems
    assertThat(inlineItems).hasSize(1)
    return inlineItems.single()
  }

  private fun popupStep(
    action: PromptLibraryEntryAction,
    presentationFactory: PresentationFactory,
  ): ActionPopupStep {
    return ActionPopupStep(
      popupActionItems(action, presentationFactory),
      null,
      { DataContext.EMPTY_CONTEXT },
      ActionPlaces.POPUP,
      presentationFactory,
      ActionPopupOptions.empty(),
    )
  }

  private fun popupActionItems(
    action: PromptLibraryEntryAction,
    presentationFactory: PresentationFactory,
  ): List<PopupFactoryImpl.ActionItem> {
    return ActionPopupStep.createActionItems(
      DefaultActionGroup(action),
      DataContext.EMPTY_CONTEXT,
      ActionPlaces.POPUP,
      presentationFactory,
      ActionPopupOptions.empty(),
    )
  }

  private fun historyEntry(): AgentPromptHistoryEntry {
    return AgentPromptHistoryEntry(
      promptText = "  3+3?  ",
      createdAtMs = 1,
      providerId = "claude",
      targetMode = PromptTargetMode.NEW_TASK,
    )
  }

  private fun savedPromptEntry(): AgentPromptSavedPromptEntry {
    return AgentPromptSavedPromptEntry(
      promptText = "3+3?",
      createdAtMs = 1,
    )
  }
}
