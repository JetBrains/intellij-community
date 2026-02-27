// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.actions.AgentSessionsActivateWithProjectShortcutAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsBindPendingCodexThreadFromEditorTabAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsCopyThreadIdFromEditorTabAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsDedicatedFrameToggleAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabArchiveThreadAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabNewThreadPopupGroup
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabNewThreadQuickAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsGoToSourceProjectFromEditorTabAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsGoToSourceProjectFromToolbarAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsOpenDedicatedFrameAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsRefreshAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsSelectThreadInToolWindowAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
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
      .contains("AgentWorkbenchSessions.TogglePreventSleepWhileWorking")
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
  fun toggleActionUpdatesAdvancedSetting(@TestDisposable disposable: Disposable) {
    val actionManager = ActionManager.getInstance()
    val action = AgentSessionsDedicatedFrameToggleAction()
    val advancedSettings = AdvancedSettings.getInstance() as AdvancedSettingsImpl

    assertThat(sessionsDescriptor())
      .contains("<advancedSetting")
      .contains("id=\"agent.workbench.chat.open.in.dedicated.frame\"")
    assertThat(actionManager.getAction("AgentWorkbenchSessions.ToggleDedicatedFrame"))
      .isNotNull
      .isInstanceOf(AgentSessionsDedicatedFrameToggleAction::class.java)

    advancedSettings.setSetting(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID, true, disposable)
    val event = TestActionEvent.createTestEvent(action)
    assertThat(action.isSelected(event)).isTrue()

    runInEdtAndWait {
      action.setSelected(event, false)
    }

    assertThat(AdvancedSettings.getBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID)).isFalse()

    runInEdtAndWait {
      action.setSelected(event, true)
    }

    assertThat(AdvancedSettings.getBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID)).isTrue()
  }

  @Test
  fun sleepPreventionToggleRegistersSettingAndStartupActivity() {
    val actionManager = ActionManager.getInstance()

    assertThat(sessionsDescriptor())
      .contains("<postStartupActivity implementation=\"com.intellij.agent.workbench.sessions.sleep.AgentSessionSleepPreventionStartupActivity\"/>")
      .contains("<advancedSetting")
      .contains("id=\"agent.workbench.prevent.system.sleep.while.working\"")
    assertThat(actionManager.getAction("AgentWorkbenchSessions.TogglePreventSleepWhileWorking"))
      .isNotNull
      .isInstanceOf(AgentSessionsPreventSleepWhileWorkingToggleAction::class.java)
    assertThat(AgentSessionsBundle.message("action.AgentWorkbenchSessions.TogglePreventSleepWhileWorking.text"))
      .isEqualTo("Prevent System Sleep While Agent Is Working")
    assertThat(AgentSessionsBundle.message("advanced.setting.agent.workbench.prevent.system.sleep.while.working"))
      .isEqualTo("Prevent system sleep while agent is working")
  }

  @Test
  fun sleepPreventionToggleUpdatesAdvancedSetting(@TestDisposable disposable: Disposable) {
    val action = AgentSessionsPreventSleepWhileWorkingToggleAction()
    val advancedSettings = AdvancedSettings.getInstance() as AdvancedSettingsImpl

    advancedSettings.setSetting("agent.workbench.prevent.system.sleep.while.working", true, disposable)
    assertThat(action.isSelected(TestActionEvent.createTestEvent(action))).isTrue()

    runInEdtAndWait {
      action.setSelected(TestActionEvent.createTestEvent(action), false)
    }

    assertThat(AdvancedSettings.getBoolean("agent.workbench.prevent.system.sleep.while.working")).isFalse()
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
    assertThat(sessionsDescriptor())
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"MainToolbarVCSGroup\"/>")
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"ExecutionTargetsToolbarGroup\"/>")
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"NewUiRunWidget\"/>")
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"AIAssistantHubPopupAction\"/>")
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"BuildSolutionBar\"/>")
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"ActiveDeviceGroup\"/>")
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
    assertThat(actionManager.getAction("AgentWorkbenchSessions.CopyThreadIdFromEditorTab"))
      .isNotNull
      .isInstanceOf(AgentSessionsCopyThreadIdFromEditorTabAction::class.java)

    val entries = actionManager.editorTabPopupEntries()

    val archiveIndex = entries.requiredIndex("AgentWorkbenchSessions.ArchiveThreadFromEditorTab")
    val goToSourceIndex = entries.requiredIndex("AgentWorkbenchSessions.GoToSourceProjectFromEditorTab")
    val closeEditorsGroupIndex = entries.requiredIndex("CloseEditorsGroup")
    val copyThreadIdIndex = entries.requiredIndex("AgentWorkbenchSessions.CopyThreadIdFromEditorTab")
    val selectInThreadsIndex = entries.requiredIndex("AgentWorkbenchSessions.SelectThreadInAgentThreads")
    val copyPathsIndex = entries.requiredIndex("CopyPaths")

    assertThat(archiveIndex).isLessThan(goToSourceIndex)
    assertThat(goToSourceIndex).isLessThan(closeEditorsGroupIndex)
    assertThat(closeEditorsGroupIndex).isLessThan(copyThreadIdIndex)
    assertThat(copyThreadIdIndex).isLessThan(selectInThreadsIndex)
    assertThat(selectInThreadsIndex).isLessThan(copyPathsIndex)
    assertThat(entries[closeEditorsGroupIndex - 1]).isEqualTo(SEPARATOR_MARKER)
    assertThat(entries[closeEditorsGroupIndex + 1]).isEqualTo(SEPARATOR_MARKER)
    assertThat(entries.subList(selectInThreadsIndex + 1, copyPathsIndex)).doesNotContain(SEPARATOR_MARKER)
  }

  @Test
  fun archiveThreadFromEditorTabShortcutsMatchPlatformDefaults() {
    val defaultKeymapShortcut = "<keyboard-shortcut keymap=\"" + '$' + "default\" first-keystroke=\"control alt DELETE\"/>"

    assertThat(actionsDescriptor())
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

  private fun actionsDescriptor(): String {
    return descriptorText("intellij.agent.workbench.sessions.actions.xml")
  }

  private fun sessionsDescriptor(): String {
    return descriptorText("intellij.agent.workbench.sessions.xml")
  }

  private fun descriptorText(resourceName: String): String {
    return checkNotNull(javaClass.classLoader.getResource(resourceName)) {
      "Module descriptor $resourceName is missing"
    }.readText()
  }

  companion object {
    private const val SEPARATOR_MARKER = "<separator>"
    private const val INLINE_EDITOR_TAB_SEPARATOR_GROUP_ID = "AgentWorkbenchSessions.EditorTabPopup.SeparatorBeforeCloseActions"
    private const val OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID = "agent.workbench.chat.open.in.dedicated.frame"
  }
}
