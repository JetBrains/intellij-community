// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Separator
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class AgentSessionsGearActionsTest {
  @Test
  fun gearActionsContainOpenFileToggleAndRefresh() {
    val actionManager = ActionManager.getInstance()

    assertThat(actionManager.childActionIds("AgentWorkbenchSessions.ToolWindow.GearActions"))
      .contains("OpenFile")
      .contains("AgentWorkbenchSessions.ToggleDedicatedFrame")
      .contains("AgentWorkbenchSessions.ToggleClaudeQuotaWidget")
      .contains("AgentWorkbenchSessions.Refresh")
      .doesNotContain("AgentWorkbenchSessions.OpenDedicatedFrame")
  }

  @Test
  fun titleActionRegistersOpenDedicatedFrameWithWindowIcon() {
    val actionManager = ActionManager.getInstance()
    val action = actionManager.getAction("AgentWorkbenchSessions.OpenDedicatedFrame")

    assertThat(action)
      .isNotNull
      .isInstanceOf(AgentSessionsOpenDedicatedFrameAction::class.java)
    assertThat(action?.templatePresentation?.icon).isEqualTo(AllIcons.Actions.MoveToWindow)
  }

  @Test
  fun toggleActionUpdatesAdvancedSetting() {
    val actionManager = ActionManager.getInstance()
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("<advancedSetting")
      .contains("id=\"agent.workbench.chat.open.in.dedicated.frame\"")
    assertThat(actionManager.getAction("AgentWorkbenchSessions.ToggleDedicatedFrame"))
      .isNotNull
      .isInstanceOf(AgentSessionsDedicatedFrameToggleAction::class.java)
  }

  @Test
  fun dedicatedFrameRegistersProjectShortcutAliasAction() {
    val actionManager = ActionManager.getInstance()
    val shortcutAliasAction = actionManager.getAction("AgentWorkbenchSessions.ActivateWithProjectShortcut")
    val platformAction = actionManager.getAction("ActivateProjectToolWindow")

    assertThat(shortcutAliasAction)
      .isNotNull
      .isInstanceOf(AgentSessionsActivateWithProjectShortcutAction::class.java)
    if (platformAction != null) {
      assertThat(platformAction).isNotSameAs(shortcutAliasAction)
    }
  }

  @Test
  fun dedicatedFrameRegistersOpenDedicatedFrameAction() {
    val actionManager = ActionManager.getInstance()
    val openDedicatedFrameAction = actionManager.getAction("AgentWorkbenchSessions.OpenDedicatedFrame")

    assertThat(openDedicatedFrameAction)
      .isNotNull
      .isInstanceOf(AgentSessionsOpenDedicatedFrameAction::class.java)
    assertThat(actionManager.childActionIds("OpenProjectWindows")).contains("AgentWorkbenchSessions.OpenDedicatedFrame")
  }

  @Test
  fun dedicatedFrameRegistersMainToolbarExclusions() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.xml is missing"
    }.readText()

    assertThat(descriptor)
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"MainToolbarVCSGroup\"/>")
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"ExecutionTargetsToolbarGroup\"/>")
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"NewUiRunWidget\"/>")
      .doesNotContain("actionConfigurationCustomizer")
  }

  @Test
  fun dedicatedFrameRegistersMainToolbarSourceProjectAction() {
    val actionManager = ActionManager.getInstance()
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.xml is missing"
    }.readText()

    assertThat(actionManager.getAction("AgentWorkbenchSessions.GoToSourceProjectFromToolbar"))
      .isNotNull
      .isInstanceOf(AgentSessionsGoToSourceProjectFromToolbarAction::class.java)
    assertThat(descriptor)
      .contains("id=\"AgentWorkbenchSessions.GoToSourceProjectFromToolbar\"")
      .contains("<add-to-group group-id=\"MainToolbarRight\" anchor=\"before\" relative-to-action=\"NewUiRunWidget\"/>")
  }

  @Test
  fun editorTabActionsAreRegisteredInEditorTabPopupMenu() {
    val actionManager = ActionManager.getInstance()

    assertThat(actionManager.getAction("AgentWorkbenchSessions.SelectThreadInAgentThreads"))
      .isNotNull
      .isInstanceOf(AgentSessionsSelectThreadInToolWindowAction::class.java)
    assertThat(actionManager.getAction("AgentWorkbenchSessions.ArchiveThreadFromEditorTab"))
      .isNotNull
      .isInstanceOf(AgentSessionsEditorTabArchiveThreadAction::class.java)
    assertThat(actionManager.getAction("AgentWorkbenchSessions.GoToSourceProjectFromEditorTab"))
      .isNotNull
      .isInstanceOf(AgentSessionsGoToSourceProjectFromEditorTabAction::class.java)
    assertThat(actionManager.getAction("AgentWorkbenchSessions.BindPendingCodexThreadFromEditorTab"))
      .isNotNull
      .isInstanceOf(AgentSessionsBindPendingCodexThreadFromEditorTabAction::class.java)
    assertThat(actionManager.getAction("AgentWorkbenchSessions.CopyThreadIdFromEditorTab"))
      .isNotNull
      .isInstanceOf(AgentSessionsCopyThreadIdFromEditorTabAction::class.java)

    val entries = actionManager.editorTabPopupEntries()

    val archiveIndex = entries.requiredIndex("AgentWorkbenchSessions.ArchiveThreadFromEditorTab")
    val goToSourceIndex = entries.requiredIndex("AgentWorkbenchSessions.GoToSourceProjectFromEditorTab")
    val bindPendingIndex = entries.requiredIndex("AgentWorkbenchSessions.BindPendingCodexThreadFromEditorTab")
    val closeEditorsGroupIndex = entries.requiredIndex("CloseEditorsGroup")
    val copyThreadIdIndex = entries.requiredIndex("AgentWorkbenchSessions.CopyThreadIdFromEditorTab")
    val selectInThreadsIndex = entries.requiredIndex("AgentWorkbenchSessions.SelectThreadInAgentThreads")
    val copyPathsIndex = entries.requiredIndex("CopyPaths")

    assertThat(archiveIndex).isLessThan(goToSourceIndex)
    assertThat(goToSourceIndex).isLessThan(bindPendingIndex)
    assertThat(bindPendingIndex).isLessThan(closeEditorsGroupIndex)
    assertThat(closeEditorsGroupIndex).isLessThan(copyThreadIdIndex)
    assertThat(copyThreadIdIndex).isLessThan(selectInThreadsIndex)
    assertThat(selectInThreadsIndex).isLessThan(copyPathsIndex)
    assertThat(entries[closeEditorsGroupIndex - 1]).isEqualTo(SEPARATOR_MARKER)
    assertThat(entries[closeEditorsGroupIndex + 1]).isEqualTo(SEPARATOR_MARKER)
    assertThat(entries.subList(selectInThreadsIndex + 1, copyPathsIndex)).doesNotContain(SEPARATOR_MARKER)
  }

  @Test
  fun archiveThreadFromEditorTabShortcutsMatchPlatformDefaults() {
    val descriptor = checkNotNull(javaClass.classLoader.getResource("intellij.agent.workbench.sessions.xml")) {
      "Module descriptor intellij.agent.workbench.sessions.xml is missing"
    }.readText()
    val defaultKeymapShortcut = "<keyboard-shortcut keymap=\"" + '$' + "default\" first-keystroke=\"control alt DELETE\"/>"

    assertThat(descriptor)
      .contains(defaultKeymapShortcut)
      .contains("<keyboard-shortcut keymap=\"Default for XWin\" first-keystroke=\"control alt DELETE\" remove=\"true\"/>")
      .contains("<keyboard-shortcut keymap=\"Default for XWin\" first-keystroke=\"alt shift F4\"/>")
      .contains("<keyboard-shortcut keymap=\"Default for GNOME\" first-keystroke=\"control alt DELETE\" remove=\"true\"/>")
      .contains("<keyboard-shortcut keymap=\"Default for GNOME\" first-keystroke=\"alt shift F4\"/>")
      .contains("<keyboard-shortcut keymap=\"Default for KDE\" first-keystroke=\"control alt DELETE\" remove=\"true\"/>")
      .contains("<keyboard-shortcut keymap=\"Default for KDE\" first-keystroke=\"alt shift F4\"/>")
      .contains("<keyboard-shortcut keymap=\"Mac OS X 10.5+\" first-keystroke=\"meta alt DELETE\"/>")
  }

  @Test
  fun treePopupActionsAreRegisteredInActionSystem() {
    val actionManager = ActionManager.getInstance()

    assertThat(actionManager.childActionIds("AgentWorkbenchSessions.TreePopup"))
      .contains("AgentWorkbenchSessions.TreePopup.Open")
      .contains("AgentWorkbenchSessions.TreePopup.More")
      .contains("AgentWorkbenchSessions.TreePopup.NewThread")
      .contains("AgentWorkbenchSessions.TreePopup.Archive")

    assertThat(actionManager.getAction("AgentWorkbenchSessions.TreePopup.NewThread"))
      .isNotNull
    assertThat(actionManager.getAction("AgentWorkbenchSessions.TreePopup.NewThread")?.templatePresentation?.icon)
      .isEqualTo(AllIcons.General.Add)
  }

  @Test
  fun editorTabNewThreadActionsAreRegisteredInActionSystem() {
    val actionManager = ActionManager.getInstance()
    val quickActionId = "AgentWorkbenchSessions.EditorTab.NewThreadQuick"
    val popupGroupId = "AgentWorkbenchSessions.EditorTab.NewThreadPopup"

    assertThat(actionManager.getAction(quickActionId))
      .isNotNull
      .isInstanceOf(AgentSessionsEditorTabNewThreadQuickAction::class.java)
    assertThat(actionManager.getAction(popupGroupId))
      .isNotNull
      .isInstanceOf(AgentSessionsEditorTabNewThreadPopupGroup::class.java)
    assertThat(actionManager.getAction(popupGroupId)?.templatePresentation?.icon)
      .isEqualTo(AllIcons.General.Add)
  }

  @Test
  fun refreshActionTriggersSessionsRefresh() {
    var refreshInvocations = 0
    val refreshAction = AgentSessionsRefreshAction(
      refreshSessions = { refreshInvocations++ },
      isRefreshingProvider = { false },
    )

    runInEdtAndWait {
      refreshAction.actionPerformed(TestActionEvent.createTestEvent(refreshAction))
    }

    assertThat(refreshInvocations)
      .withFailMessage("Refresh action didn't trigger sessions refresh callback.")
      .isEqualTo(1)
  }

  private fun ActionManager.childActionIds(groupId: String): List<String> {
    val group = getAction(groupId) as? ActionGroup
    assertThat(group).withFailMessage("Action group '%s' is not registered", groupId).isNotNull
    return checkNotNull(group).getChildren(TestActionEvent.createTestEvent()).mapNotNull { getId(it) }
  }

  private fun ActionManager.editorTabPopupEntries(): List<String> {
    val group = getAction("EditorTabPopupMenu") as? ActionGroup
    assertThat(group).withFailMessage("Action group '%s' is not registered", "EditorTabPopupMenu").isNotNull
    return flattenEditorTabPopupEntries(checkNotNull(group).getChildren(TestActionEvent.createTestEvent()))
  }

  private fun ActionManager.flattenEditorTabPopupEntries(actions: Array<AnAction>): List<String> {
    return actions.flatMap { action ->
      when (action) {
        is Separator -> listOf(SEPARATOR_MARKER)
        is ActionGroup -> {
          if (getId(action) == INLINE_EDITOR_TAB_SEPARATOR_GROUP_ID) {
            flattenEditorTabPopupEntries(action.getChildren(TestActionEvent.createTestEvent()))
          }
          else {
            getId(action)?.let(::listOf).orEmpty()
          }
        }
        else -> getId(action)?.let(::listOf).orEmpty()
      }
    }
  }

  private fun List<String>.requiredIndex(entry: String): Int {
    val index = indexOf(entry)
    assertThat(index).withFailMessage("Entry '%s' was not found in: %s", entry, this).isNotEqualTo(-1)
    return index
  }

  companion object {
    private const val SEPARATOR_MARKER = "<separator>"
    private const val INLINE_EDITOR_TAB_SEPARATOR_GROUP_ID = "AgentWorkbenchSessions.EditorTabPopup.SeparatorBeforeCloseActions"
  }
}
