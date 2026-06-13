// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-task-cost-profiles.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptPlanEffortMode
import com.intellij.agent.workbench.prompt.core.AgentPromptPlanEffortModeKind
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.prompt.ui.context.dataContextOrNull
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.generationSettingsForPlanEffort
import com.intellij.agent.workbench.sessions.providerItemMonochromeIconWithMode
import com.intellij.agent.workbench.sessions.setLaunchProfileIcon
import com.intellij.icons.AllIcons
import com.intellij.ide.DataManager
import com.intellij.ide.setToolTipText
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.ActionLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import javax.swing.Icon
import javax.swing.JPanel
import kotlin.time.Duration.Companion.seconds

private val MODEL_CATALOG_REFRESH_STATUS_DELAY = 3.seconds

internal class AgentPromptGenerationSettingsController(
  private val invocationData: AgentPromptInvocationData,
  private val providerSelector: AgentPromptProviderSelector,
  private val generationSettingsPanel: JPanel,
  private val profileAction: AgentPromptToolbarProfileAction? = null,
  private val launchProfileLink: ActionLink = ActionLink(AgentPromptBundle.message("popup.profile.default")),
  private val modelSelectorLink: ActionLink,
  private val reasoningEffortLink: ActionLink,
  private val planReasoningEffortLink: ActionLink = ActionLink(AgentPromptBundle.message("popup.generation.plan.reasoning.same")),
  private val modelCatalogScope: CoroutineScope,
  private val modelCatalogService: AgentPromptGenerationModelCatalogService = invocationData.project.service(),
  private val launcherProvider: () -> AgentPromptLauncherBridge?,
  private val onDefaultSaved: (String) -> Unit,
  private val manageProfilesDialogRunner: (() -> Unit) -> Unit = { showDialog ->
    ApplicationManager.getApplication().invokeLater { showDialog() }
  },
) {
  private val transientSettingsByProviderId = LinkedHashMap<String, AgentPromptGenerationSettings>()
  private val userProfilesById = LinkedHashMap<String, AgentPromptLaunchProfile>()
  private val modelCatalogsByProviderId = LinkedHashMap<String, ModelCatalogState>()
  private var generationControlsVisible = true
  private var activeProfileId: String? = null
  private var defaultProfileId: String? = null
  private var selectedPlanEffortMode: AgentPromptPlanEffortMode = AgentPromptPlanEffortMode.SAME_AS_NORMAL
  private var activeModelPopup: JBPopup? = null
  private var activeModelPopupProviderId: String? = null

  init {
    if (profileAction == null) {
      launchProfileLink.addActionListener { showLaunchProfilePopup() }
    }
    profileAction?.setActionGroupProvider(::createLaunchProfileActionGroup)
    modelSelectorLink.addActionListener { showModelPopup() }
    reasoningEffortLink.addActionListener { showReasoningEffortPopup() }
    planReasoningEffortLink.addActionListener { showPlanReasoningEffortPopup() }
  }

  fun restoreLaunchProfiles(preferences: AgentPromptLauncherBridge.ProviderPreferences) {
    userProfilesById.clear()
    preferences.launchProfiles.forEach { profile -> userProfilesById[profile.id] = profile }
    defaultProfileId = preferences.activeLaunchProfileId
    activeProfileId = defaultProfileId
    val activeProfile = findProfile(activeProfileId)
    if (activeProfile != null) {
      if (!applyProfile(activeProfile)) {
        activeProfileId = null
      }
    }
    refreshPresentation()
  }

  fun refreshSelectedProviderModels() {
    providerSelector.selectedProvider?.let(::requestModelCatalogRefresh)
    refreshPresentation()
  }

  fun setGenerationControlsVisible(visible: Boolean) {
    generationControlsVisible = visible
    refreshPresentation()
  }

  fun currentSettings(): AgentPromptGenerationSettings {
    val provider = providerSelector.selectedProvider ?: return AgentPromptGenerationSettings.AUTO
    val providerId = provider.bridge.provider.value
    val configuredSettings = transientSettingsByProviderId[providerId]
                             ?: AgentPromptGenerationSettings.AUTO
    val sanitizedSettings = provider.bridge.sanitizeGenerationSettings(configuredSettings)
    return sanitizeSettingsForLoadedModelCatalog(providerId, sanitizedSettings)
  }

  fun currentGenerationModelCatalog(): List<AgentPromptGenerationModel> {
    val provider = providerSelector.selectedProvider ?: return emptyList()
    return loadedModelCatalog(provider.bridge.provider.value).orEmpty()
  }

  fun currentLaunchSettings(): AgentPromptGenerationSettings {
    val currentSettings = currentSettings()
    return generationSettingsForPlanEffort(
      generationSettings = currentSettings,
      planEffort = currentPlanEffortMode(currentSettings),
      startInPlanMode = providerSelector.isPlanModeSelected(),
    )
  }

  fun refreshPresentation() {
    val selectedProvider = providerSelector.selectedProvider
    val showGenerationControls = generationControlsVisible
    val modelCatalog = selectedProvider?.bridge?.provider?.value?.let(::loadedModelCatalog)
    val modelSelectionAvailable = selectedProvider?.bridge?.supportsGenerationModelSelection == true
    val currentSettings = currentSettings()
    val reasoningEfforts = availableReasoningEfforts(currentSettings, modelCatalog)
    val currentPlanEffortMode = sanitizePlanEffortMode(selectedPlanEffortMode, reasoningEfforts)
    selectedPlanEffortMode = currentPlanEffortMode
    val reasoningEffortAvailable = reasoningEfforts.isNotEmpty()
    val hasPlanModeOption = selectedProvider?.bridge?.promptOptions?.any { option ->
      option.id == AGENT_PROMPT_PROVIDER_OPTION_PLAN_MODE
    } == true
    val planEffortAvailable = reasoningEffortAvailable && providerSelector.isPlanModeSelected() && hasPlanModeOption
    generationSettingsPanel.isVisible = showGenerationControls
    launchProfileLink.isVisible = showGenerationControls
    launchProfileLink.isEnabled = showGenerationControls
    val profile = findProfile(activeProfileId)
    val profileText = launchProfileText(profile, isActiveProfileModified())
    val profileTooltip = AgentPromptBundle.message("popup.profile.tooltip")
    profileAction?.setPresentation(
      text = profileText,
      description = profileTooltip,
      icon = profileIcon(profile),
      visible = showGenerationControls,
      enabled = showGenerationControls,
    )
    modelSelectorLink.isVisible = showGenerationControls && modelSelectionAvailable
    modelSelectorLink.isEnabled = showGenerationControls && modelSelectionAvailable
    reasoningEffortLink.isVisible = showGenerationControls
    reasoningEffortLink.isEnabled = showGenerationControls && reasoningEffortAvailable
    planReasoningEffortLink.isVisible = showGenerationControls && planEffortAvailable
    planReasoningEffortLink.isEnabled = showGenerationControls && planEffortAvailable
    if (showGenerationControls) {
      launchProfileLink.text = profileText
      launchProfileLink.setToolTipText(HtmlChunk.text(profileTooltip))
      launchProfileLink.accessibleContext.accessibleName = AgentPromptBundle.message("popup.profile.accessible.name") + ": " + profileText
      modelSelectorLink.text = modelText(currentSettings.modelId, modelCatalog.orEmpty())
      modelSelectorLink.setToolTipText(HtmlChunk.text(AgentPromptBundle.message("popup.generation.model.tooltip")))
      modelSelectorLink.accessibleContext.accessibleName = modelSelectorLink.text
      reasoningEffortLink.text = reasoningEffortText(currentSettings.reasoningEffort)
      reasoningEffortLink.setToolTipText(HtmlChunk.text(reasoningEffortTooltipText(reasoningEffortAvailable)))
      reasoningEffortLink.accessibleContext.accessibleName = reasoningEffortLink.text
      planReasoningEffortLink.text = planReasoningEffortText(currentPlanEffortMode)
      planReasoningEffortLink.setToolTipText(HtmlChunk.text(AgentPromptBundle.message("popup.generation.plan.reasoning.tooltip")))
      planReasoningEffortLink.accessibleContext.accessibleName =
        AgentPromptBundle.message("popup.generation.plan.reasoning.accessible.name") + ": " + planReasoningEffortLink.text
    }
    generationSettingsPanel.revalidate()
    generationSettingsPanel.repaint()
  }

  private fun requestModelCatalogRefresh(selectedProvider: ProviderEntry) {
    if (!selectedProvider.bridge.supportsGenerationModelSelection) {
      return
    }
    val providerId = selectedProvider.bridge.provider.value
    when (modelCatalogsByProviderId[providerId]) {
      ModelCatalogState.Loading,
      is ModelCatalogState.Refreshing,
        -> return
      is ModelCatalogState.Loaded,
      is ModelCatalogState.RefreshFailed,
      ModelCatalogState.Failed,
      null,
        -> Unit
    }

    val cachedModels = loadedModelCatalog(providerId)
    modelCatalogsByProviderId[providerId] = if (cachedModels == null) {
      ModelCatalogState.Loading
    }
    else {
      ModelCatalogState.Loaded(cachedModels)
    }
    val refresh = modelCatalogService.requestRefresh(selectedProvider.bridge, invocationData.project)
    if (cachedModels != null) {
      modelCatalogScope.launch {
        delay(MODEL_CATALOG_REFRESH_STATUS_DELAY)
        withContext(Dispatchers.EDT) {
          if (!refresh.isCompleted && modelCatalogsByProviderId[providerId] == ModelCatalogState.Loaded(cachedModels)) {
            modelCatalogsByProviderId[providerId] = ModelCatalogState.Refreshing(cachedModels)
            refreshPresentation()
            refreshModelPopupIfOpen(providerId)
          }
        }
      }
    }
    modelCatalogScope.launch {
      val result = runCatching { refresh.await() }
      withContext(Dispatchers.EDT) {
        modelCatalogsByProviderId[providerId] = result.fold(
          onSuccess = { models ->
            ModelCatalogState.Loaded(models)
          },
          onFailure = {
            val fallbackModels = modelCatalogService.cachedCatalog(providerId) ?: cachedModels
            if (fallbackModels == null) {
              ModelCatalogState.Failed
            }
            else {
              ModelCatalogState.RefreshFailed(fallbackModels)
            }
          },
        )
        refreshPresentation()
        refreshModelPopupIfOpen(providerId)
      }
    }
  }

  private fun modelCatalogState(providerId: String): ModelCatalogState? {
    return modelCatalogsByProviderId[providerId]
           ?: modelCatalogService.cachedCatalog(providerId)?.let { models -> ModelCatalogState.Loaded(models) }
  }

  private fun loadedModelCatalog(providerId: String): List<AgentPromptGenerationModel>? {
    return modelCatalogState(providerId)?.modelsOrNull()
  }

  private fun sanitizeSettingsForLoadedModelCatalog(
    providerId: String,
    settings: AgentPromptGenerationSettings,
  ): AgentPromptGenerationSettings {
    val models = loadedModelCatalog(providerId) ?: return settings
    val selectedModel = settings.modelId?.let { modelId -> models.firstOrNull { model -> model.id == modelId } }
    val modelId = selectedModel?.id
    val supportedEfforts = selectedModel
                             ?.supportedReasoningEfforts
                             ?.takeIf { efforts -> efforts.isNotEmpty() }
                           ?: models.catalogReasoningEfforts()
    val reasoningEffort = if (supportedEfforts != null &&
                              settings.reasoningEffort != AgentPromptReasoningEffort.AUTO &&
                              settings.reasoningEffort !in supportedEfforts) {
      AgentPromptReasoningEffort.AUTO
    }
    else {
      settings.reasoningEffort
    }
    val planReasoningEffort = if (supportedEfforts != null &&
                                  settings.planReasoningEffort != null &&
                                  settings.planReasoningEffort != AgentPromptReasoningEffort.AUTO &&
                                  settings.planReasoningEffort !in supportedEfforts) {
      AgentPromptReasoningEffort.AUTO
    }
    else {
      settings.planReasoningEffort
    }
    return settings.copy(modelId = modelId, reasoningEffort = reasoningEffort, planReasoningEffort = planReasoningEffort)
  }

  private fun availableReasoningEfforts(
    settings: AgentPromptGenerationSettings,
    models: List<AgentPromptGenerationModel>?,
  ): Set<AgentPromptReasoningEffort> {
    val modelEfforts = settings.modelId
      ?.let { modelId -> models?.firstOrNull { model -> model.id == modelId } }
      ?.supportedReasoningEfforts
      ?.takeIf { efforts -> efforts.isNotEmpty() }
    return modelEfforts
           ?: models?.catalogReasoningEfforts()
           ?: providerSelector.selectedProvider?.bridge?.supportedReasoningEfforts.orEmpty()
  }

  private fun currentPlanEffortMode(settings: AgentPromptGenerationSettings): AgentPromptPlanEffortMode {
    val providerId = providerSelector.selectedProvider?.bridge?.provider?.value ?: return AgentPromptPlanEffortMode.PROVIDER_DEFAULT
    return sanitizePlanEffortMode(
      planEffort = selectedPlanEffortMode,
      supportedEfforts = availableReasoningEfforts(settings, loadedModelCatalog(providerId)),
    )
  }

  private fun sanitizePlanEffortMode(
    planEffort: AgentPromptPlanEffortMode,
    supportedEfforts: Set<AgentPromptReasoningEffort>,
  ): AgentPromptPlanEffortMode {
    if (planEffort.kind != AgentPromptPlanEffortModeKind.EXPLICIT) {
      return planEffort
    }
    val explicitEffort = planEffort.explicitEffort ?: return AgentPromptPlanEffortMode.PROVIDER_DEFAULT
    return if (explicitEffort in supportedEfforts) planEffort else AgentPromptPlanEffortMode.PROVIDER_DEFAULT
  }

  private fun showModelPopup() {
    val selectedProvider = providerSelector.selectedProvider ?: return
    if (!selectedProvider.bridge.supportsGenerationModelSelection) {
      return
    }
    val providerId = selectedProvider.bridge.provider.value
    requestModelCatalogRefresh(selectedProvider)
    showModelPopup(providerId)
  }

  private fun showModelPopup(providerId: String) {
    val selectedProvider = providerSelector.selectedProvider ?: return
    if (selectedProvider.bridge.provider.value != providerId || !selectedProvider.bridge.supportsGenerationModelSelection) {
      return
    }

    val group = createModelActionGroup(providerId, modelCatalogState(providerId))

    val popup = JBPopupFactory.getInstance()
      .createActionGroupPopup(
        null,
        group,
        invocationData.dataContextOrNull() ?: DataManager.getInstance().getDataContext(modelSelectorLink),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
        null,
        Int.MAX_VALUE,
      )
    activeModelPopup = popup
    activeModelPopupProviderId = providerId
    popup.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        if (activeModelPopup === popup) {
          activeModelPopup = null
          activeModelPopupProviderId = null
        }
      }
    })
    popup.showUnderneathOf(modelSelectorLink)
  }

  private fun refreshModelPopupIfOpen(providerId: String) {
    val popup = activeModelPopup ?: return
    if (activeModelPopupProviderId != providerId || !popup.isVisible) {
      return
    }

    popup.cancel()
    showModelPopup(providerId)
  }

  private fun showReasoningEffortPopup() {
    val selectedProvider = providerSelector.selectedProvider ?: return
    val providerId = selectedProvider.bridge.provider.value
    val modelCatalog = loadedModelCatalog(providerId)
    val supportedEfforts = availableReasoningEfforts(currentSettings(), modelCatalog)
    if (supportedEfforts.isEmpty()) {
      return
    }

    val group = createReasoningEffortActionGroup(supportedEfforts)

    JBPopupFactory.getInstance()
      .createActionGroupPopup(
        null,
        group,
        invocationData.dataContextOrNull() ?: DataManager.getInstance().getDataContext(reasoningEffortLink),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
        null,
        Int.MAX_VALUE,
      )
      .showUnderneathOf(reasoningEffortLink)
  }

  private fun showPlanReasoningEffortPopup() {
    val selectedProvider = providerSelector.selectedProvider ?: return
    val providerId = selectedProvider.bridge.provider.value
    val supportedEfforts = availableReasoningEfforts(currentSettings(), loadedModelCatalog(providerId))
    if (supportedEfforts.isEmpty()) {
      return
    }

    JBPopupFactory.getInstance()
      .createActionGroupPopup(
        null,
        createPlanReasoningEffortActionGroup(supportedEfforts),
        invocationData.dataContextOrNull() ?: DataManager.getInstance().getDataContext(planReasoningEffortLink),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
        null,
        Int.MAX_VALUE,
      )
      .showUnderneathOf(planReasoningEffortLink)
  }

  private fun showLaunchProfilePopup() {
    JBPopupFactory.getInstance()
      .createActionGroupPopup(
        null,
        createLaunchProfileActionGroup(),
        invocationData.dataContextOrNull() ?: DataManager.getInstance().getDataContext(launchProfileLink),
        JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
        true,
        null,
        Int.MAX_VALUE,
      )
      .showUnderneathOf(launchProfileLink)
  }

  private fun selectModel(modelId: String?) {
    val providerId = providerSelector.selectedProvider?.bridge?.provider?.value ?: return
    val currentSettings = currentSettings()
    transientSettingsByProviderId[providerId] = sanitizeSettingsForLoadedModelCatalog(
      providerId = providerId,
      settings = currentSettings.copy(modelId = modelId),
    )
    refreshPresentation()
  }

  private fun selectReasoningEffort(effort: AgentPromptReasoningEffort) {
    val providerId = providerSelector.selectedProvider?.bridge?.provider?.value ?: return
    val currentSettings = currentSettings()
    transientSettingsByProviderId[providerId] = currentSettings.copy(reasoningEffort = effort)
    refreshPresentation()
  }

  private fun selectPlanReasoningEffort(planEffort: AgentPromptPlanEffortMode) {
    selectedPlanEffortMode = planEffort
    refreshPresentation()
  }

  @TestOnly
  internal fun createModelActionGroupForTest(loadIfNeeded: Boolean = false): DefaultActionGroup? {
    val selectedProvider = providerSelector.selectedProvider ?: return null
    if (!selectedProvider.bridge.supportsGenerationModelSelection) {
      return null
    }
    val providerId = selectedProvider.bridge.provider.value
    if (loadIfNeeded) {
      requestModelCatalogRefresh(selectedProvider)
    }
    return createModelActionGroup(providerId, modelCatalogState(providerId))
  }

  @TestOnly
  internal fun createReasoningEffortActionGroupForTest(): DefaultActionGroup? {
    val selectedProvider = providerSelector.selectedProvider ?: return null
    val providerId = selectedProvider.bridge.provider.value
    val modelCatalog = loadedModelCatalog(providerId)
    val supportedEfforts = availableReasoningEfforts(currentSettings(), modelCatalog)
    if (supportedEfforts.isEmpty()) {
      return null
    }
    return createReasoningEffortActionGroup(supportedEfforts)
  }

  @TestOnly
  internal fun createPlanReasoningEffortActionGroupForTest(): DefaultActionGroup? {
    val selectedProvider = providerSelector.selectedProvider ?: return null
    val providerId = selectedProvider.bridge.provider.value
    val supportedEfforts = availableReasoningEfforts(currentSettings(), loadedModelCatalog(providerId))
    if (supportedEfforts.isEmpty()) {
      return null
    }
    return createPlanReasoningEffortActionGroup(supportedEfforts)
  }

  private fun createModelActionGroup(providerId: String, modelCatalogState: ModelCatalogState?): DefaultActionGroup {
    val group = DefaultActionGroup()
    group.add(ModelAction(modelId = null, text = AgentPromptBundle.message("popup.generation.model.popup.auto")))
    when (modelCatalogState) {
      is ModelCatalogState.Loaded -> {
        addModelActions(group, modelCatalogState.models)
      }
      is ModelCatalogState.Refreshing -> {
        addModelActions(group, modelCatalogState.models)
        group.add(ModelCatalogStatusAction(AgentPromptBundle.message("popup.generation.model.refreshing"), AnimatedIcon.Default.INSTANCE))
      }
      is ModelCatalogState.RefreshFailed -> {
        addModelActions(group, modelCatalogState.models)
        addModelCatalogRetryActions(group, providerId, AgentPromptBundle.message("popup.generation.model.refresh.failed"))
      }
      ModelCatalogState.Loading -> {
        group.add(ModelCatalogStatusAction(AgentPromptBundle.message("popup.generation.model.loading"), AnimatedIcon.Default.INSTANCE))
      }
      ModelCatalogState.Failed -> {
        addModelCatalogRetryActions(group, providerId, AgentPromptBundle.message("popup.generation.model.load.failed"))
      }
      null -> Unit
    }
    return group
  }

  private fun addModelActions(group: DefaultActionGroup, models: List<AgentPromptGenerationModel>) {
    if (models.isEmpty()) {
      group.add(ModelCatalogStatusAction(AgentPromptBundle.message("popup.generation.model.empty")))
    }
    else {
      models.groupedForModelSelector().forEach { section ->
        group.add(Separator.create(section.group.modelSelectorText()))
        section.models.forEach { model -> group.add(ModelAction(modelId = model.id, text = model.displayName)) }
      }
    }
  }

  private fun addModelCatalogRetryActions(group: DefaultActionGroup, providerId: String, statusText: @Nls String) {
    group.add(ModelCatalogStatusAction(statusText))
    group.add(RetryModelCatalogAction(providerId))
  }

  private fun createReasoningEffortActionGroup(supportedEfforts: Set<AgentPromptReasoningEffort>): DefaultActionGroup {
    val group = DefaultActionGroup()
    reasoningEffortOrder()
      .filter { effort -> effort == AgentPromptReasoningEffort.AUTO || effort in supportedEfforts }
      .forEach { effort -> group.add(ReasoningEffortAction(effort)) }
    return group
  }

  private fun createPlanReasoningEffortActionGroup(supportedEfforts: Set<AgentPromptReasoningEffort>): DefaultActionGroup {
    val group = DefaultActionGroup()
    group.add(PlanReasoningEffortAction(AgentPromptPlanEffortMode.SAME_AS_NORMAL))
    group.add(PlanReasoningEffortAction(AgentPromptPlanEffortMode.PROVIDER_DEFAULT))
    reasoningEffortOrder()
      .filter { effort -> effort != AgentPromptReasoningEffort.AUTO && effort in supportedEfforts }
      .forEach { effort -> group.add(PlanReasoningEffortAction(AgentPromptPlanEffortMode.explicit(effort))) }
    return group
  }

  @TestOnly
  internal fun createLaunchProfileActionGroupForTest(): DefaultActionGroup {
    return createLaunchProfileActionGroup()
  }

  private fun createLaunchProfileActionGroup(): DefaultActionGroup {
    val group = DefaultActionGroup()
    val profiles = launchableProfiles()
    val standardProfiles = profiles.filter { profile -> profile.launchMode != AgentSessionLaunchMode.YOLO }
    val yoloProfiles = profiles.filter { profile -> profile.launchMode == AgentSessionLaunchMode.YOLO }
    standardProfiles.forEach { profile -> group.add(LaunchProfileAction(profile)) }
    if (yoloProfiles.isNotEmpty()) {
      if (standardProfiles.isNotEmpty()) {
        group.add(Separator.getInstance())
      }
      group.add(Separator.create(AgentPromptBundle.message("popup.provider.section.auto")))
      yoloProfiles.forEach { profile -> group.add(LaunchProfileAction(profile)) }
    }
    group.add(Separator.getInstance())
    group.add(ManageProfilesAction())
    return group
  }

  private fun applyProfile(profile: AgentPromptLaunchProfile): Boolean {
    val providerEntry = findApplicableProviderEntry(profile) ?: return false
    val provider = providerEntry.bridge.provider
    val planModeSelected = providerSelector.isPlanModeSelected()
    providerSelector.selectProvider(provider, profile.launchMode)
    providerSelector.setPlanModeSelected(planModeSelected)
    transientSettingsByProviderId[profile.providerId] = profile.generationSettings
    activeProfileId = profile.id
    refreshPresentation()
    return true
  }

  private fun canApplyProfile(profile: AgentPromptLaunchProfile): Boolean {
    return findApplicableProviderEntry(profile) != null
  }

  private fun findApplicableProviderEntry(profile: AgentPromptLaunchProfile): ProviderEntry? {
    val provider = AgentSessionProvider.fromOrNull(profile.providerId) ?: return null
    val providerEntry = providerSelector.findProviderEntry(provider) ?: return null
    return providerEntry.takeIf { entry ->
      entry.isCliAvailable && profile.launchMode in entry.bridge.supportedLaunchModes
    }
  }

  private fun saveNewProfile(profile: AgentPromptLaunchProfile) {
    userProfilesById[profile.id] = profile
    activeProfileId = profile.id
    saveProfiles()
    onDefaultSaved(AgentPromptBundle.message("popup.profile.saved"))
    refreshPresentation()
  }

  private fun updateUserProfile(profile: AgentPromptLaunchProfile) {
    if (profile.kind != AgentPromptLaunchProfileKind.USER || profile.id !in userProfilesById) {
      return
    }
    userProfilesById[profile.id] = profile
    activeProfileId = profile.id
    saveProfiles()
    onDefaultSaved(AgentPromptBundle.message("popup.profile.updated"))
    refreshPresentation()
  }

  private fun setDefaultProfile(profile: AgentPromptLaunchProfile) {
    defaultProfileId = profile.id
    saveProfiles()
    onDefaultSaved(AgentPromptBundle.message("popup.profile.default.saved"))
    refreshPresentation()
  }

  private fun deleteProfile(profile: AgentPromptLaunchProfile) {
    if (Messages.showYesNoDialog(
        invocationData.project,
        AgentPromptBundle.message("popup.profile.delete.message", profile.name),
        AgentPromptBundle.message("popup.profile.delete.title"),
        Messages.getQuestionIcon(),
      ) != Messages.YES) {
      return
    }
    userProfilesById.remove(profile.id)
    if (activeProfileId == profile.id) {
      activeProfileId = null
    }
    if (defaultProfileId == profile.id) {
      defaultProfileId = null
    }
    saveProfiles()
    onDefaultSaved(AgentPromptBundle.message("popup.profile.deleted"))
    refreshPresentation()
  }

  private fun saveProfiles() {
    val launcher = launcherProvider() ?: return
    val currentPreferences = launcher.loadProviderPreferences()
    launcher.saveProviderPreferences(
      currentPreferences.copy(
        launchProfiles = userProfilesById.values.toList(),
        activeLaunchProfileId = defaultProfileId,
      )
    )
  }

  private fun showManageProfilesDialog() {
    manageProfilesDialogRunner {
      createManageProfilesDialog().show()
    }
  }

  @TestOnly
  internal fun createManageProfilesDialogForTest(): AgentPromptLaunchProfileEditorDialog {
    return createManageProfilesDialog()
  }

  private fun createManageProfilesDialog(): AgentPromptLaunchProfileEditorDialog {
    return AgentPromptLaunchProfileEditorDialog(
      project = invocationData.project,
      profiles = allManagedProfiles(),
      activeProfileId = activeProfileId,
      defaultProfileId = defaultProfileId,
      providerEntries = providerSelector.providerEntries(),
      currentDraftProfile = currentDraftProfile(
        id = "",
        name = AgentPromptBundle.message("popup.profile.name.default"),
        kind = AgentPromptLaunchProfileKind.USER,
      ),
      modelCatalogProvider = ::loadedModelCatalog,
      newUserProfileId = ::newUserProfileId,
      onCreateProfile = ::saveNewProfile,
      onUpdateProfile = ::updateUserProfile,
      onDeleteProfile = ::deleteProfile,
      onSetDefaultProfile = ::setDefaultProfile,
      onSelectProfile = { profile -> activeProfileId = profile?.id },
    )
  }

  private fun currentDraftProfile(
    id: String,
    name: String,
    kind: AgentPromptLaunchProfileKind,
  ): AgentPromptLaunchProfile? {
    val provider = providerSelector.selectedProvider?.bridge?.provider ?: return null
    val currentSettings = currentSettings()
    return AgentPromptLaunchProfile(
      id = id,
      name = name,
      kind = kind,
      providerId = provider.value,
      launchMode = providerSelector.selectedLaunchMode,
      generationSettings = currentSettings,
    )
  }

  private fun findProfile(profileId: String?): AgentPromptLaunchProfile? {
    if (profileId == null) return null
    return userProfilesById[profileId] ?: providerSelector.builtInLaunchProfiles().firstOrNull { profile -> profile.id == profileId }
  }

  private fun profileIcon(profile: AgentPromptLaunchProfile?): Icon {
    val item = profileMenuItem(profile) ?: providerSelector.selectedMenuItem()
    return item?.let(::providerItemMonochromeIconWithMode) ?: AllIcons.Toolwindows.ToolWindowMessages
  }

  private fun profileMenuItem(profile: AgentPromptLaunchProfile?): AgentSessionProviderMenuItem? {
    if (profile == null) {
      return null
    }
    return providerSelector.findMenuItem(AgentSessionProvider.fromOrNull(profile.providerId), profile.launchMode)
  }

  private fun isActiveProfileModified(): Boolean {
    val activeProfile = findProfile(activeProfileId) ?: return false
    val draft = currentDraftProfile(activeProfile.id, activeProfile.name, activeProfile.kind) ?: return false
    return activeProfile.profilePayload() != draft.profilePayload()
  }

  private fun selectedProfileIdForPresentation(): String? {
    val activeProfile = findProfile(activeProfileId)
    if (activeProfile != null) {
      if (isActiveProfileModified()) return null
      return activeProfile.id
    }

    val draft = currentDraftProfile(id = "", name = "", kind = AgentPromptLaunchProfileKind.USER) ?: return null
    return launchableProfiles().firstOrNull { profile -> profile.profilePayload() == draft.profilePayload() }?.id
  }

  private fun newUserProfileId(): String {
    val base = "user:${System.currentTimeMillis()}"
    if (base !in userProfilesById) return base
    var suffix = 2
    while ("$base:$suffix" in userProfilesById) {
      suffix++
    }
    return "$base:$suffix"
  }

  @TestOnly
  internal fun manageProfilesRowsForTest(): List<AgentPromptLaunchProfile> {
    return allManagedProfiles()
  }

  @TestOnly
  internal fun setDefaultProfileForTest(profileId: String): Boolean {
    val profile = findProfile(profileId) ?: return false
    setDefaultProfile(profile)
    return true
  }

  private fun launchableProfiles(): List<AgentPromptLaunchProfile> {
    return providerSelector.builtInLaunchProfiles() + userProfilesById.values.filter(::canApplyProfile)
  }

  private fun allManagedProfiles(): List<AgentPromptLaunchProfile> {
    return providerSelector.builtInLaunchProfiles() + userProfilesById.values
  }

  private inner class ModelAction(
    private val modelId: String?,
    text: @Nls String,
  ) : DumbAwareToggleAction(text) {
    init {
      templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Never
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return currentSettings().modelId == modelId
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        selectModel(modelId)
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private class ModelCatalogStatusAction(
    text: @Nls String,
    private val statusIcon: Icon? = null,
  ) : DumbAwareAction(text, null, statusIcon) {
    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = false
      e.presentation.icon = statusIcon
    }

    override fun actionPerformed(e: AnActionEvent) {
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private inner class RetryModelCatalogAction(
    private val providerId: String,
  ) : DumbAwareAction(AgentPromptBundle.message("popup.generation.model.retry")) {
    init {
      templatePresentation.description = AgentPromptBundle.message("popup.generation.model.retry.description")
      templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Never
    }

    override fun actionPerformed(e: AnActionEvent) {
      val selectedProvider = providerSelector.selectedProvider ?: return
      if (selectedProvider.bridge.provider.value != providerId) {
        return
      }

      requestModelCatalogRefresh(selectedProvider)
      refreshPresentation()
      refreshModelPopupIfOpen(providerId)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private inner class ReasoningEffortAction(
    private val effort: AgentPromptReasoningEffort,
  ) : DumbAwareToggleAction(reasoningEffortPopupText(effort)) {
    init {
      templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Never
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return currentSettings().reasoningEffort == effort
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        selectReasoningEffort(effort)
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private inner class PlanReasoningEffortAction(
    private val planEffort: AgentPromptPlanEffortMode,
  ) : DumbAwareToggleAction(planReasoningEffortPopupText(planEffort)) {
    init {
      templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Never
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return selectedPlanEffortMode == planEffort
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        selectPlanReasoningEffort(planEffort)
      }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private inner class LaunchProfileAction(
    private val profile: AgentPromptLaunchProfile,
  ) : DumbAwareToggleAction(profile.name, null, profileIcon(profile)) {
    init {
      templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Never
      setLaunchProfileIcon(templatePresentation, profileIcon(profile), isProfileSelected())
    }

    private fun isProfileSelected(): Boolean {
      return selectedProfileIdForPresentation() == profile.id
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return isProfileSelected()
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        applyProfile(profile)
      }
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      setLaunchProfileIcon(e.presentation, profileIcon(profile), Toggleable.isSelected(e.presentation))
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

  private inner class ManageProfilesAction : DumbAwareAction(AgentPromptBundle.message("popup.profile.manage")) {
    init {
      templatePresentation.description = AgentPromptBundle.message("popup.profile.manage.description")
      templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Never
    }

    override fun actionPerformed(e: AnActionEvent) {
      showManageProfilesDialog()
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }

}

private sealed interface ModelCatalogState {
  data object Loading : ModelCatalogState

  data class Loaded(@JvmField val models: List<AgentPromptGenerationModel>) : ModelCatalogState

  data class Refreshing(@JvmField val models: List<AgentPromptGenerationModel>) : ModelCatalogState

  data object Failed : ModelCatalogState

  data class RefreshFailed(@JvmField val models: List<AgentPromptGenerationModel>) : ModelCatalogState
}

private fun ModelCatalogState.modelsOrNull(): List<AgentPromptGenerationModel>? {
  return when (this) {
    is ModelCatalogState.Loaded -> models
    is ModelCatalogState.Refreshing -> models
    is ModelCatalogState.RefreshFailed -> models
    ModelCatalogState.Loading,
    ModelCatalogState.Failed,
      -> null
  }
}

private fun AgentPromptLaunchProfile.profilePayload(): AgentPromptLaunchProfile {
  return copy(
    id = "",
    name = "",
    kind = AgentPromptLaunchProfileKind.USER,
    planEffort = AgentPromptPlanEffortMode.SAME_AS_NORMAL,
    startInPlanMode = false,
  )
}

internal fun List<AgentPromptGenerationModel>.catalogReasoningEfforts(): Set<AgentPromptReasoningEffort>? {
  val efforts = flatMapTo(LinkedHashSet()) { model -> model.supportedReasoningEfforts }
  return efforts.takeIf { it.isNotEmpty() }
}

private fun modelText(modelId: String?, models: List<AgentPromptGenerationModel>): @Nls String {
  if (modelId == null) {
    return AgentPromptBundle.message("popup.generation.model.auto")
  }
  val displayName = models.firstOrNull { model -> model.id == modelId }?.displayName ?: modelId
  return AgentPromptBundle.message("popup.generation.model.selected", displayName)
}

internal fun reasoningEffortOrder(): List<AgentPromptReasoningEffort> {
  return listOf(
    AgentPromptReasoningEffort.AUTO,
    AgentPromptReasoningEffort.LOW,
    AgentPromptReasoningEffort.MEDIUM,
    AgentPromptReasoningEffort.HIGH,
    AgentPromptReasoningEffort.XHIGH,
    AgentPromptReasoningEffort.MAX,
  )
}

private fun reasoningEffortTooltipText(available: Boolean): @Nls String {
  return if (available) {
    AgentPromptBundle.message("popup.generation.reasoning.tooltip")
  }
  else {
    AgentPromptBundle.message("popup.generation.reasoning.unavailable.tooltip")
  }
}

private fun reasoningEffortText(effort: AgentPromptReasoningEffort): @Nls String {
  return when (effort) {
    AgentPromptReasoningEffort.AUTO -> AgentPromptBundle.message("popup.generation.reasoning.auto")
    AgentPromptReasoningEffort.LOW -> AgentPromptBundle.message("popup.generation.reasoning.low")
    AgentPromptReasoningEffort.MEDIUM -> AgentPromptBundle.message("popup.generation.reasoning.medium")
    AgentPromptReasoningEffort.HIGH -> AgentPromptBundle.message("popup.generation.reasoning.high")
    AgentPromptReasoningEffort.XHIGH -> AgentPromptBundle.message("popup.generation.reasoning.xhigh")
    AgentPromptReasoningEffort.MAX -> AgentPromptBundle.message("popup.generation.reasoning.max")
  }
}

internal fun reasoningEffortPopupText(effort: AgentPromptReasoningEffort): @Nls String {
  return when (effort) {
    AgentPromptReasoningEffort.AUTO -> AgentPromptBundle.message("popup.generation.reasoning.popup.auto")
    AgentPromptReasoningEffort.LOW -> AgentPromptBundle.message("popup.generation.reasoning.popup.low")
    AgentPromptReasoningEffort.MEDIUM -> AgentPromptBundle.message("popup.generation.reasoning.popup.medium")
    AgentPromptReasoningEffort.HIGH -> AgentPromptBundle.message("popup.generation.reasoning.popup.high")
    AgentPromptReasoningEffort.XHIGH -> AgentPromptBundle.message("popup.generation.reasoning.popup.xhigh")
    AgentPromptReasoningEffort.MAX -> AgentPromptBundle.message("popup.generation.reasoning.popup.max")
  }
}

private fun launchProfileText(profile: AgentPromptLaunchProfile?, modified: Boolean): @Nls String {
  val profileName = when {
    profile == null || profile.kind == AgentPromptLaunchProfileKind.BUILT_IN -> AgentPromptBundle.message("popup.profile.default")
    else -> profile.name
  }
  return if (modified) {
    AgentPromptBundle.message("popup.profile.modified", profileName)
  }
  else {
    AgentPromptBundle.message("popup.profile.selected", profileName)
  }
}

private fun planReasoningEffortText(planEffort: AgentPromptPlanEffortMode): @Nls String {
  return when (planEffort.kind) {
    AgentPromptPlanEffortModeKind.SAME_AS_NORMAL -> AgentPromptBundle.message("popup.generation.plan.reasoning.same")
    AgentPromptPlanEffortModeKind.PROVIDER_DEFAULT -> AgentPromptBundle.message("popup.generation.plan.reasoning.provider.default")
    AgentPromptPlanEffortModeKind.EXPLICIT -> when (planEffort.explicitEffort) {
      AgentPromptReasoningEffort.LOW -> AgentPromptBundle.message("popup.generation.plan.reasoning.low")
      AgentPromptReasoningEffort.MEDIUM -> AgentPromptBundle.message("popup.generation.plan.reasoning.medium")
      AgentPromptReasoningEffort.HIGH -> AgentPromptBundle.message("popup.generation.plan.reasoning.high")
      AgentPromptReasoningEffort.XHIGH -> AgentPromptBundle.message("popup.generation.plan.reasoning.xhigh")
      AgentPromptReasoningEffort.MAX -> AgentPromptBundle.message("popup.generation.plan.reasoning.max")
      AgentPromptReasoningEffort.AUTO,
      null,
        -> AgentPromptBundle.message("popup.generation.plan.reasoning.provider.default")
    }
  }
}

private fun planReasoningEffortPopupText(planEffort: AgentPromptPlanEffortMode): @Nls String {
  return when (planEffort.kind) {
    AgentPromptPlanEffortModeKind.SAME_AS_NORMAL -> AgentPromptBundle.message("popup.generation.plan.reasoning.popup.same")
    AgentPromptPlanEffortModeKind.PROVIDER_DEFAULT -> AgentPromptBundle.message("popup.generation.plan.reasoning.popup.provider.default")
    AgentPromptPlanEffortModeKind.EXPLICIT -> planEffort.explicitEffort?.let(::reasoningEffortPopupText)
                                              ?: AgentPromptBundle.message("popup.generation.plan.reasoning.popup.provider.default")
  }
}
