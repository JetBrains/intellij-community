// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.AgentPromptBundle
import com.intellij.agent.workbench.prompt.context.dataContextOrNull
import com.intellij.agent.workbench.sessions.core.AgentSessionLaunchMode
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.prompt.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.agent.workbench.sessions.core.providers.withYoloModeBadge
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.DialogUtil
import org.jetbrains.annotations.Nls
import javax.swing.JPanel

internal class AgentPromptProviderSelector(
    private val invocationData: AgentPromptInvocationData,
    private val providerIconLabel: JBLabel,
    private val providerOptionsPanel: JPanel,
    private val providersProvider: () -> List<AgentSessionProviderDescriptor>,
    private val sessionsMessageResolver: AgentPromptSessionsMessageResolver,
) {
  private var providerEntries: List<ProviderEntry> = emptyList()
  private var providerMenuModel: AgentSessionProviderMenuModel = AgentSessionProviderMenuModel(emptyList(), emptyList())
  private val selectedOptionIdsByProvider = LinkedHashMap<AgentSessionProvider, LinkedHashSet<String>>()

  var selectedProvider: ProviderEntry? = null
    private set

  var selectedLaunchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD
    private set

  val availableProviders: List<AgentSessionProvider>
    get() = providerEntries.map { entry -> entry.bridge.provider }

  fun refresh() {
    val bridges = providersProvider()
    providerMenuModel = buildAgentSessionProviderMenuModel(bridges)
    providerEntries = bridges.map { bridge ->
      ProviderEntry(
        bridge = bridge,
        displayName = sessionsMessageResolver.resolve(bridge.displayNameKey, bridge) ?: bridge.displayNameFallback,
        isCliAvailable = bridge.isCliAvailable(),
        icon = bridge.icon,
      )
    }
    val activeProviders = providerEntries.mapTo(HashSet()) { entry -> entry.bridge.provider }
    val obsoleteProviders = selectedOptionIdsByProvider.keys.filterNot { provider -> provider in activeProviders }
    obsoleteProviders.forEach(selectedOptionIdsByProvider::remove)
    providerEntries.forEach { entry ->
      val currentSelection = selectedOptionIdsByProvider[entry.bridge.provider] ?: return@forEach
      selectedOptionIdsByProvider[entry.bridge.provider] = sanitizeSelectedOptionIds(entry.bridge, currentSelection)
    }

    val currentProviderId = selectedProvider?.bridge?.provider
    selectedProvider = findProviderEntry(currentProviderId)
                       ?: providerEntries.firstOrNull()
    updatePresentation()
  }

  fun restoreProviderOptionSelections(providerOptionsByProviderId: Map<String, Set<String>>) {
    selectedOptionIdsByProvider.clear()
    providerEntries.forEach { entry ->
      val providerId = entry.bridge.provider.value
      val storedSelection = providerOptionsByProviderId[providerId] ?: return@forEach
      selectedOptionIdsByProvider[entry.bridge.provider] = sanitizeSelectedOptionIds(entry.bridge, storedSelection)
    }
    updateProviderOptionsPresentation()
  }

  fun providerOptionSelections(): Map<String, Set<String>> {
    return providerEntries
      .asSequence()
      .filter { entry -> entry.bridge.promptOptions.isNotEmpty() }
      .associate { entry -> entry.bridge.provider.value to selectedOptionIds(entry.bridge.provider) }
  }

  fun applyLegacyPlanModeSelection(provider: AgentSessionProvider?, enabled: Boolean) {
    val entry = findProviderEntry(provider) ?: return
    if (entry.bridge.promptOptions.none { option -> option.id == AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE }) {
      return
    }

    val selectedOptionIds = optionSelectionState(entry.bridge)
    if (enabled) {
      selectedOptionIds.add(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
    }
    else {
      selectedOptionIds.remove(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
    }

    if (selectedProvider?.bridge?.provider == provider) {
      updateProviderOptionsPresentation()
    }
  }

  fun selectProvider(provider: AgentSessionProvider?) {
    if (provider == null) {
      return
    }
    selectedProvider = findProviderEntry(provider) ?: selectedProvider
    if (launchMode != null) {
      selectedLaunchMode = launchMode
    }
    updatePresentation()
  }

  fun findProviderEntry(provider: AgentSessionProvider?): ProviderEntry? {
    if (provider == null) {
      return null
    }
    return providerEntries.firstOrNull { entry -> entry.bridge.provider == provider }
  }

  fun selectedOptionIds(provider: AgentSessionProvider): Set<String> {
    val entry = findProviderEntry(provider) ?: return emptySet()
    return optionSelectionState(entry.bridge).toSet()
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
      updateProviderOptionsPresentation()
      return
    }

    providerIconLabel.icon = provider.icon
    providerIconLabel.setToolTipText(HtmlChunk.text(provider.displayName))
    updateProviderOptionsPresentation()
  }

  private fun updateProviderOptionsPresentation() {
    providerOptionsPanel.removeAll()
    val bridge = selectedProvider?.bridge
    val options = bridge?.promptOptions.orEmpty()
    if (bridge == null || options.isEmpty()) {
      providerOptionsPanel.isVisible = false
      providerOptionsPanel.revalidate()
      providerOptionsPanel.repaint()
      return
    }

    val selectedOptionIds = optionSelectionState(bridge)
    options.forEach { option ->
      providerOptionsPanel.add(createProviderOptionCheckBox(bridge, option, selectedOptionIds))
    }
    providerOptionsPanel.isVisible = true
    providerOptionsPanel.revalidate()
    providerOptionsPanel.repaint()
  }

  private fun createProviderSelectionAction(item: AgentSessionProviderMenuItem, onSelected: (ProviderEntry) -> Unit): AnAction {
    val text = sessionsMessageResolver.resolve(item.labelKey, item.bridge) ?: item.displayNameFallback()
    val icon = getIcon(item)
    return object : AnAction(text, null, icon) {
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
        selectedLaunchMode = item.mode
        updatePresentation()
        onSelected(entry)
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }
  }

  private fun getIcon(item: AgentSessionProviderMenuItem): Icon = getIcon(item.bridge.icon, item.mode)

  private fun getIcon(baseIcon: Icon, mode: AgentSessionLaunchMode): Icon {
    if (mode == AgentSessionLaunchMode.YOLO) {
      return withYoloModeBadge(baseIcon)
    }
    return baseIcon
  }

  private fun AgentSessionProviderMenuItem.displayNameFallback(): @Nls String {
    return findProviderEntry(bridge.provider)?.displayName ?: bridge.displayNameFallback
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

  private fun createProviderOptionCheckBox(
      bridge: AgentSessionProviderDescriptor,
      option: AgentPromptProviderOption,
      selectedOptionIds: LinkedHashSet<String>,
  ): JBCheckBox {
    val label = sessionsMessageResolver.resolve(option.labelKey, bridge) ?: option.labelFallback
    return JBCheckBox(label, option.id in selectedOptionIds).apply {
      isFocusable = false
      addActionListener {
        if (isSelected) {
          selectedOptionIds.add(option.id)
        }
        else {
          selectedOptionIds.remove(option.id)
        }
      }
    }
  }

  private fun optionSelectionState(bridge: AgentSessionProviderDescriptor): LinkedHashSet<String> {
    return selectedOptionIdsByProvider.getOrPut(bridge.provider) {
      bridge.promptOptions
        .asSequence()
        .filter(AgentPromptProviderOption::defaultSelected)
        .mapTo(LinkedHashSet()) { option -> option.id }
    }
  }

  private fun sanitizeSelectedOptionIds(bridge: AgentSessionProviderDescriptor, optionIds: Set<String>): LinkedHashSet<String> {
    val validIds = bridge.promptOptions.mapTo(HashSet()) { option -> option.id }
    return optionIds
      .asSequence()
      .filter { optionId -> optionId in validIds }
      .toCollection(LinkedHashSet())
  }

  private fun AgentSessionProviderDescriptor.hasPromptOption(optionId: String): Boolean {
    return promptOptions.any { option -> option.id == optionId }
  }
}
