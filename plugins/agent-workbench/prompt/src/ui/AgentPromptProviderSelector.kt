// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.prompt.context.dataContextOrNull
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderBridge
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.setToolTipText
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import org.jetbrains.annotations.Nls

internal class AgentPromptProviderSelector(
  private val invocationData: AgentPromptInvocationData,
  private val providerIconLabel: JBLabel,
  private val codexPlanModeCheckBox: JBCheckBox,
  private val providersProvider: () -> List<AgentSessionProviderBridge>,
  private val sessionsMessageResolver: AgentPromptSessionsMessageResolver,
) {
  private var providerEntries: List<ProviderEntry> = emptyList()
  private var providerMenuModel: AgentSessionProviderMenuModel = AgentSessionProviderMenuModel(emptyList(), emptyList())

  var selectedProvider: ProviderEntry? = null
    private set

  val availableProviders: List<AgentSessionProvider>
    get() = providerEntries.map { entry -> entry.bridge.provider }

  fun refresh() {
    val bridges = providersProvider().sortedBy { bridge -> providerPriority(bridge.provider) }
    providerMenuModel = buildAgentSessionProviderMenuModel(bridges)
    providerEntries = bridges.map { bridge ->
      ProviderEntry(
        bridge = bridge,
        displayName = sessionsMessageResolver.resolve(bridge.displayNameKey, bridge) ?: providerDisplayName(bridge.provider),
        isCliAvailable = bridge.isCliAvailable(),
        icon = bridge.icon,
      )
    }

    val currentProviderId = selectedProvider?.bridge?.provider
    selectedProvider = findProviderEntry(currentProviderId)
      ?: findProviderEntry(AgentSessionProvider.CODEX)
      ?: providerEntries.firstOrNull()
    updatePresentation()
  }

  fun selectProvider(provider: AgentSessionProvider?) {
    if (provider == null) {
      return
    }
    selectedProvider = findProviderEntry(provider) ?: selectedProvider
    updatePresentation()
  }

  fun findProviderEntry(provider: AgentSessionProvider?): ProviderEntry? {
    if (provider == null) {
      return null
    }
    return providerEntries.firstOrNull { entry -> entry.bridge.provider == provider }
  }

  fun showChooser(onUnavailable: (@Nls String) -> Unit, onSelected: (ProviderEntry) -> Unit) {
    if (!providerMenuModel.hasEntries()) {
      onUnavailable(AgentPromptBundle.message("popup.error.no.providers"))
      return
    }

    val group = DefaultActionGroup()
    providerMenuModel.standardItems.forEach { item ->
      group.add(createProviderSelectionAction(item, onSelected))
    }
    if (providerMenuModel.yoloItems.isNotEmpty()) {
      if (providerMenuModel.standardItems.isNotEmpty()) {
        group.add(Separator.getInstance())
      }
      val yoloSectionName = sessionsMessageResolver.resolve("toolwindow.action.new.session.section.auto")
        ?: AgentPromptBundle.message("popup.provider.section.auto")
      group.add(Separator.create(yoloSectionName))
      providerMenuModel.yoloItems.forEach { item ->
        group.add(createProviderSelectionAction(item, onSelected))
      }
    }

    val chooserDataContext = invocationData.dataContextOrNull() ?: DataManager.getInstance().getDataContext(providerIconLabel)
    JBPopupFactory.getInstance()
      .createActionGroupPopup(
        null,
        group,
        chooserDataContext,
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
        null,
        Int.MAX_VALUE,
      )
      .showUnderneathOf(providerIconLabel)
  }

  private fun updatePresentation() {
    val provider = selectedProvider
    if (provider == null) {
      providerIconLabel.icon = AllIcons.Toolwindows.ToolWindowMessages
      providerIconLabel.setToolTipText(HtmlChunk.text(AgentPromptBundle.message("popup.provider.selector.tooltip")))
      updateCodexPlanToggleVisibility()
      return
    }

    providerIconLabel.icon = provider.icon
    providerIconLabel.setToolTipText(HtmlChunk.text(provider.displayName))
    updateCodexPlanToggleVisibility()
  }

  private fun updateCodexPlanToggleVisibility() {
    codexPlanModeCheckBox.isVisible = selectedProvider?.bridge?.provider == AgentSessionProvider.CODEX
  }

  private fun createProviderSelectionAction(item: AgentSessionProviderMenuItem, onSelected: (ProviderEntry) -> Unit): AnAction {
    val text = sessionsMessageResolver.resolve(item.labelKey, item.bridge) ?: item.displayNameFallback()
    return object : AnAction(text, null, item.bridge.icon) {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = item.isEnabled
        e.presentation.description = if (item.isEnabled) null else disabledProviderReason(item)
      }

      override fun actionPerformed(e: AnActionEvent) {
        if (!item.isEnabled) {
          return
        }

        val entry = findProviderEntry(item.bridge.provider) ?: return
        selectedProvider = entry
        updatePresentation()
        onSelected(entry)
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }
  }

  private fun AgentSessionProviderMenuItem.displayNameFallback(): @Nls String {
    return findProviderEntry(bridge.provider)?.displayName ?: providerDisplayName(bridge.provider)
  }

  private fun disabledProviderReason(item: AgentSessionProviderMenuItem): @Nls String {
    val reasonKey = item.disabledReasonKey
    if (reasonKey != null) {
      sessionsMessageResolver.resolve(reasonKey, item.bridge)?.let { resolved ->
        return resolved
      }
    }
    return AgentPromptBundle.message("popup.error.provider.unavailable", item.displayNameFallback())
  }
}

private fun providerDisplayName(provider: AgentSessionProvider): String {
  return when (provider) {
    AgentSessionProvider.CODEX -> AgentPromptBundle.message("provider.codex")
    AgentSessionProvider.CLAUDE -> AgentPromptBundle.message("provider.claude")
    else -> provider.value.replaceFirstChar { char ->
      if (char.isLowerCase()) char.titlecase() else char.toString()
    }
  }
}

private fun providerPriority(provider: AgentSessionProvider): Int {
  return when (provider) {
    AgentSessionProvider.CODEX -> 0
    AgentSessionProvider.CLAUDE -> 1
    else -> 2
  }
}
