// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentPromptProviderOption
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.buildAgentSessionProviderMenuModel
import com.intellij.agent.workbench.sessions.core.providers.buildBuiltInLaunchProfiles
import com.intellij.agent.workbench.sessions.core.providers.hasEntries
import com.intellij.agent.workbench.sessions.providerItemIconWithMode
import com.intellij.agent.workbench.sessions.service.AgentSessionProviderAvailabilityService
import com.intellij.agent.workbench.sessions.settings.AgentSessionProviderSettingsService
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls

internal class AgentPromptProviderSelector(
  invocationData: AgentPromptInvocationData,
  private val headerControls: AgentPromptHeaderControls,
  private val providersProvider: () -> List<AgentSessionProviderDescriptor>,
  private val sessionsMessageResolver: AgentPromptSessionsMessageResolver,
  /**
   * Scope that drives the suspending refresh which evaluates each provider's CLI availability off the
   * UI path. Tests that drive the selector synchronously pass `null` and seed the availability cache
   * directly.
   */
  private val asyncRefreshScope: CoroutineScope? = null,
  private val onProviderOptionsChanged: () -> Unit = {},
  private val onProviderSelectionChanged: () -> Unit = {},
) {
  private val providerAvailabilityService = invocationData.project.service<AgentSessionProviderAvailabilityService>()
  private val providerSettingsService = service<AgentSessionProviderSettingsService>()
  private var providerEntries: List<ProviderEntry> = emptyList()
  private var providerMenuModel: AgentSessionProviderMenuModel = AgentSessionProviderMenuModel(emptyList(), emptyList())
  private val selectedOptionIdsByProvider = LinkedHashMap<AgentSessionProvider, LinkedHashSet<String>>()

  var selectedProvider: ProviderEntry? = null
    private set

  var selectedLaunchMode: AgentSessionLaunchMode = AgentSessionLaunchMode.STANDARD
    private set

  val availableProviders: List<AgentSessionProvider>
    get() = providerEntries.map { entry -> entry.bridge.provider }

  fun providerEntries(): List<ProviderEntry> {
    return providerEntries
  }

  fun refresh() {
    val bridges = providerSettingsService.enabledProviders(providersProvider().filter { provider -> provider.supportsPromptLaunch })
    val (resolvedMenuModel, resolvedEntries) = resolveProviderState(bridges, providerAvailabilityService.availabilitySnapshot(bridges))
    applyResolvedState(resolvedMenuModel, resolvedEntries)
    asyncRefreshScope?.launch { refreshProviderAvailability(bridges) }
  }

  /**
   * Re-runs the provider scan using [AgentSessionProviderDescriptor.isCliAvailable]. The lookup may dispatch
   * to background work (EEL probes for known-location candidates), so the result is cached and applied back
   * on the EDT before [updatePresentation] re-renders the popup. No-op when nothing changed compared to the
   * cached paint already on screen.
   */
  private suspend fun refreshProviderAvailability(bridges: List<AgentSessionProviderDescriptor>) {
    val availabilityByProvider = providerAvailabilityService.refreshNow(bridges, force = false)
    val (resolvedMenuModel, resolvedEntries) = resolveProviderState(bridges, availabilityByProvider)
    withContext(Dispatchers.EDT) {
      if (resolvedEntries == providerEntries && resolvedMenuModel == providerMenuModel) return@withContext
      applyResolvedState(resolvedMenuModel, resolvedEntries)
    }
  }

  private fun resolveProviderState(
    bridges: List<AgentSessionProviderDescriptor>,
    availabilityByProvider: Map<AgentSessionProvider, Boolean>,
  ): Pair<AgentSessionProviderMenuModel, List<ProviderEntry>> {
    val resolvedMenuModel = buildAgentSessionProviderMenuModel(bridges, availabilityByProvider)
    val visibleProviders = (resolvedMenuModel.standardItems + resolvedMenuModel.yoloItems)
      .mapTo(LinkedHashSet()) { item -> item.bridge.provider }
    val resolvedEntries = bridges.filter { bridge -> bridge.provider in visibleProviders }.map { bridge ->
      ProviderEntry(
        bridge = bridge,
        displayName = sessionsMessageResolver.resolve(bridge.displayNameKey, bridge) ?: bridge.displayNameFallback,
        isCliAvailable = availabilityByProvider[bridge.provider] == true,
        icon = bridge.icon,
      )
    }
    return resolvedMenuModel to resolvedEntries
  }

  private fun applyResolvedState(
    resolvedMenuModel: AgentSessionProviderMenuModel,
    resolvedEntries: List<ProviderEntry>,
  ) {
    val previousProvider = selectedProvider?.bridge?.provider
    val previousLaunchMode = selectedLaunchMode
    providerMenuModel = resolvedMenuModel
    providerEntries = resolvedEntries
    val activeProviders = providerEntries.mapTo(HashSet()) { entry -> entry.bridge.provider }
    val obsoleteProviders = selectedOptionIdsByProvider.keys.filterNot { provider -> provider in activeProviders }
    obsoleteProviders.forEach(selectedOptionIdsByProvider::remove)
    providerEntries.forEach { entry ->
      val currentSelection = selectedOptionIdsByProvider[entry.bridge.provider] ?: return@forEach
      selectedOptionIdsByProvider[entry.bridge.provider] = sanitizeSelectedOptionIds(entry.bridge, currentSelection)
    }
    val currentProviderId = selectedProvider?.bridge?.provider
    selectedProvider = findProviderEntry(currentProviderId) ?: providerEntries.firstOrNull()
    selectedLaunchMode = normalizeLaunchMode(selectedProvider?.bridge, selectedLaunchMode)
    updatePresentation()
    notifyProviderSelectionChanged(previousProvider, previousLaunchMode)
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

  fun setProviderOptionsVisible(visible: Boolean) {
    headerControls.setProviderOptionsVisible(visible)
  }

  fun providerOptionSelections(): Map<String, Set<String>> {
    return providerEntries
      .asSequence()
      .filter { entry -> entry.bridge.promptOptions.isNotEmpty() }
      .associate { entry -> entry.bridge.provider.value to selectedOptionIds(entry.bridge.provider) }
  }

  fun builtInLaunchProfiles(): List<AgentPromptLaunchProfile> {
    return buildBuiltInLaunchProfiles(providerMenuModel) { item ->
      sessionsMessageResolver.resolve(item.labelKey, item.bridge) ?: item.displayNameFallback()
    }
  }

  fun selectProvider(provider: AgentSessionProvider?, launchMode: AgentSessionLaunchMode? = null) {
    if (provider == null) {
      return
    }
    val previousProvider = selectedProvider?.bridge?.provider
    val previousLaunchMode = selectedLaunchMode
    selectedProvider = findProviderEntry(provider) ?: selectedProvider
    selectedLaunchMode = normalizeLaunchMode(selectedProvider?.bridge, launchMode ?: selectedLaunchMode)
    updatePresentation()
    notifyProviderSelectionChanged(previousProvider, previousLaunchMode)
  }

  fun findProviderEntry(provider: AgentSessionProvider?): ProviderEntry? {
    if (provider == null) {
      return null
    }
    return providerEntries.firstOrNull { entry -> entry.bridge.provider == provider }
  }

  fun findMenuItem(provider: AgentSessionProvider?, launchMode: AgentSessionLaunchMode): AgentSessionProviderMenuItem? {
    if (provider == null) {
      return null
    }
    return providerMenuItemsForMode(launchMode).firstOrNull { item -> item.bridge.provider == provider }
  }

  fun selectedMenuItem(): AgentSessionProviderMenuItem? {
    return findMenuItem(selectedProvider?.bridge?.provider, selectedLaunchMode)
  }

  fun compactBuiltInProfileLabel(profile: AgentPromptLaunchProfile): @Nls String? {
    val item = findMenuItem(AgentSessionProvider.fromOrNull(profile.providerId), profile.launchMode) ?: return null
    return when (profile.launchMode) {
      AgentSessionLaunchMode.STANDARD -> AgentPromptBundle.message("popup.profile.header.standard")
      AgentSessionLaunchMode.YOLO -> compactYoloModeLabel(item)
    }
  }

  fun selectedOptionIds(provider: AgentSessionProvider): Set<String> {
    val entry = findProviderEntry(provider) ?: return emptySet()
    return optionSelectionState(entry.bridge).toSet()
  }

  fun isPlanModeSelected(): Boolean {
    val provider = selectedProvider?.bridge?.provider ?: return false
    return AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE in selectedOptionIds(provider)
  }

  fun setPlanModeSelected(selected: Boolean) {
    val bridge = selectedProvider?.bridge ?: return
    if (bridge.promptOptions.none { option -> option.id == AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE }) {
      return
    }
    val selectedOptionIds = optionSelectionState(bridge)
    val changed = if (selected) {
      selectedOptionIds.add(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
    }
    else {
      selectedOptionIds.remove(AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE)
    }
    if (changed) {
      updateProviderOptionsPresentation()
      onProviderOptionsChanged()
    }
  }

  internal fun buildChooserActionGroup(onSelected: (ProviderEntry) -> Unit): ActionGroup? {
    if (!providerMenuModel.hasEntries()) {
      return null
    }

    val group = DumbAwarePromptActionGroup()
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
    return group
  }

  private fun updatePresentation() {
    updateProviderOptionsPresentation()
  }

  private fun updateProviderOptionsPresentation() {
    val bridge = selectedProvider?.bridge
    val options = bridge?.promptOptions.orEmpty()
    if (bridge == null || options.isEmpty()) {
      headerControls.setProviderOptionActions(emptyList())
      return
    }

    val selectedOptionIds = optionSelectionState(bridge)
    headerControls.setProviderOptionActions(options.map { option -> createProviderOptionAction(bridge, option, selectedOptionIds) })
  }

  private fun createProviderSelectionAction(item: AgentSessionProviderMenuItem, onSelected: (ProviderEntry) -> Unit): AnAction {
    val text = sessionsMessageResolver.resolve(item.labelKey, item.bridge) ?: item.displayNameFallback()
    val icon = providerItemIconWithMode(item)
    return object : DumbAwareAction(text, null, icon) {
      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = item.isEnabled
        e.presentation.description = if (item.isEnabled) null else disabledProviderReason(item)
      }

      override fun actionPerformed(e: AnActionEvent) {
        if (!item.isEnabled) {
          return
        }

        val entry = findProviderEntry(item.bridge.provider) ?: return
        val previousProvider = selectedProvider?.bridge?.provider
        val previousLaunchMode = selectedLaunchMode
        selectedProvider = entry
        selectedLaunchMode = item.mode
        updatePresentation()
        notifyProviderSelectionChanged(previousProvider, previousLaunchMode)
        onSelected(entry)
      }

      override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
    }
  }

  private fun normalizeLaunchMode(
    bridge: AgentSessionProviderDescriptor?,
    mode: AgentSessionLaunchMode,
  ): AgentSessionLaunchMode {
    if (bridge == null || mode in bridge.supportedLaunchModes) {
      return mode
    }
    if (AgentSessionLaunchMode.STANDARD in bridge.supportedLaunchModes) {
      return AgentSessionLaunchMode.STANDARD
    }
    return bridge.supportedLaunchModes.firstOrNull() ?: AgentSessionLaunchMode.STANDARD
  }

  private fun AgentSessionProviderMenuItem.displayNameFallback(): @Nls String {
    return findProviderEntry(bridge.provider)?.displayName ?: bridge.displayNameFallback
  }

  private fun compactYoloModeLabel(item: AgentSessionProviderMenuItem): @Nls String {
    val modeLabelKey = item.bridge.yoloSessionModeLabelKey
    if (modeLabelKey != null) {
      sessionsMessageResolver.resolve(modeLabelKey, item.bridge)?.let { label -> return label }
    }
    return AgentPromptBundle.message("popup.profile.header.yolo")
  }

  private fun providerMenuItemsForMode(launchMode: AgentSessionLaunchMode): List<AgentSessionProviderMenuItem> {
    return when (launchMode) {
      AgentSessionLaunchMode.STANDARD -> providerMenuModel.standardItems
      AgentSessionLaunchMode.YOLO -> providerMenuModel.yoloItems
    }
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

  private fun createProviderOptionAction(
    bridge: AgentSessionProviderDescriptor,
    option: AgentPromptProviderOption,
    selectedOptionIds: LinkedHashSet<String>,
  ): AgentPromptHeaderCheckBoxAction {
    val label = sessionsMessageResolver.resolve(option.labelKey, bridge) ?: option.labelFallback
    return AgentPromptHeaderCheckBoxAction(label, option.id in selectedOptionIds) { selected ->
      val changed = if (selected) {
        selectedOptionIds.add(option.id)
      }
      else {
        selectedOptionIds.remove(option.id)
      }
      if (changed) {
        onProviderOptionsChanged()
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

  private fun notifyProviderSelectionChanged(previousProvider: AgentSessionProvider?, previousLaunchMode: AgentSessionLaunchMode) {
    if (previousProvider != selectedProvider?.bridge?.provider || previousLaunchMode != selectedLaunchMode) {
      onProviderSelectionChanged()
    }
  }
}

private class DumbAwarePromptActionGroup : DefaultActionGroup(), DumbAware
