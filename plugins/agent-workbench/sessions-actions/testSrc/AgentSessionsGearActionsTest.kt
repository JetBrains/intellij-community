// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.common.AgentWorkbenchActionIds
import com.intellij.agent.workbench.sessions.actions.AgentSessionsActivateWithProjectShortcutAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsConfigureProvidersAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsCopyThreadIdFromEditorTabAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsDedicatedFrameToggleAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabArchiveThreadAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabNewThreadAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsEditorTabRenameThreadAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsGoToSourceProjectFromEditorTabAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsGoToSourceProjectFromToolbarAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsMainToolbarNewThreadAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsOpenDedicatedFrameAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsPreventSleepWhileWorkingToggleAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsRefreshAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsSelectThreadInToolWindowAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsShowArchivedThreadsAction
import com.intellij.agent.workbench.sessions.actions.AgentSessionsSwitchSourceAndChatAction
import com.intellij.agent.workbench.sessions.actions.DumbAwareDefaultActionGroup
import com.intellij.agent.workbench.sessions.core.settings.AgentWorkbenchSettings
import com.intellij.agent.workbench.sessions.frame.AgentChatOpenModeSettings
import com.intellij.agent.workbench.sessions.model.AgentSessionThreadViewMode
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsImpl
import com.intellij.openapi.project.DumbService
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.runInEdtAndWait
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

@TestApplication
@Timeout(value = 2, unit = TimeUnit.MINUTES)
class AgentSessionsGearActionsTest {
  @BeforeEach
  fun setUp() {
    AgentWorkbenchSettings.getInstance().loadState(AgentWorkbenchSettings.SettingsState())
  }

  @AfterEach
  fun tearDown() {
    AgentWorkbenchSettings.getInstance().loadState(AgentWorkbenchSettings.SettingsState())
  }

  @Test
  fun gearActionsContainOpenFileToggleAndRefresh() {
    val actionManager = ActionManager.getInstance()
    val entries = actionManager.childActionEntries("AgentWorkbenchSessions.ToolWindow.GearActions")

    assertThat(entries).containsSubsequence(
      "OpenFile",
      "AgentWorkbenchSessions.ShowArchivedThreads",
      "AgentWorkbenchSessions.Refresh",
      "AgentWorkbenchSessions.ConfigureProviders",
      ACTION_SEPARATOR_MARKER,
      "AgentWorkbenchSessions.ToggleDedicatedFrame",
      "AgentWorkbenchSessions.TogglePreventSleepWhileWorking",
    )
    assertThat(entries).doesNotContain("AgentWorkbenchSessions.OpenDedicatedFrame")
    assertThat(actionManager.getAction("AgentWorkbenchSessions.ConfigureProviders"))
      .isNotNull
      .isInstanceOf(AgentSessionsConfigureProvidersAction::class.java)
  }

  @Test
  fun showArchivedThreadsActionSwitchesThreadViewAndLoadsArchivedSessions() {
    var mode = AgentSessionThreadViewMode.ACTIVE
    var loadInvocations = 0
    val action = AgentSessionsShowArchivedThreadsAction(
      viewMode = { mode },
      setViewMode = { mode = it },
      ensureArchivedSessionsLoaded = { loadInvocations++ },
    )
    val event = TestActionEvent.createTestEvent(action)

    action.update(event)
    assertThat(event.presentation.isVisible).isTrue()

    action.actionPerformed(event)

    assertThat(mode).isEqualTo(AgentSessionThreadViewMode.ARCHIVED)
    assertThat(loadInvocations).isEqualTo(1)

    action.update(event)
    assertThat(event.presentation.isVisible).isFalse()
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
  fun toggleActionUpdatesAgentWorkbenchSetting(@TestDisposable disposable: Disposable) {
    val actionManager = ActionManager.getInstance()
    val action = AgentSessionsDedicatedFrameToggleAction()
    val advancedSettings = AdvancedSettings.getInstance() as AdvancedSettingsImpl

    assertThat(sessionsDescriptor())
      .contains("<advancedSetting")
      .contains("id=\"agent.workbench.chat.open.in.dedicated.frame\"")
      .contains("visible=\"false\"")
    assertThat(actionManager.getAction("AgentWorkbenchSessions.ToggleDedicatedFrame"))
      .isNotNull
      .isInstanceOf(AgentSessionsDedicatedFrameToggleAction::class.java)

    advancedSettings.setSetting(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID, false, disposable)
    AgentChatOpenModeSettings.setOpenInDedicatedFrame(true)
    val event = TestActionEvent.createTestEvent(action)
    assertThat(action.isSelected(event)).isTrue()
    assertThat(AdvancedSettings.getBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID)).isTrue()

    runInEdtAndWait {
      action.setSelected(event, false)
    }

    assertThat(AgentChatOpenModeSettings.openInDedicatedFrame()).isFalse()
    assertThat(AgentWorkbenchSettings.getInstance().openInDedicatedFrame).isFalse()
    assertThat(AgentWorkbenchSettings.getInstance().openInDedicatedFrameOverride).isFalse()
    assertThat(AdvancedSettings.getBoolean(OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID)).isTrue()

    runInEdtAndWait {
      action.setSelected(event, true)
    }

    assertThat(AgentChatOpenModeSettings.openInDedicatedFrame()).isTrue()
    assertThat(AgentWorkbenchSettings.getInstance().openInDedicatedFrame).isTrue()
    assertThat(AgentWorkbenchSettings.getInstance().openInDedicatedFrameOverride).isNull()
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
    val activityExclusion = "<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" " +
                            "id=\"AgentWorkbenchSessions.MainToolbar.Activity\"/>"

    assertThat(sessionsDescriptor())
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"MainToolbarVCSGroup\"/>")
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"ExecutionTargetsToolbarGroup\"/>")
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"NewUiRunWidget\"/>")
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"AIAssistantHubPopupAction\"/>")
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"BuildSolutionBar\"/>")
      .contains("<projectFrameActionExclusion frameType=\"AGENT_DEDICATED\" place=\"MainToolbar\" id=\"ActiveDeviceGroup\"/>")
      .doesNotContain(activityExclusion)
      .doesNotContain("actionConfigurationCustomizer")
  }

  @Test
  fun dedicatedFrameRegistersMainToolbarSourceProjectAction() {
    val actionManager = ActionManager.getInstance()

    assertThat(actionManager.getAction("AgentWorkbenchSessions.GoToSourceProjectFromToolbar"))
      .isNotNull
      .isInstanceOf(AgentSessionsGoToSourceProjectFromToolbarAction::class.java)

    val entries = actionManager.childActionIds("MainToolbarRight")
    val goToSourceIndex = entries.requiredIndex("AgentWorkbenchSessions.GoToSourceProjectFromToolbar")
    val runWidgetIndex = entries.requiredIndex("NewUiRunWidget")
    assertThat(goToSourceIndex).isLessThan(runWidgetIndex)
  }

  @Test
  fun mainToolbarNewThreadActionIsRegisteredAfterRunWidget() {
    val actionManager = ActionManager.getInstance()
    val newThreadActionId = AgentWorkbenchActionIds.Sessions.MainToolbar.NEW_THREAD

    assertThat(actionManager.getAction(newThreadActionId))
      .isNotNull
      .isInstanceOf(AgentSessionsMainToolbarNewThreadAction::class.java)
    assertThat(actionManager.getAction(newThreadActionId)?.actionUpdateThread)
      .isEqualTo(ActionUpdateThread.BGT)

    val entries = actionManager.childActionIds("MainToolbarRight")
    val runWidgetIndex = entries.requiredIndex("NewUiRunWidget")
    val newThreadIndex = entries.requiredIndex(newThreadActionId)
    assertThat(newThreadIndex).isGreaterThan(runWidgetIndex)
    assertThat(entries.count { it == newThreadActionId }).isEqualTo(1)
  }

  @Test
  fun switchSourceAndChatActionIsRegisteredWithoutDefaultShortcut() {
    val actionManager = ActionManager.getInstance()
    val actionId = AgentWorkbenchActionIds.Sessions.SWITCH_SOURCE_AND_CHAT

    assertThat(actionManager.getAction(actionId))
      .isNotNull
      .isInstanceOf(AgentSessionsSwitchSourceAndChatAction::class.java)
    assertThat(actionManager.getAction(actionId)?.actionUpdateThread)
      .isEqualTo(ActionUpdateThread.BGT)

    val entries = actionManager.childActionIds("OpenProjectWindows")
    val openFrameIndex = entries.requiredIndex(AgentWorkbenchActionIds.Sessions.OPEN_DEDICATED_FRAME)
    val switchIndex = entries.requiredIndex(actionId)
    assertThat(switchIndex).isGreaterThan(openFrameIndex)
    val actionDescriptor = actionsDescriptor()
      .substringAfter("id=\"AgentWorkbenchSessions.SwitchSourceAndChat\"")
      .substringBefore("</action>")
    assertThat(actionDescriptor).doesNotContain("keyboard-shortcut")
  }

  @Test
  fun editorTabActionsAreRegisteredInEditorTabPopupMenu() {
    val actionManager = ActionManager.getInstance()

    assertThat(actionManager.getAction("AgentWorkbenchSessions.SelectThreadInAgentThreads"))
      .isNotNull
      .isInstanceOf(AgentSessionsSelectThreadInToolWindowAction::class.java)
    assertThat(actionManager.getAction("AgentWorkbenchSessions.RenameThreadFromEditorTab"))
      .isNotNull
      .isInstanceOf(AgentSessionsEditorTabRenameThreadAction::class.java)
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

    val renameIndex = entries.requiredIndex("AgentWorkbenchSessions.RenameThreadFromEditorTab")
    val archiveIndex = entries.requiredIndex("AgentWorkbenchSessions.ArchiveThreadFromEditorTab")
    val goToSourceIndex = entries.requiredIndex("AgentWorkbenchSessions.GoToSourceProjectFromEditorTab")
    val closeEditorsGroupIndex = entries.requiredIndex("CloseEditorsGroup")
    val copyThreadIdIndex = entries.requiredIndex("AgentWorkbenchSessions.CopyThreadIdFromEditorTab")
    val selectInThreadsIndex = entries.requiredIndex("AgentWorkbenchSessions.SelectThreadInAgentThreads")
    val copyPathsIndex = entries.requiredIndex("CopyPaths")

    assertThat(archiveIndex).isLessThan(goToSourceIndex)
    assertThat(goToSourceIndex).isLessThan(closeEditorsGroupIndex)
    assertThat(closeEditorsGroupIndex).isLessThan(renameIndex)
    assertThat(renameIndex).isLessThan(copyThreadIdIndex)
    assertThat(copyThreadIdIndex).isLessThan(selectInThreadsIndex)
    assertThat(selectInThreadsIndex).isLessThan(copyPathsIndex)
    assertThat(entries[closeEditorsGroupIndex - 1]).isEqualTo(ACTION_SEPARATOR_MARKER)
    assertThat(entries[closeEditorsGroupIndex + 1]).isEqualTo(ACTION_SEPARATOR_MARKER)
    assertThat(entries.subList(renameIndex + 1, copyPathsIndex)).doesNotContain(ACTION_SEPARATOR_MARKER)
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
  fun editorTabNewThreadActionIsRegisteredInActionSystem() {
    val actionManager = ActionManager.getInstance()
    val actionId = AgentWorkbenchActionIds.Sessions.EditorTab.NEW_THREAD

    assertThat(actionManager.getAction(actionId))
      .isNotNull
      .isInstanceOf(AgentSessionsEditorTabNewThreadAction::class.java)
    assertThat(actionManager.getAction(actionId)?.templatePresentation?.icon)
      .isEqualTo(AllIcons.General.Add)
    assertThat(actionManager.getAction(actionId)?.actionUpdateThread)
      .isEqualTo(ActionUpdateThread.BGT)
    assertThat(actionManager.getAction("EditorTabsToolbarActions"))
      .isNotNull
    assertThat(actionManager.childActionIds("EditorTabsToolbarActions"))
      .contains(actionId)
    assertThat(actionManager.childActionIds("EditorTabsToolbarActions").count { it == actionId })
      .isEqualTo(1)
    assertThat(actionManager.childActionIds("EditorTabActionGroup"))
      .doesNotContain(actionId)
  }

  @Test
  fun aggregateAgentWorkbenchGroupsAreDumbAware() {
    val actionManager = ActionManager.getInstance()

    listOf(
      "AgentWorkbenchSessions.ToolWindow.GearActions",
      "AgentWorkbenchSessions.TreePopup",
      EDITOR_TAB_POPUP_SEPARATOR_BEFORE_CLOSE_ACTIONS_ID,
    ).forEach { groupId ->
      val group = actionManager.getAction(groupId)

      assertThat(group)
        .withFailMessage("Action group '%s' is not registered", groupId)
        .isInstanceOf(DumbAwareDefaultActionGroup::class.java)
      assertThat(DumbService.isDumbAware(group)).isTrue()
    }
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
    private const val OPEN_CHAT_IN_DEDICATED_FRAME_SETTING_ID = "agent.workbench.chat.open.in.dedicated.frame"
  }
}
