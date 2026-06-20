// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui.actions

import com.intellij.agent.workbench.prompt.core.AGENT_PROMPT_INITIAL_TEXT_DATA_KEY
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.buildBuiltInLaunchProfiles
import com.intellij.agent.workbench.sessions.core.providers.effectiveLaunchProfiles
import com.intellij.agent.workbench.sessions.providerItemMonochromeIconWithMode
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.settings.AgentSessionProviderSettingsService
import com.intellij.agent.workbench.sessions.state.AgentSessionLaunchProfileStateService
import com.intellij.icons.AllIcons
import com.intellij.ide.scratch.ScratchUtil
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.CustomizedDataContext
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import org.intellij.plugins.markdown.lang.MarkdownFileType
import javax.swing.Icon

internal const val AGENT_WORKBENCH_OPEN_SCRATCH_AS_PROMPT_ACTION_ID: String = "AgentWorkbenchPrompt.OpenScratchAsPrompt"
internal const val AGENT_WORKBENCH_SCRATCH_PROMPT_CONTEXT_BAR_GROUP_ID: String = "AgentWorkbenchPrompt.ScratchPromptEditorContextBarGroup"

@Suppress("unused")
internal class AgentWorkbenchOpenScratchAsPromptAction(
  private val openPrompt: (AnActionEvent, String) -> Unit,
  private val resolveIcon: (Project) -> Icon = ::resolveScratchPromptActionIcon,
) : DumbAwareAction() {
  constructor() : this(::openGlobalPromptWithInitialText)

  override fun update(e: AnActionEvent) {
    e.presentation.keepPopupOnPerform = KeepPopupOnPerform.Never
    val project = CommonDataKeys.PROJECT.getData(e.dataContext)?.takeIf { project -> !project.isDisposed }
    if (project == null || resolveScratchMarkdownPromptText(e.dataContext) == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    e.presentation.isEnabledAndVisible = true
    e.presentation.icon = resolveIcon(project)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val promptText = resolveScratchMarkdownPromptText(e.dataContext) ?: return
    openPrompt(e, promptText)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

@Suppress("unused")
internal class AgentWorkbenchScratchPromptFloatingToolbarProvider :
  AbstractFloatingToolbarProvider(AGENT_WORKBENCH_SCRATCH_PROMPT_CONTEXT_BAR_GROUP_ID) {
  override suspend fun isApplicableAsync(dataContext: DataContext): Boolean {
    return resolveScratchMarkdownPromptText(dataContext) != null
  }
}

internal fun resolveScratchMarkdownPromptText(dataContext: DataContext): String? {
  CommonDataKeys.PROJECT.getData(dataContext)?.takeIf { project -> !project.isDisposed } ?: return null
  val editor = CommonDataKeys.EDITOR.getData(dataContext) ?: return null
  val document = editor.document
  val file = CommonDataKeys.VIRTUAL_FILE.getData(dataContext)
             ?: FileDocumentManager.getInstance().getFile(document)
             ?: return null
  if (!file.isValid || file.isDirectory || !ScratchUtil.isScratch(file)) {
    return null
  }
  if (!FileTypeManager.getInstance().isFileOfType(file, MarkdownFileType.INSTANCE)) {
    return null
  }

  val promptText = document.immutableCharSequence.toString()
  return promptText.takeIf(String::isNotBlank)
}

private fun openGlobalPromptWithInitialText(e: AnActionEvent, promptText: String) {
  val promptAction = ActionManager.getInstance().getAction(AGENT_WORKBENCH_GLOBAL_PROMPT_ACTION_ID) ?: return
  val dataContext = CustomizedDataContext.withSnapshot(e.dataContext) { sink: DataSink ->
    sink[AGENT_PROMPT_INITIAL_TEXT_DATA_KEY] = promptText
  }
  val promptEvent = AnActionEvent.createEvent(promptAction, dataContext, null, e.place, ActionUiKind.NONE, null)
  promptAction.actionPerformed(promptEvent)
}

private fun resolveScratchPromptActionIcon(project: Project): Icon {
  val providerSettings = service<AgentSessionProviderSettingsService>()
  val providers = AgentSessionProviders.allProviders().filter { provider ->
    provider.supportsPromptLaunch && providerSettings.isProviderEnabled(provider.provider)
  }
  val availabilityService = project.service<AgentSessionProviderAvailabilityService>()
  availabilityService.requestRefresh(providers)
  val menuModel = buildAgentSessionProviderMenuModel(providers, availabilityService.availabilitySnapshot(providers))
  val launchProfileStateService = service<AgentSessionLaunchProfileStateService>()
  val activeItem = resolveActivePromptProfileItem(
    menuModel = menuModel,
    userProfiles = launchProfileStateService.getUserLaunchProfiles(),
    activeProfileId = launchProfileStateService.getDefaultLaunchProfileId(),
  )
  return activeItem?.let(::providerItemMonochromeIconWithMode) ?: AllIcons.Actions.InlayGear
}

private fun resolveActivePromptProfileItem(
  menuModel: AgentSessionProviderMenuModel,
  userProfiles: List<AgentPromptLaunchProfile>,
  activeProfileId: String?,
): AgentSessionProviderMenuItem? {
  val enabledItems = (menuModel.standardItems + menuModel.yoloItems).filter(AgentSessionProviderMenuItem::isEnabled)
  if (enabledItems.isEmpty()) return null

  val profiles = effectiveLaunchProfiles(
    buildBuiltInLaunchProfiles(menuModel) { item -> item.bridge.displayNameFallback },
    userProfiles,
  )
  val activeProfile = profiles.firstOrNull { profile -> activeProfileId == null || profile.id == activeProfileId }
                      ?: profiles.firstOrNull()
                      ?: return enabledItems.firstOrNull()
  return enabledItems.firstOrNull { item ->
    item.bridge.provider.value == activeProfile.providerId && item.mode == activeProfile.launchMode
  } ?: enabledItems.firstOrNull()
}
