// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptInvocationData
import com.intellij.agent.workbench.prompt.core.AgentPromptLauncherBridge
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.prompt.ui.context.dataContextOrNull
import com.intellij.ide.DataManager
import com.intellij.ide.setToolTipText
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
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
  private val modelSelectorLink: ActionLink,
  private val reasoningEffortLink: ActionLink,
  private val modelCatalogScope: CoroutineScope,
  private val modelCatalogService: AgentPromptGenerationModelCatalogService = invocationData.project.service(),
  private val launcherProvider: () -> AgentPromptLauncherBridge?,
  private val onDefaultSaved: () -> Unit,
) {
  private val defaultSettingsByProviderId = LinkedHashMap<String, AgentPromptGenerationSettings>()
  private val transientSettingsByProviderId = LinkedHashMap<String, AgentPromptGenerationSettings>()
  private val modelCatalogsByProviderId = LinkedHashMap<String, ModelCatalogState>()
  private var generationControlsVisible = true
  private var activeModelPopup: JBPopup? = null
  private var activeModelPopupProviderId: String? = null

  init {
    modelSelectorLink.addActionListener { showModelPopup() }
    reasoningEffortLink.addActionListener { showReasoningEffortPopup() }
  }

  fun restoreDefaultSettings(settingsByProviderId: Map<String, AgentPromptGenerationSettings>) {
    defaultSettingsByProviderId.clear()
    defaultSettingsByProviderId.putAll(settingsByProviderId)
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
                             ?: defaultSettingsByProviderId[providerId]
                             ?: AgentPromptGenerationSettings.AUTO
    val sanitizedSettings = provider.bridge.sanitizeGenerationSettings(configuredSettings)
    return sanitizeSettingsForLoadedModelCatalog(providerId, sanitizedSettings)
  }

  fun currentGenerationModelCatalog(): List<AgentPromptGenerationModel> {
    val provider = providerSelector.selectedProvider ?: return emptyList()
    return loadedModelCatalog(provider.bridge.provider.value).orEmpty()
  }

  fun refreshPresentation() {
    val selectedProvider = providerSelector.selectedProvider
    val showGenerationControls = generationControlsVisible
    val modelCatalog = selectedProvider?.bridge?.provider?.value?.let(::loadedModelCatalog)
    val modelSelectionAvailable = selectedProvider?.bridge?.supportsGenerationModelSelection == true
    val currentSettings = currentSettings()
    val reasoningEfforts = availableReasoningEfforts(currentSettings, modelCatalog)
    val reasoningEffortAvailable = reasoningEfforts.isNotEmpty()
    generationSettingsPanel.isVisible = showGenerationControls
    modelSelectorLink.isVisible = showGenerationControls && modelSelectionAvailable
    modelSelectorLink.isEnabled = showGenerationControls && modelSelectionAvailable
    reasoningEffortLink.isVisible = showGenerationControls
    reasoningEffortLink.isEnabled = showGenerationControls && reasoningEffortAvailable
    if (showGenerationControls) {
      modelSelectorLink.text = modelText(currentSettings.modelId, modelCatalog.orEmpty())
      modelSelectorLink.setToolTipText(HtmlChunk.text(AgentPromptBundle.message("popup.generation.model.tooltip")))
      modelSelectorLink.accessibleContext.accessibleName = modelSelectorLink.text
      reasoningEffortLink.text = reasoningEffortText(currentSettings.reasoningEffort)
      reasoningEffortLink.setToolTipText(HtmlChunk.text(reasoningEffortTooltipText(reasoningEffortAvailable)))
      reasoningEffortLink.accessibleContext.accessibleName = reasoningEffortLink.text
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
    return settings.copy(modelId = modelId, reasoningEffort = reasoningEffort)
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

  private fun saveCurrentSettingsAsDefault() {
    val providerId = providerSelector.selectedProvider?.bridge?.provider?.value ?: return
    val settings = currentSettings()
    updateStoredDefaultSettings { defaults ->
      if (settings.isAuto()) {
        defaults.remove(providerId)
      }
      else {
        defaults[providerId] = settings
      }
    }
  }

  private fun clearCurrentSettingsDefault() {
    val providerId = providerSelector.selectedProvider?.bridge?.provider?.value ?: return
    updateStoredDefaultSettings { defaults ->
      defaults.remove(providerId)
    }
  }

  private fun updateStoredDefaultSettings(update: (MutableMap<String, AgentPromptGenerationSettings>) -> Unit) {
    val launcher = launcherProvider() ?: return
    val currentPreferences = launcher.loadProviderPreferences()
    val updatedDefaults = LinkedHashMap(currentPreferences.generationSettingsByProviderId)
    update(updatedDefaults)
    launcher.saveProviderPreferences(currentPreferences.copy(generationSettingsByProviderId = updatedDefaults))
    defaultSettingsByProviderId.clear()
    defaultSettingsByProviderId.putAll(updatedDefaults)
    onDefaultSaved()
    refreshPresentation()
  }

  private enum class DefaultActionOperation {
    SAVE,
    CLEAR,
  }

  private fun defaultActionOperation(
    currentSettings: AgentPromptGenerationSettings,
    savedEffectiveSettings: AgentPromptGenerationSettings?,
  ): DefaultActionOperation? {
    if (savedEffectiveSettings != null && (currentSettings.isAuto() || currentSettings == savedEffectiveSettings)) {
      return DefaultActionOperation.CLEAR
    }
    if (!currentSettings.isAuto()) {
      return DefaultActionOperation.SAVE
    }
    return null
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
    addSaveDefaultAction(group)
    return group
  }

  private fun addModelActions(group: DefaultActionGroup, models: List<AgentPromptGenerationModel>) {
    if (models.isEmpty()) {
      group.add(ModelCatalogStatusAction(AgentPromptBundle.message("popup.generation.model.empty")))
    }
    else {
      models.forEach { model -> group.add(ModelAction(modelId = model.id, text = model.displayName)) }
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
    addSaveDefaultAction(group)
    return group
  }

  private fun addSaveDefaultAction(group: DefaultActionGroup) {
    val action = createSaveDefaultAction() ?: return
    group.add(Separator.getInstance())
    group.add(action)
  }

  private fun createSaveDefaultAction(): SaveDefaultAction? {
    val selectedProvider = providerSelector.selectedProvider ?: return null
    val providerId = selectedProvider.bridge.provider.value
    val currentSettings = currentSettings()
    val savedSettings = defaultSettingsByProviderId[providerId]
    val savedEffectiveSettings = if (savedSettings != null && !savedSettings.isAuto()) {
      sanitizeSettingsForLoadedModelCatalog(
        providerId = providerId,
        settings = selectedProvider.bridge.sanitizeGenerationSettings(savedSettings),
      )
    }
    else {
      null
    }
    return when (defaultActionOperation(currentSettings, savedEffectiveSettings)) {
      DefaultActionOperation.SAVE -> SaveDefaultAction(
        text = AgentPromptBundle.message("popup.generation.save.default"),
        description = AgentPromptBundle.message("popup.generation.save.default.description"),
        operation = DefaultActionOperation.SAVE,
      )
      DefaultActionOperation.CLEAR -> SaveDefaultAction(
        text = AgentPromptBundle.message("popup.generation.clear.default"),
        description = AgentPromptBundle.message("popup.generation.clear.default.description"),
        operation = DefaultActionOperation.CLEAR,
      )
      null -> null
    }
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

  private inner class SaveDefaultAction(
    text: @Nls String,
    description: @Nls String,
    private val operation: DefaultActionOperation,
  ) : DumbAwareAction(text) {
    init {
      templatePresentation.description = description
    }

    override fun actionPerformed(e: AnActionEvent) {
      when (operation) {
        DefaultActionOperation.SAVE -> saveCurrentSettingsAsDefault()
        DefaultActionOperation.CLEAR -> clearCurrentSettingsDefault()
      }
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

private fun AgentPromptGenerationSettings.isAuto(): Boolean {
  return modelId == null && reasoningEffort == AgentPromptReasoningEffort.AUTO
}

private fun List<AgentPromptGenerationModel>.catalogReasoningEfforts(): Set<AgentPromptReasoningEffort>? {
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

private fun reasoningEffortOrder(): List<AgentPromptReasoningEffort> {
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

private fun reasoningEffortPopupText(effort: AgentPromptReasoningEffort): @Nls String {
  return when (effort) {
    AgentPromptReasoningEffort.AUTO -> AgentPromptBundle.message("popup.generation.reasoning.popup.auto")
    AgentPromptReasoningEffort.LOW -> AgentPromptBundle.message("popup.generation.reasoning.popup.low")
    AgentPromptReasoningEffort.MEDIUM -> AgentPromptBundle.message("popup.generation.reasoning.popup.medium")
    AgentPromptReasoningEffort.HIGH -> AgentPromptBundle.message("popup.generation.reasoning.popup.high")
    AgentPromptReasoningEffort.XHIGH -> AgentPromptBundle.message("popup.generation.reasoning.popup.xhigh")
    AgentPromptReasoningEffort.MAX -> AgentPromptBundle.message("popup.generation.reasoning.popup.max")
  }
}
