// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

// @spec community/plugins/agent-workbench/spec/actions/global-prompt-task-cost-profiles.spec.md

import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.prompt.ui.context.dataContextOrNull
import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderMenuItem
import com.intellij.agent.workbench.sessions.core.providers.generationSettingsForPlanMode
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
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.ui.components.ActionLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import javax.swing.Icon
import javax.swing.JPanel

internal class AgentPromptGenerationSettingsController(
  private val invocationData: AgentPromptInvocationData,
  private val providerSelector: AgentPromptProviderSelector,
  private val generationSettingsPanel: JPanel,
  private val profileAction: AgentPromptToolbarProfileAction? = null,
  private val launchProfileLink: ActionLink = ActionLink(AgentPromptBundle.message("popup.profile.header.standard")),
  private val modelSelectorLink: ActionLink,
  private val reasoningEffortLink: ActionLink,
  private val planReasoningEffortLink: ActionLink = ActionLink(AgentPromptBundle.message("popup.generation.plan.reasoning.same")),
  defaultProfileActionControl: AgentPromptDefaultProfileActionControl = AgentPromptDefaultProfileActionControl(),
  private val modelCatalogScope: CoroutineScope,
  private val modelCatalogService: AgentPromptGenerationModelCatalogService = invocationData.project.service(),
  private val launcherProvider: () -> AgentPromptLauncherBridge?,
  private val onDefaultSaved: (String) -> Unit,
  private val onLaunchProfileApplied: () -> Unit = {},
  private val manageProfilesDialogRunner: (AgentPromptLaunchProfileEditorOpenDialog) -> Unit = { openDialog ->
    ApplicationManager.getApplication().invokeLater { openDialog(null) }
  },
) {
  private val transientSettingsByProviderId = LinkedHashMap<String, AgentPromptGenerationSettings>()
  private var generationControlsVisible = true
  private var providerSelectorVisible = true
  private var activeModelPopup: JBPopup? = null
  private var activeModelPopupProviderId: String? = null
  private val launchProfileState = AgentPromptLaunchProfileState(
    builtInProfiles = ::builtInLaunchProfiles,
    canApplyProfile = ::canApplyProfile,
  )
  private val defaultProfileActionController = AgentPromptDefaultProfileActionController(
    actionControl = defaultProfileActionControl,
    actionProvider = { launchProfileState.defaultAction(currentProfileDraft()) },
    onMakeDefault = ::setDefaultProfile,
    onSaveAsDefault = ::saveDraftProfileAsDefault,
  )

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
    launchProfileState.restore(
      preferences = preferences,
      implicitDefaultProfileId = implicitBuiltInDefaultProfileId(),
    )
    val activeProfile = preferences.activeLaunchProfileId?.let(::findProfile)
    if (activeProfile != null) {
      if (!applyProfile(activeProfile)) {
        launchProfileState.clearSelectedProfile()
      }
    }
    refreshPresentation()
  }

  fun setGenerationControlsVisible(visible: Boolean) {
    setControlsVisibility(providerSelectorVisible = visible, generationControlsVisible = visible)
  }

  /**
   * Decouples the provider selector from the per-task generation controls so an extension tab can keep the
   * provider chooser while hiding model/reasoning controls that its submit action does not consume.
   */
  fun setControlsVisibility(providerSelectorVisible: Boolean, generationControlsVisible: Boolean) {
    this.providerSelectorVisible = providerSelectorVisible
    this.generationControlsVisible = generationControlsVisible
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
    return generationSettingsForPlanMode(
      generationSettings = currentSettings,
      startInPlanMode = providerSelector.isPlanModeSelected(),
    )
  }

  fun refreshPresentation() {
    val selectedProvider = providerSelector.selectedProvider
    val showGenerationControls = generationControlsVisible
    val showProviderSelector = providerSelectorVisible
    val modelCatalog = selectedProvider?.bridge?.provider?.value?.let(::loadedModelCatalog)
    val modelSelectionAvailable = selectedProvider?.bridge?.supportsGenerationModelSelection == true
    val currentSettings = currentSettings()
    val reasoningEfforts = availableReasoningEfforts(currentSettings, modelCatalog)
    val currentPlanReasoningEffort = sanitizePlanReasoningEffort(currentSettings.planReasoningEffort, reasoningEfforts)
    val reasoningEffortAvailable = reasoningEfforts.isNotEmpty()
    val planModeSelected = providerSelector.isPlanModeSelected()
    val planEffortSupported = reasoningEffortAvailable && selectedProvider?.bridge?.supportsPlanReasoningEffort == true
    generationSettingsPanel.isVisible = showGenerationControls
    launchProfileLink.isVisible = showProviderSelector
    launchProfileLink.isEnabled = showProviderSelector
    val profileDraft = currentProfileDraft()
    val profile = launchProfileState.profileForPresentation(profileDraft)
    val profileText = launchProfileText(profile)
    val profileTooltip = AgentPromptBundle.message("popup.profile.tooltip")
    profileAction?.setPresentation(
      text = profileText,
      description = profileTooltip,
      icon = profileIcon(profile),
      visible = showProviderSelector,
      enabled = showProviderSelector,
    )
    modelSelectorLink.isVisible = showGenerationControls && modelSelectionAvailable
    modelSelectorLink.isEnabled = showGenerationControls && modelSelectionAvailable
    reasoningEffortLink.isVisible = showGenerationControls
    reasoningEffortLink.isEnabled = showGenerationControls && reasoningEffortAvailable
    planReasoningEffortLink.isVisible = showGenerationControls && planEffortSupported
    planReasoningEffortLink.isEnabled = showGenerationControls && planEffortSupported && planModeSelected
    defaultProfileActionController.refreshPresentation(showGenerationControls)
    if (showProviderSelector) {
      launchProfileLink.text = profileText
      launchProfileLink.setToolTipText(HtmlChunk.text(profileTooltip))
      launchProfileLink.accessibleContext.accessibleName = AgentPromptBundle.message("popup.profile.accessible.name") + ": " + profileText
    }
    if (showGenerationControls) {
      modelSelectorLink.text = modelText(
        modelId = currentSettings.modelId,
        models = modelCatalog.orEmpty(),
        displayNameForSavedModel = ::displayNameForSavedModel,
      )
      modelSelectorLink.setToolTipText(HtmlChunk.text(AgentPromptBundle.message("popup.generation.model.tooltip")))
      modelSelectorLink.accessibleContext.accessibleName = modelSelectorLink.text
      reasoningEffortLink.text = reasoningEffortText(currentSettings.reasoningEffort)
      reasoningEffortLink.setToolTipText(HtmlChunk.text(reasoningEffortTooltipText(reasoningEffortAvailable)))
      reasoningEffortLink.accessibleContext.accessibleName = reasoningEffortLink.text
      planReasoningEffortLink.text = planReasoningEffortText(currentPlanReasoningEffort)
      val planReasoningEffortTooltipKey = if (planModeSelected) {
        "popup.generation.plan.reasoning.tooltip"
      }
      else {
        "popup.generation.plan.reasoning.disabled.tooltip"
      }
      planReasoningEffortLink.setToolTipText(HtmlChunk.text(AgentPromptBundle.message(planReasoningEffortTooltipKey)))
      planReasoningEffortLink.accessibleContext.accessibleName =
        AgentPromptBundle.message("popup.generation.plan.reasoning.accessible.name") + ": " + planReasoningEffortLink.text
    }
    generationSettingsPanel.revalidate()
    generationSettingsPanel.repaint()
  }

  private fun requestModelCatalogRefresh(selectedProvider: ProviderEntry) {
    val providerId = selectedProvider.bridge.provider.value
    modelCatalogService.requestStateRefresh(selectedProvider.bridge, invocationData.project) {
      if (!modelCatalogScope.isActive) {
        return@requestStateRefresh
      }
      refreshPresentation()
      refreshModelPopupIfOpen(providerId)
    }
  }

  private fun requestModelCatalogRefresh(providerId: String, onStateChanged: () -> Unit) {
    val provider = providerSelector.providerEntries().firstOrNull { entry -> entry.bridge.provider.value == providerId } ?: return
    modelCatalogService.requestStateRefresh(provider.bridge, invocationData.project, onStateChanged)
  }

  private fun modelCatalogState(providerId: String): AgentPromptGenerationModelCatalogState? {
    return modelCatalogService.catalogState(providerId)
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

  private fun sanitizePlanReasoningEffort(
    planReasoningEffort: AgentPromptReasoningEffort?,
    supportedEfforts: Set<AgentPromptReasoningEffort>,
  ): AgentPromptReasoningEffort? {
    return when (planReasoningEffort) {
      null -> null
      AgentPromptReasoningEffort.AUTO -> AgentPromptReasoningEffort.AUTO
      in supportedEfforts -> planReasoningEffort
      else -> AgentPromptReasoningEffort.AUTO
    }
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

  private fun selectPlanReasoningEffort(planReasoningEffort: AgentPromptReasoningEffort?) {
    val providerId = providerSelector.selectedProvider?.bridge?.provider?.value ?: return
    val currentSettings = currentSettings()
    transientSettingsByProviderId[providerId] = currentSettings.copy(planReasoningEffort = planReasoningEffort)
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
    if (!selectedProvider.bridge.supportsPlanReasoningEffort || !providerSelector.isPlanModeSelected()) {
      return null
    }
    val providerId = selectedProvider.bridge.provider.value
    val supportedEfforts = availableReasoningEfforts(currentSettings(), loadedModelCatalog(providerId))
    if (supportedEfforts.isEmpty()) {
      return null
    }
    return createPlanReasoningEffortActionGroup(supportedEfforts)
  }

  private fun createModelActionGroup(providerId: String, modelCatalogState: AgentPromptGenerationModelCatalogState?): DefaultActionGroup {
    val group = DefaultActionGroup()
    buildGenerationModelSelectorEntries(providerId,
                                        modelCatalogState,
                                        currentSettings().modelId,
                                        ::displayNameForSavedModel).forEach { entry ->
      when (entry) {
        is AgentPromptGenerationModelSelectorEntry.Model -> {
          entry.separatorGroup?.let { group.add(Separator.create(it.modelSelectorText())) }
          group.add(ModelAction(modelId = entry.modelId, text = entry.displayName))
        }
        is AgentPromptGenerationModelSelectorEntry.Status -> {
          group.add(ModelCatalogStatusAction(entry.displayName, modelCatalogStatusIcon(entry.kind)))
        }
        is AgentPromptGenerationModelSelectorEntry.Retry -> {
          group.add(RetryModelCatalogAction(entry.providerId))
        }
      }
    }
    return group
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
    group.add(PlanReasoningEffortAction(null))
    group.add(PlanReasoningEffortAction(AgentPromptReasoningEffort.AUTO))
    reasoningEffortOrder()
      .filter { effort -> effort != AgentPromptReasoningEffort.AUTO && effort in supportedEfforts }
      .forEach { effort -> group.add(PlanReasoningEffortAction(effort)) }
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
    onLaunchProfileApplied()
    transientSettingsByProviderId[profile.providerId] = profile.generationSettings
    launchProfileState.selectProfile(profile)
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
    launchProfileState.saveNewProfile(profile)
    saveProfiles()
    onDefaultSaved(AgentPromptBundle.message("popup.profile.saved"))
    refreshPresentation()
  }

  private fun saveProfile(profile: AgentPromptLaunchProfile) {
    if (!launchProfileState.saveProfile(profile)) {
      return
    }
    saveProfiles()
    onDefaultSaved(AgentPromptBundle.message("popup.profile.updated"))
    refreshPresentation()
  }

  private fun setDefaultProfile(profile: AgentPromptLaunchProfile) {
    launchProfileState.setDefaultProfile(profile)
    saveProfiles()
    onDefaultSaved(AgentPromptBundle.message("popup.profile.default.saved"))
    refreshPresentation()
  }

  private fun saveDraftProfileAsDefault() {
    val draft = currentDraftProfile(
      id = newUserProfileId(),
      name = generatedDraftProfileName(),
    ) ?: return
    if (!canApplyProfile(draft)) {
      return
    }
    launchProfileState.saveDraftAsDefault(draft)
    saveProfiles()
    onDefaultSaved(AgentPromptBundle.message("popup.profile.saved.default"))
    refreshPresentation()
  }

  private fun deleteProfile(profile: AgentPromptLaunchProfile): Boolean {
    if (!launchProfileState.canDeleteProfile(profile)) {
      return false
    }
    val resetsBuiltInProfile = builtInLaunchProfiles().any { item -> item.id == profile.id }
    val message = if (resetsBuiltInProfile) {
      AgentPromptBundle.message("popup.profile.reset.message", profile.name)
    }
    else {
      AgentPromptBundle.message("popup.profile.delete.message", profile.name)
    }
    val title = if (resetsBuiltInProfile) AgentPromptBundle.message("popup.profile.reset.title")
    else AgentPromptBundle.message("popup.profile.delete.title")
    if (Messages.showYesNoDialog(
        invocationData.project,
        message,
        title,
        Messages.getQuestionIcon(),
      ) != Messages.YES) {
      return false
    }
    if (!launchProfileState.deleteProfile(profile)) {
      return false
    }
    saveProfiles()
    onDefaultSaved(AgentPromptBundle.message(if (resetsBuiltInProfile) "popup.profile.reset" else "popup.profile.deleted"))
    refreshPresentation()
    return true
  }

  private fun saveProfiles() {
    val launcher = launcherProvider() ?: return
    val currentPreferences = launcher.loadProviderPreferences()
    launcher.saveProviderPreferences(
      currentPreferences.copy(
        launchProfiles = launchProfileState.userProfiles(),
        activeLaunchProfileId = launchProfileState.persistedDefaultProfileId,
      )
    )
  }

  private fun showManageProfilesDialog() {
    manageProfilesDialogRunner { restorePromptOnClose ->
      ApplicationManager.getApplication().service<AgentPromptLaunchProfileEditorWindowService>().openOrFocus(
        request = createManageProfilesDialogRequest(),
        restorePromptOnClose = restorePromptOnClose,
      )
    }
  }

  @TestOnly
  internal fun createManageProfilesDialogForTest(): AgentPromptLaunchProfileEditorDialog {
    return createManageProfilesDialog()
  }

  @TestOnly
  internal fun openManageProfilesDialogForTest(restorePromptOnClose: (() -> Unit)? = null) {
    ApplicationManager.getApplication().service<AgentPromptLaunchProfileEditorWindowService>().openOrFocusForTest(
      request = createManageProfilesDialogRequest(),
      restorePromptOnClose = restorePromptOnClose,
    )
  }

  private fun createManageProfilesDialogRequest(): AgentPromptLaunchProfileEditorRequest {
    return AgentPromptLaunchProfileEditorRequest(
      project = invocationData.project,
      profiles = allManagedProfiles(),
      activeProfileId = launchProfileState.selectedProfileId,
      defaultProfileId = launchProfileState.effectiveDefaultProfileId,
      builtInProfiles = builtInLaunchProfiles(),
      providerEntries = providerSelector.providerEntries(),
      modelCatalogProvider = ::loadedModelCatalog,
      modelCatalogStateProvider = ::modelCatalogState,
      requestModelCatalogRefresh = ::requestModelCatalogRefresh,
      newUserProfileId = ::newUserProfileId,
      onCreateProfile = ::saveNewProfile,
      onUpdateProfile = ::saveProfile,
      onDeleteProfile = ::deleteProfile,
      onSetDefaultProfile = ::setDefaultProfile,
    )
  }

  private fun createManageProfilesDialog(
    onDispose: (AgentPromptLaunchProfileEditorDialog) -> Unit = {},
  ): AgentPromptLaunchProfileEditorDialog {
    val request = createManageProfilesDialogRequest()
    lateinit var dialog: AgentPromptLaunchProfileEditorDialog
    dialog = AgentPromptLaunchProfileEditorDialog(
      project = request.project,
      profiles = request.profiles,
      activeProfileId = request.activeProfileId,
      defaultProfileId = request.defaultProfileId,
      builtInProfiles = request.builtInProfiles,
      providerEntries = request.providerEntries,
      modelCatalogProvider = request.modelCatalogProvider,
      modelCatalogStateProvider = request.modelCatalogStateProvider,
      requestModelCatalogRefresh = request.requestModelCatalogRefresh,
      newUserProfileId = request.newUserProfileId,
      onCreateProfile = request.onCreateProfile,
      onUpdateProfile = request.onUpdateProfile,
      onDeleteProfile = request.onDeleteProfile,
      onSetDefaultProfile = request.onSetDefaultProfile,
      onSelectProfile = {},
      onDispose = { onDispose(dialog) },
    )
    return dialog
  }

  private fun currentDraftProfile(
    id: String,
    name: String,
  ): AgentPromptLaunchProfile? {
    val provider = providerSelector.selectedProvider?.bridge?.provider ?: return null
    val currentSettings = currentSettings()
    return AgentPromptLaunchProfile(
      id = id,
      name = name,
      kind = AgentPromptLaunchProfileKind.USER,
      providerId = provider.value,
      launchMode = providerSelector.selectedLaunchMode,
      generationSettings = currentSettings,
    )
  }

  private fun findProfile(profileId: String?): AgentPromptLaunchProfile? {
    return launchProfileState.findProfile(profileId)
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

  private fun displayNameForSavedModel(modelId: String): @NlsSafe String {
    return providerSelector.selectedProvider?.bridge?.displayNameForGenerationModelId(modelId)
           ?: unknownGenerationModelDisplayName(modelId)
  }

  private fun selectedProfileIdForPresentation(): String? {
    return launchProfileState.selectedProfileIdForPresentation(currentProfileDraft())
  }

  private fun currentProfileDraft(): AgentPromptLaunchProfile? {
    return currentDraftProfile(id = "", name = "")
  }

  private fun generatedDraftProfileName(): @NlsSafe String {
    val draft = currentProfileDraft() ?: return AgentPromptBundle.message("popup.profile.name.default")
    val providerId = providerSelector.selectedProvider?.bridge?.provider?.value
    return generatedLaunchProfileName(
      profile = draft,
      existingProfiles = allManagedProfiles(),
      models = providerId?.let(::loadedModelCatalog).orEmpty(),
      compactLaunchModeLabel = providerSelector.compactBuiltInProfileLabel(draft),
    )
  }

  private fun newUserProfileId(): String {
    val base = "user:${System.currentTimeMillis()}"
    if (findProfile(base) == null) return base
    var suffix = 2
    while (findProfile("$base:$suffix") != null) {
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
    return launchProfileState.launchableProfiles()
  }

  private fun allManagedProfiles(): List<AgentPromptLaunchProfile> {
    return launchProfileState.allManagedProfiles()
  }

  private fun builtInLaunchProfiles(): List<AgentPromptLaunchProfile> {
    return providerSelector.builtInLaunchProfiles()
  }

  private fun implicitBuiltInDefaultProfileId(): String? {
    val selectedProviderId = providerSelector.selectedProvider?.bridge?.provider?.value ?: return null
    val selectedLaunchMode = providerSelector.selectedLaunchMode
    return builtInLaunchProfiles().firstOrNull { profile ->
      profile.providerId == selectedProviderId && profile.launchMode == selectedLaunchMode
    }?.id
  }

  private inner class ModelAction(
    private val modelId: String?,
    text: @NlsSafe String,
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
    private val planReasoningEffort: AgentPromptReasoningEffort?,
  ) : DumbAwareToggleAction(planReasoningEffortPopupText(planReasoningEffort)) {
    init {
      templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.Never
    }

    override fun isSelected(e: AnActionEvent): Boolean {
      return currentSettings().planReasoningEffort == planReasoningEffort
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (state) {
        selectPlanReasoningEffort(planReasoningEffort)
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

  private fun launchProfileText(profile: AgentPromptLaunchProfile?): @Nls String {
    val profileName = when {
      profile == null -> AgentPromptBundle.message("popup.profile.header.custom")
      profile.kind == AgentPromptLaunchProfileKind.BUILT_IN -> {
        providerSelector.compactBuiltInProfileLabel(profile) ?: AgentPromptBundle.message("popup.profile.header.standard")
      }
      else -> profile.name
    }
    return AgentPromptBundle.message("popup.profile.selected", profileName)
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

internal fun List<AgentPromptGenerationModel>.catalogReasoningEfforts(): Set<AgentPromptReasoningEffort>? {
  val efforts = flatMapTo(LinkedHashSet()) { model -> model.supportedReasoningEfforts }
  return efforts.takeIf { it.isNotEmpty() }
}

private fun modelText(
  modelId: String?,
  models: List<AgentPromptGenerationModel>,
  displayNameForSavedModel: (String) -> @NlsSafe String,
): @Nls String {
  if (modelId == null) {
    return AgentPromptBundle.message("popup.generation.model.auto")
  }
  val displayName = models.firstOrNull { model -> model.id == modelId }?.displayName ?: displayNameForSavedModel(modelId)
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

private fun planReasoningEffortText(planReasoningEffort: AgentPromptReasoningEffort?): @Nls String {
  return when (planReasoningEffort) {
    null -> AgentPromptBundle.message("popup.generation.plan.reasoning.same")
    AgentPromptReasoningEffort.AUTO -> AgentPromptBundle.message("popup.generation.plan.reasoning.provider.default")
    AgentPromptReasoningEffort.LOW -> AgentPromptBundle.message("popup.generation.plan.reasoning.low")
    AgentPromptReasoningEffort.MEDIUM -> AgentPromptBundle.message("popup.generation.plan.reasoning.medium")
    AgentPromptReasoningEffort.HIGH -> AgentPromptBundle.message("popup.generation.plan.reasoning.high")
    AgentPromptReasoningEffort.XHIGH -> AgentPromptBundle.message("popup.generation.plan.reasoning.xhigh")
    AgentPromptReasoningEffort.MAX -> AgentPromptBundle.message("popup.generation.plan.reasoning.max")
  }
}

private fun planReasoningEffortPopupText(planReasoningEffort: AgentPromptReasoningEffort?): @Nls String {
  return when (planReasoningEffort) {
    null -> AgentPromptBundle.message("popup.generation.plan.reasoning.popup.same")
    AgentPromptReasoningEffort.AUTO -> AgentPromptBundle.message("popup.generation.plan.reasoning.popup.provider.default")
    else -> reasoningEffortPopupText(planReasoningEffort)
  }
}
