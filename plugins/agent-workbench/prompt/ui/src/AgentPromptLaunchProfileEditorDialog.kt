// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.common.session.AgentSessionLaunchMode
import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModelGroup
import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationSettings
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfile
import com.intellij.agent.workbench.prompt.core.AgentPromptLaunchProfileKind
import com.intellij.agent.workbench.prompt.core.AgentPromptReasoningEffort
import com.intellij.agent.workbench.sessions.core.providers.launchProfileMatchesBuiltIn
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ide.ui.laf.darcula.ui.DarculaJBPopupComboPopup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.GroupedComboBoxRenderer
import com.intellij.ui.PopupMenuListenerAdapter
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.PlatformIcons
import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.awt.Font
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.ButtonGroup
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.Icon
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent
import javax.swing.event.PopupMenuEvent

internal class AgentPromptLaunchProfileEditorDialog(
  project: Project,
  profiles: List<AgentPromptLaunchProfile>,
  activeProfileId: String?,
  defaultProfileId: String?,
  builtInProfiles: List<AgentPromptLaunchProfile>,
  private var providerEntries: List<ProviderEntry>,
  private val modelCatalogProvider: (String) -> List<AgentPromptGenerationModel>?,
  private val modelCatalogStateProvider: (String) -> AgentPromptGenerationModelCatalogState? = { providerId ->
    modelCatalogProvider(providerId)?.let(AgentPromptGenerationModelCatalogState::Loaded)
  },
  private val requestModelCatalogRefresh: (String, () -> Unit) -> Unit = { _, _ -> },
  private val newUserProfileId: () -> String,
  private val onCreateProfile: (AgentPromptLaunchProfile) -> Unit,
  private val onUpdateProfile: (AgentPromptLaunchProfile) -> Unit,
  private val onDeleteProfile: (AgentPromptLaunchProfile) -> Boolean,
  private val onSetDefaultProfile: (AgentPromptLaunchProfile) -> Unit,
  private val onSelectProfile: (AgentPromptLaunchProfile?) -> Unit,
  private val onDispose: () -> Unit = {},
) : DialogWrapper(project) {
  private var managedProfiles: List<AgentPromptLaunchProfile> = profiles
  private var currentBuiltInProfiles: List<AgentPromptLaunchProfile> = builtInProfiles
  private var selectedProfileId: String? = activeProfileId
  private var currentDefaultProfileId: String? = defaultProfileId
  private var currentLaunchModes: Set<AgentSessionLaunchMode> = emptySet()
  private var isUpdatingEditor = false
  private var isChangingSelection = false
  private var selectedModelIdForEditor: String? = null

  private val profileListModel = DefaultListModel<AgentPromptLaunchProfile>()
  private val profileList = JBList(profileListModel).apply {
    selectionMode = ListSelectionModel.SINGLE_SELECTION
    cellRenderer = ProfileListCellRenderer()
    fixedCellWidth = JBUI.scale(PROFILE_LIST_WIDTH)
    emptyText.text = AgentPromptBundle.message("popup.profile.manage.empty")
    accessibleContext.accessibleName = AgentPromptBundle.message("popup.profile.manage.list.accessible.name")
  }

  private val nameField = JBTextField().apply {
    emptyText.text = AgentPromptBundle.message("popup.profile.name.default")
  }
  private val providerCombo = ComboBox<ProviderOption>()
  private val standardModeButton = JBRadioButton(launchModeText(AgentSessionLaunchMode.STANDARD))
  private val yoloModeButton = JBRadioButton(launchModeText(AgentSessionLaunchMode.YOLO))
  private val launchModePanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
    isOpaque = false
    ButtonGroup().apply {
      add(standardModeButton)
      add(yoloModeButton)
    }
    standardModeButton.border = JBUI.Borders.emptyRight(12)
    add(standardModeButton)
    add(yoloModeButton)
  }
  private val modelCombo = ComboBox<ModelOption>()
  private val effortCombo = ComboBox<ReasoningEffortOption>()
  private val planEffortCombo = ComboBox<PlanEffortOption>()
  private var planEffortLabel: JBLabel? = null
  private val statusLabel = JBLabel(" ")
  private val setDefaultButton = JButton(AgentPromptBundle.message("popup.profile.set.default"))

  init {
    title = AgentPromptBundle.message("popup.profile.manage.title")
    isModal = false
    initModels()
    profileList.addListSelectionListener {
      if (!it.valueIsAdjusting) {
        handleProfileSelectionChange()
        renderSelectedProfile()
      }
    }
    installProfileListRenameActions()
    providerCombo.addActionListener {
      if (!isUpdatingEditor) {
        refreshLaunchModeOptions()
        refreshModelOptions(selectedModelId = null)
        refreshPlanEffortOptions()
        handleEditorChanged()
      }
    }
    modelCombo.addActionListener {
      if (!isUpdatingEditor) {
        val selectedOption = selectedModelOption()
        when {
          selectedOption?.retryProviderId != null -> {
            requestSelectedProviderModelRefresh()
          }
          selectedOption?.selectable == true -> {
            selectedModelIdForEditor = selectedOption.modelId
            refreshEffortOptions()
            refreshPlanEffortOptions()
            handleEditorChanged()
          }
          else -> {
            refreshModelOptions(selectedModelId = selectedModelIdForEditor)
            handleEditorChanged()
          }
        }
      }
    }
    standardModeButton.addActionListener {
      if (!isUpdatingEditor) {
        handleEditorChanged()
      }
    }
    yoloModeButton.addActionListener {
      if (!isUpdatingEditor) {
        handleEditorChanged()
      }
    }
    planEffortCombo.addActionListener {
      if (!isUpdatingEditor) {
        handleEditorChanged()
      }
    }
    nameField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        handleEditorChanged()
      }
    })
    setDefaultButton.addActionListener { setSelectedProfileAsDefault() }
    init()
    selectProfile(selectedProfileId ?: managedProfiles.firstOrNull()?.id)
    renderSelectedProfile()
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return profileList
  }

  override fun getDimensionServiceKey(): String {
    return DIMENSION_SERVICE_KEY
  }

  override fun createCenterPanel(): JComponent {
    return JPanel(BorderLayout(JBUI.scale(12), 0)).apply {
      border = JBUI.Borders.empty(8)
      preferredSize = JBUI.size(DIALOG_WIDTH, DIALOG_HEIGHT)
      add(createProfileListPanel(), BorderLayout.WEST)
      add(createDetailsPanel(), BorderLayout.CENTER)
    }
  }

  override fun createActions(): Array<Action> {
    okAction.putValue(Action.NAME, AgentPromptBundle.message("popup.profile.editor.close"))
    return arrayOf(okAction)
  }

  override fun dispose() {
    try {
      super.dispose()
    }
    finally {
      onDispose()
    }
  }

  fun refreshProfilesIfUnmodified(
    profiles: List<AgentPromptLaunchProfile>,
    activeProfileId: String?,
    defaultProfileId: String?,
    builtInProfiles: List<AgentPromptLaunchProfile>,
    providerEntries: List<ProviderEntry>,
  ) {
    if (hasEditorChanges(selectedProfile())) {
      return
    }
    managedProfiles = profiles
    currentBuiltInProfiles = builtInProfiles
    selectedProfileId = activeProfileId
    currentDefaultProfileId = defaultProfileId
    this.providerEntries = providerEntries
    reloadListAndSelectProfile(selectedProfileId ?: managedProfiles.firstOrNull()?.id)
    renderSelectedProfile()
  }

  fun showOrFocus() {
    if (!isVisible) {
      show()
      return
    }
    val window = peer.window ?: return
    if (window is Frame && window.extendedState and Frame.ICONIFIED != 0) {
      window.extendedState = window.extendedState and Frame.ICONIFIED.inv()
    }
    window.toFront()
    window.requestFocus()
    preferredFocusedComponent.requestFocusInWindow()
  }

  fun isModalForTest(): Boolean {
    return isModal
  }

  fun profileNamesForTest(): List<String> {
    return managedProfiles.map { profile -> profile.name }
  }

  fun selectProfileForTest(profileId: String) {
    selectProfile(profileId)
  }

  fun isSelectedProfileEditableForTest(): Boolean {
    return selectedProfile() != null
  }

  fun isSelectedProfileUnavailableForTest(): Boolean {
    return selectedProfile()?.let { profile -> providerOption(profile.providerId)?.isAvailable != true } == true
  }

  fun setSelectedProfileNameForTest(name: String) {
    nameField.text = name
  }

  fun selectedProfileNameForTest(): String {
    return nameField.text
  }

  fun selectSelectedProfileProviderForTest(providerId: String) {
    providerCombo.selectedItem = (0 until providerCombo.model.size)
      .asSequence()
      .map { index -> providerCombo.model.getElementAt(index) }
      .first { option -> option.providerId == providerId }
  }

  fun selectedProfileModelIdForTest(): String? {
    return selectedModelOption()?.modelId
  }

  fun isPlanEffortVisibleForTest(): Boolean {
    return planEffortCombo.isVisible && planEffortLabel?.isVisible == true
  }

  fun planEffortOptionTextsForTest(): List<String> {
    return (0 until planEffortCombo.model.size).map { index -> planEffortCombo.model.getElementAt(index).displayName }
  }

  fun selectPlanEffortForTest(planReasoningEffort: AgentPromptReasoningEffort?) {
    planEffortCombo.selectedItem = (0 until planEffortCombo.model.size)
      .asSequence()
      .map { index -> planEffortCombo.model.getElementAt(index) }
      .first { option -> option.planReasoningEffort == planReasoningEffort }
  }

  fun renameSelectedProfileForTest() {
    renameSelectedProfile()
  }

  fun isNameFieldTextSelectedForTest(): Boolean {
    return nameField.selectedText == nameField.text
  }

  fun isSelectedProfileRemovableForTest(): Boolean {
    return selectedProfile()?.let(::isRemovableProfile) == true
  }

  fun deleteSelectedProfileForTest() {
    deleteSelectedProfile()
  }

  fun copySelectedProfileForTest() {
    copySelectedProfile()
  }

  fun selectedLaunchModeForTest(): AgentSessionLaunchMode? {
    return selectedLaunchMode()
  }

  fun isLaunchModeEnabledForTest(mode: AgentSessionLaunchMode): Boolean {
    return when (mode) {
      AgentSessionLaunchMode.STANDARD -> standardModeButton.isEnabled
      AgentSessionLaunchMode.YOLO -> yoloModeButton.isEnabled
    }
  }

  fun profileListFixedCellWidthForTest(): Int {
    return profileList.fixedCellWidth
  }

  fun profileListRendererTextForTest(profileId: String): String {
    val component = profileListRendererComponentForTest(profileId) ?: return ""
    return (component as SimpleColoredComponent).getCharSequence(false).toString()
  }

  fun isProfileListRendererNameBoldForTest(profileId: String): Boolean {
    val component = profileListRendererComponentForTest(profileId) ?: return false
    val iterator = (component as SimpleColoredComponent).iterator()
    return iterator.hasNext() && iterator.next() == profileListRendererTextForTest(profileId) && iterator.textAttributes.fontStyle == Font.BOLD
  }

  fun modelOptionTextsForTest(): List<String> {
    return (0 until modelCombo.model.size).map { index -> modelCombo.model.getElementAt(index).displayName }
  }

  fun modelOptionSeparatorTextsForTest(): List<String?> {
    return (0 until modelCombo.model.size).map { index ->
      modelCombo.model.getElementAt(index).separatorGroup?.modelSelectorText()
    }
  }

  fun openModelComboForTest() {
    val event = PopupMenuEvent(modelCombo)
    modelCombo.popupMenuListeners.forEach { listener -> listener.popupMenuWillBecomeVisible(event) }
  }

  fun modelOptionIconsForTest(): List<Icon?> {
    return (0 until modelCombo.model.size).map { index -> modelCombo.model.getElementAt(index).icon }
  }

  @Suppress("UnstableApiUsage")
  fun isModelComboLiveUpdateEnabledForTest(): Boolean {
    return modelCombo.getClientProperty(DarculaJBPopupComboPopup.USE_LIVE_UPDATE_MODEL) == true
  }

  private fun profileListRendererComponentForTest(profileId: String): Component? {
    val index = managedProfiles.indexOfFirst { profile -> profile.id == profileId }
    val profile = managedProfiles.getOrNull(index) ?: return null
    return profileList.cellRenderer.getListCellRendererComponent(profileList, profile, index, false, false)
  }

  private fun createProfileListPanel(): JComponent {
    return ToolbarDecorator.createDecorator(profileList)
      .disableAddAction()
      .setEditActionUpdater { false }
      .addExtraAction(CopyProfileAction())
      .setRemoveAction { deleteSelectedProfile() }
      .setRemoveActionName(AgentPromptBundle.message("popup.profile.delete"))
      .setRemoveActionUpdater { selectedProfile()?.let(::isRemovableProfile) == true }
      .disableUpDownActions()
      .setPreferredSize(JBUI.size(PROFILE_LIST_WIDTH, PROFILE_LIST_HEIGHT))
      .createPanel()
  }

  private fun installProfileListRenameActions() {
    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        return renameSelectedProfile()
      }
    }.installOn(profileList)
    profileList.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), RENAME_PROFILE_ACTION_ID)
    profileList.actionMap.put(RENAME_PROFILE_ACTION_ID, object : AbstractAction() {
      override fun actionPerformed(e: ActionEvent) {
        renameSelectedProfile()
      }
    })
  }

  private fun createDetailsPanel(): JComponent {
    val panel = JPanel(GridBagLayout()).apply { isOpaque = false }
    var row = 0
    panel.addLabeledField(row++, AgentPromptBundle.message("popup.profile.manage.column.name"), nameField)
    panel.addLabeledField(row++, AgentPromptBundle.message("popup.profile.manage.column.provider"), providerCombo)
    panel.addLabeledField(row++, AgentPromptBundle.message("popup.profile.manage.column.mode"), launchModePanel)
    panel.addLabeledField(row++, AgentPromptBundle.message("popup.profile.group.model"), modelCombo)
    panel.addLabeledField(row++, AgentPromptBundle.message("popup.profile.group.effort"), effortCombo)
    planEffortLabel = panel.addLabeledField(row++, AgentPromptBundle.message("popup.profile.group.plan.effort"), planEffortCombo)
    panel.add(statusLabel, GridBagConstraints().apply {
      gridx = 0
      gridy = row++
      gridwidth = 2
      fill = GridBagConstraints.HORIZONTAL
      weightx = 1.0
      insets = JBUI.insets(4, 0, 8, 0)
    })
    panel.add(createButtonPanel(), GridBagConstraints().apply {
      gridx = 0
      gridy = row
      gridwidth = 2
      fill = GridBagConstraints.HORIZONTAL
      weightx = 1.0
      weighty = 1.0
      anchor = GridBagConstraints.NORTHWEST
    })
    installAccessibleLabels(panel)
    return panel
  }

  private fun createButtonPanel(): JPanel {
    return JPanel().apply {
      isOpaque = false
      add(setDefaultButton)
    }
  }

  private fun installAccessibleLabels(panel: JPanel) {
    for (component in panel.components) {
      val label = component as? JLabel ?: continue
      val target = label.labelFor ?: continue
      target.accessibleContext.accessibleName = label.text
    }
  }

  private fun initModels() {
    reloadList()
    providerCombo.renderer = ProviderOptionRenderer()
    modelCombo.isSwingPopup = false
    @Suppress("UnstableApiUsage")
    modelCombo.putClientProperty(DarculaJBPopupComboPopup.USE_LIVE_UPDATE_MODEL, true)
    modelCombo.addPopupMenuListener(object : PopupMenuListenerAdapter() {
      override fun popupMenuWillBecomeVisible(e: PopupMenuEvent) {
        requestSelectedProviderModelRefresh()
      }
    })
    modelCombo.renderer = ModelOptionRenderer(modelCombo)
    effortCombo.renderer = ReasoningEffortOptionRenderer()
    planEffortCombo.renderer = PlanEffortOptionRenderer()
  }

  private fun reloadList() {
    profileListModel.clear()
    managedProfiles.forEach(profileListModel::addElement)
  }

  private fun reloadListAndSelectProfile(profileId: String?) {
    withInternalSelectionChange {
      reloadList()
      selectProfileIndex(profileId)
    }
  }

  private fun profileById(profileId: String?): AgentPromptLaunchProfile? {
    if (profileId == null) return null
    return managedProfiles.firstOrNull { profile -> profile.id == profileId }
  }

  private fun selectedProfile(): AgentPromptLaunchProfile? {
    return profileList.selectedValue
  }

  private fun selectProfile(profileId: String?) {
    withInternalSelectionChange {
      selectProfileIndex(profileId)
    }
  }

  private fun selectProfileIndex(profileId: String?) {
    val index = managedProfiles.indexOfFirst { profile -> profile.id == profileId }
    if (index >= 0) {
      profileList.selectedIndex = index
      selectedProfileId = profileId
    }
    else {
      profileList.clearSelection()
      selectedProfileId = null
    }
  }

  private inline fun withInternalSelectionChange(action: () -> Unit) {
    val wasChangingSelection = isChangingSelection
    isChangingSelection = true
    try {
      action()
    }
    finally {
      isChangingSelection = wasChangingSelection
    }
  }

  private fun handleProfileSelectionChange() {
    if (isChangingSelection) {
      return
    }
    val previousProfile = profileById(selectedProfileId)
    val newProfile = selectedProfile()
    if (previousProfile?.id == newProfile?.id) {
      return
    }
    selectedProfileId = newProfile?.id
    onSelectProfile(newProfile)
  }

  private fun renderSelectedProfile() {
    val profile = selectedProfile()
    isUpdatingEditor = true
    try {
      nameField.text = profile?.name.orEmpty()
      refreshProviderOptions(profile)
      refreshLaunchModeOptions(profile)
      refreshModelOptions(profile)
      refreshEffortOptions(profile)
      refreshPlanEffortOptions(profile)
    }
    finally {
      isUpdatingEditor = false
    }
    updateButtonState()
  }

  private fun refreshProviderOptions(profile: AgentPromptLaunchProfile? = selectedProfile()) {
    val selectedProviderId = profile?.providerId
    val options = buildList {
      if (selectedProviderId != null && providerOption(selectedProviderId) == null) {
        add(ProviderOption(providerId = selectedProviderId,
                           displayName = selectedProviderId,
                           isAvailable = false,
                           icon = AllIcons.Nodes.Plugin))
      }
      providerEntries.forEach { entry ->
        add(ProviderOption(entry.bridge.provider.value, entry.displayName, entry.isCliAvailable, entry.icon))
      }
    }
    providerCombo.model = DefaultComboBoxModel(options.toTypedArray())
    providerCombo.selectedItem = options.firstOrNull { option -> option.providerId == selectedProviderId } ?: options.firstOrNull()
  }

  private fun refreshLaunchModeOptions(profile: AgentPromptLaunchProfile? = selectedProfile()) {
    val selectedProvider = selectedProviderOption()
    val providerEntry = providerEntry(selectedProvider?.providerId)
    currentLaunchModes = providerEntry?.bridge?.supportedLaunchModes.orEmpty()
      .ifEmpty { setOf(profile?.launchMode ?: AgentSessionLaunchMode.STANDARD) }
    val selectedMode = when {
      profile?.launchMode in currentLaunchModes -> profile?.launchMode
      AgentSessionLaunchMode.STANDARD in currentLaunchModes -> AgentSessionLaunchMode.STANDARD
      else -> currentLaunchModes.firstOrNull()
    }
    standardModeButton.isSelected = selectedMode == AgentSessionLaunchMode.STANDARD
    yoloModeButton.isSelected = selectedMode == AgentSessionLaunchMode.YOLO
  }

  private fun refreshModelOptions(profile: AgentPromptLaunchProfile? = selectedProfile()) {
    val providerId = selectedProviderOption()?.providerId ?: profile?.providerId
    val selectedModelId = profile?.generationSettings?.modelId ?: selectedModelIdForEditor
    selectedModelIdForEditor = selectedModelId
    val options = providerId
                    ?.let { id -> buildModelOptions(id, modelCatalogStateProvider(id), selectedModelId) }
                  ?: listOf(ModelOption(null, AgentPromptBundle.message("popup.generation.model.popup.auto")))
    val wasUpdatingEditor = isUpdatingEditor
    isUpdatingEditor = true
    try {
      modelCombo.model = DefaultComboBoxModel(options.toTypedArray())
      modelCombo.selectedItem = options.firstOrNull { option -> option.selectable && option.modelId == selectedModelId }
                                ?: options.firstOrNull { option -> option.selectable }
    }
    finally {
      isUpdatingEditor = wasUpdatingEditor
    }
  }

  private fun refreshModelOptions(selectedModelId: String?) {
    selectedModelIdForEditor = selectedModelId
    refreshModelOptions(profile = null)
    refreshEffortOptions()
    refreshPlanEffortOptions()
  }

  private fun requestSelectedProviderModelRefresh() {
    val providerId = selectedProviderOption()?.providerId ?: return
    requestModelCatalogRefresh(providerId) {
      if (isDisposed) {
        return@requestModelCatalogRefresh
      }
      refreshModelOptions(selectedModelId = selectedModelIdForEditor)
      refreshPlanEffortOptions()
      updateButtonState()
    }
    refreshModelOptions(selectedModelId = selectedModelIdForEditor)
    refreshPlanEffortOptions()
  }

  private fun buildModelOptions(
    providerId: String,
    catalogState: AgentPromptGenerationModelCatalogState?,
    selectedModelId: String?,
  ): List<ModelOption> {
    return buildGenerationModelSelectorEntries(providerId, catalogState, selectedModelId) { modelId ->
      providerEntry(providerId)?.bridge?.displayNameForGenerationModelId(modelId)
      ?: unknownGenerationModelDisplayName(modelId)
    }.map { entry ->
      when (entry) {
        is AgentPromptGenerationModelSelectorEntry.Model -> ModelOption(
          modelId = entry.modelId,
          displayName = entry.displayName,
          separatorGroup = entry.separatorGroup,
        )
        is AgentPromptGenerationModelSelectorEntry.Status -> ModelOption(
          modelId = null,
          displayName = entry.displayName,
          selectable = false,
          icon = modelCatalogStatusIcon(entry.kind),
        )
        is AgentPromptGenerationModelSelectorEntry.Retry -> ModelOption(
          modelId = null,
          displayName = entry.displayName,
          selectable = false,
          retryProviderId = entry.providerId,
        )
      }
    }
  }

  private fun refreshEffortOptions(profile: AgentPromptLaunchProfile? = selectedProfile()) {
    val supportedEfforts = selectedProviderReasoningEfforts()
    val efforts = reasoningEffortOrder()
      .filter { effort -> effort == AgentPromptReasoningEffort.AUTO || effort in supportedEfforts }
      .map { effort -> ReasoningEffortOption(effort, reasoningEffortPopupText(effort)) }
    effortCombo.model = DefaultComboBoxModel(efforts.toTypedArray())
    effortCombo.selectedItem =
      efforts.firstOrNull { option -> option.effort == profile?.generationSettings?.reasoningEffort } ?: efforts.first()
  }

  private fun refreshPlanEffortOptions(profile: AgentPromptLaunchProfile? = selectedProfile()) {
    val supportedEfforts = selectedProviderReasoningEfforts()
    val visible = isSelectedProviderPlanEffortAvailable(supportedEfforts)
    planEffortLabel?.isVisible = visible
    planEffortCombo.isVisible = visible
    val options = planEffortOptions(supportedEfforts)
    planEffortCombo.model = DefaultComboBoxModel(options.toTypedArray())
    val selectedPlanEffort = sanitizePlanReasoningEffort(profile?.generationSettings?.planReasoningEffort, supportedEfforts)
    planEffortCombo.selectedItem = options.firstOrNull { option -> option.planReasoningEffort == selectedPlanEffort }
                                   ?: options.first()
  }

  private fun updateButtonState() {
    val profile = selectedProfile()
    val draft = currentEditorDraft(profile)
    val draftValid = draft != null
    nameField.isEnabled = profile != null
    providerCombo.isEnabled = profile != null
    updateLaunchModeControlState(profile != null)
    modelCombo.isEnabled = profile != null
    effortCombo.isEnabled = profile != null
    planEffortCombo.isEnabled = profile != null && planEffortCombo.isVisible
    setDefaultButton.isEnabled =
      profile != null && draftValid && profile.id != currentDefaultProfileId && providerOption(profile.providerId)?.isAvailable == true
    val editorStatusText = statusText(profile, draftValid)
    statusLabel.text = editorStatusText
    updateDetailsAccessibleDescription(editorStatusText.ifBlank { null })
  }

  private fun handleEditorChanged() {
    if (isUpdatingEditor) {
      return
    }
    val profile = selectedProfile()
    val draft = currentEditorDraft(profile)
    updateButtonState()
    if (profile == null || draft == null || draft == profile) {
      return
    }
    saveProfileDraft(draft)
  }

  private fun updateDetailsAccessibleDescription(description: @Nls String?) {
    listOf(
      nameField,
      providerCombo,
      launchModePanel,
      standardModeButton,
      yoloModeButton,
      modelCombo,
      effortCombo,
      planEffortCombo,
    ).forEach { component ->
      component.accessibleContext.accessibleDescription = description
    }
  }

  private fun hasEditorChanges(profile: AgentPromptLaunchProfile?): Boolean {
    profile ?: return false
    return nameField.text.trim() != profile.name ||
           selectedProviderOption()?.providerId != profile.providerId ||
           selectedLaunchMode() != profile.launchMode ||
           selectedModelIdForEditor != profile.generationSettings.modelId ||
           selectedReasoningEffortOption()?.effort != profile.generationSettings.reasoningEffort ||
           selectedPlanReasoningEffort() != profile.generationSettings.planReasoningEffort
  }

  private fun updateLaunchModeControlState(enabled: Boolean) {
    launchModePanel.isEnabled = enabled
    standardModeButton.isEnabled = enabled && AgentSessionLaunchMode.STANDARD in currentLaunchModes
    yoloModeButton.isEnabled = enabled && AgentSessionLaunchMode.YOLO in currentLaunchModes
  }

  private fun statusText(profile: AgentPromptLaunchProfile?, draftValid: Boolean): @Nls String {
    if (profile == null) return " "
    if (providerOption(profile.providerId)?.isAvailable != true) {
      return AgentPromptBundle.message("popup.profile.editor.status.unavailable")
    }
    if (!draftValid) {
      return AgentPromptBundle.message("popup.profile.editor.status.invalid")
    }
    if (profile.id == currentDefaultProfileId) {
      return AgentPromptBundle.message("popup.profile.editor.status.default")
    }
    if (isCustomizedBuiltInProfile(profile)) {
      return AgentPromptBundle.message("popup.profile.editor.status.customized.builtin")
    }
    return AgentPromptBundle.message("popup.profile.editor.status.ready")
  }

  private fun copySelectedProfile() {
    val profile = selectedProfile() ?: return
    val draft = currentEditorDraft(profile) ?: return
    val copy = draft.copy(
      id = newUserProfileId(),
      name = generatedLaunchProfileName(
        profile = draft,
        existingProfiles = managedProfiles,
        models = modelCatalogProvider(draft.providerId).orEmpty(),
        compactLaunchModeLabel = AgentPromptBundle.message("popup.profile.generated.full.auto"),
      ),
      kind = AgentPromptLaunchProfileKind.USER,
    )
    onCreateProfile(copy)
    managedProfiles = managedProfiles + copy
    reloadListAndSelectProfile(copy.id)
    renderSelectedProfile()
    nameField.requestFocusInWindow()
    nameField.selectAll()
  }

  private fun renameSelectedProfile(): Boolean {
    selectedProfile() ?: return false
    nameField.requestFocusInWindow()
    nameField.selectAll()
    return true
  }

  private fun saveProfileDraft(updated: AgentPromptLaunchProfile) {
    val index = managedProfiles.indexOfFirst { item -> item.id == updated.id }
    if (index < 0) {
      return
    }
    onUpdateProfile(updated)
    val builtInProfile = builtInProfile(updated.id)
    val effectiveProfile = if (builtInProfile != null && launchProfileMatchesBuiltIn(updated, builtInProfile)) builtInProfile else updated
    managedProfiles = managedProfiles.toMutableList().apply {
      this[index] = effectiveProfile
    }
    profileListModel.set(index, effectiveProfile)
    selectedProfileId = effectiveProfile.id
    profileList.repaint()
    updateButtonState()
  }

  private fun setSelectedProfileAsDefault() {
    val profile = selectedProfile() ?: return
    onSetDefaultProfile(profile)
    currentDefaultProfileId = profile.id
    profileList.repaint()
    updateButtonState()
  }

  private fun deleteSelectedProfile() {
    val profile = selectedProfile()?.takeIf(::isRemovableProfile) ?: return
    if (!onDeleteProfile(profile)) {
      return
    }
    val builtInProfile = builtInProfile(profile.id)
    managedProfiles = if (builtInProfile != null) {
      managedProfiles.map { item -> if (item.id == profile.id) builtInProfile else item }
    }
    else {
      managedProfiles.filterNot { item -> item.id == profile.id }
    }
    if (selectedProfileId == profile.id && builtInProfile == null) {
      selectedProfileId = managedProfiles.firstOrNull()?.id
    }
    if (currentDefaultProfileId == profile.id && builtInProfile == null) {
      currentDefaultProfileId = null
    }
    reloadListAndSelectProfile(selectedProfileId)
    renderSelectedProfile()
  }

  private fun currentEditorDraft(existing: AgentPromptLaunchProfile?): AgentPromptLaunchProfile? {
    val name = nameField.text.trim().takeIf { it.isNotEmpty() } ?: return null
    val provider = selectedProviderOption()?.takeIf { option -> option.isAvailable } ?: return null
    val launchMode = selectedLaunchMode() ?: return null
    val modelId = selectedModelIdForEditor
    val reasoningEffort = selectedReasoningEffortOption()?.effort ?: AgentPromptReasoningEffort.AUTO
    val planReasoningEffort = selectedPlanReasoningEffort()
    return AgentPromptLaunchProfile(
      id = existing?.id ?: "",
      name = name,
      kind = existing?.kind ?: AgentPromptLaunchProfileKind.USER,
      providerId = provider.providerId,
      launchMode = launchMode,
      generationSettings = AgentPromptGenerationSettings(
        modelId = modelId,
        reasoningEffort = reasoningEffort,
        planReasoningEffort = planReasoningEffort,
      ),
    )
  }

  private fun selectedProviderReasoningEfforts(): Set<AgentPromptReasoningEffort> {
    val selectedProvider = selectedProviderOption()
    val providerEntry = providerEntry(selectedProvider?.providerId)
    val selectedModelId = selectedModelIdForEditor
    val models = selectedProvider?.providerId?.let { providerId -> modelCatalogStateProvider(providerId)?.modelsOrNull() }.orEmpty()
    val modelEfforts = selectedModelId
      ?.let { modelId -> models.firstOrNull { model -> model.id == modelId } }
      ?.supportedReasoningEfforts
      ?.takeIf { efforts -> efforts.isNotEmpty() }
    return modelEfforts
           ?: models.catalogReasoningEfforts()
           ?: providerEntry?.bridge?.supportedReasoningEfforts.orEmpty()
  }

  private fun isSelectedProviderPlanEffortAvailable(supportedEfforts: Set<AgentPromptReasoningEffort>): Boolean {
    if (supportedEfforts.isEmpty()) {
      return false
    }
    val providerEntry = providerEntry(selectedProviderOption()?.providerId) ?: return false
    return providerEntry.bridge.supportsPlanReasoningEffort
  }

  private fun planEffortOptions(supportedEfforts: Set<AgentPromptReasoningEffort>): List<PlanEffortOption> {
    return buildList {
      add(
        PlanEffortOption(
          null,
          planReasoningEffortPopupText(null),
        )
      )
      add(
        PlanEffortOption(
          AgentPromptReasoningEffort.AUTO,
          planReasoningEffortPopupText(AgentPromptReasoningEffort.AUTO),
        )
      )
      reasoningEffortOrder()
        .filter { effort -> effort != AgentPromptReasoningEffort.AUTO && effort in supportedEfforts }
        .forEach { effort ->
          add(PlanEffortOption(effort, planReasoningEffortPopupText(effort)))
        }
    }
  }

  private fun selectedPlanReasoningEffort(): AgentPromptReasoningEffort? {
    val supportedEfforts = selectedProviderReasoningEfforts()
    return if (isSelectedProviderPlanEffortAvailable(supportedEfforts)) {
      (planEffortCombo.selectedItem as? PlanEffortOption)?.planReasoningEffort
    }
    else {
      null
    }
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

  private fun isRemovableProfile(profile: AgentPromptLaunchProfile): Boolean {
    return profile.kind == AgentPromptLaunchProfileKind.USER || isCustomizedBuiltInProfile(profile)
  }

  private fun isCustomizedBuiltInProfile(profile: AgentPromptLaunchProfile): Boolean {
    val builtInProfile = builtInProfile(profile.id) ?: return false
    return !launchProfileMatchesBuiltIn(profile, builtInProfile)
  }

  private fun builtInProfile(profileId: String): AgentPromptLaunchProfile? {
    return currentBuiltInProfiles.firstOrNull { profile -> profile.id == profileId }
  }

  private fun providerOption(providerId: String): ProviderOption? {
    return providerEntries.firstOrNull { entry -> entry.bridge.provider.value == providerId }?.let { entry ->
      ProviderOption(entry.bridge.provider.value, entry.displayName, entry.isCliAvailable, entry.icon)
    }
  }

  private fun providerEntry(providerId: String?): ProviderEntry? {
    val provider = providerId?.let(AgentSessionProvider::fromOrNull) ?: return null
    return providerEntries.firstOrNull { entry -> entry.bridge.provider == provider }
  }

  private fun selectedProviderOption(): ProviderOption? = providerCombo.selectedItem as? ProviderOption

  private fun selectedLaunchMode(): AgentSessionLaunchMode? {
    return when {
      standardModeButton.isSelected -> AgentSessionLaunchMode.STANDARD
      yoloModeButton.isSelected -> AgentSessionLaunchMode.YOLO
      else -> null
    }
  }

  private fun selectedModelOption(): ModelOption? = modelCombo.selectedItem as? ModelOption

  private fun selectedReasoningEffortOption(): ReasoningEffortOption? = effortCombo.selectedItem as? ReasoningEffortOption

  private inner class ProfileListCellRenderer : ColoredListCellRenderer<AgentPromptLaunchProfile>() {
    override fun customizeCellRenderer(
      list: javax.swing.JList<out AgentPromptLaunchProfile>,
      value: AgentPromptLaunchProfile?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean,
    ) {
      icon = profileIcon(value)
      if (value == null) return
      val nameAttributes = if (value.id == currentDefaultProfileId) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
      else SimpleTextAttributes.REGULAR_ATTRIBUTES
      append(value.name, nameAttributes)
      if (providerOption(value.providerId)?.isAvailable != true) {
        append("  " + AgentPromptBundle.message("popup.profile.editor.unavailable.marker"), SimpleTextAttributes.ERROR_ATTRIBUTES)
      }
    }
  }

  private fun profileIcon(profile: AgentPromptLaunchProfile?): Icon {
    val provider = profile?.providerId?.let(AgentSessionProvider::fromOrNull)
    return providerEntries.firstOrNull { entry -> entry.bridge.provider == provider }?.icon ?: AllIcons.Nodes.Plugin
  }

  private companion object {
    const val RENAME_PROFILE_ACTION_ID: String = "renameProfile"
    const val PROFILE_LIST_WIDTH: Int = 260
    const val PROFILE_LIST_HEIGHT: Int = 320
    const val DIALOG_WIDTH: Int = 840
    const val DIALOG_HEIGHT: Int = 360
    const val DIMENSION_SERVICE_KEY: String = "AgentWorkbench.LaunchProfiles"
  }

  private inner class CopyProfileAction : AnAction(
    AgentPromptBundle.message("popup.profile.copy"),
    AgentPromptBundle.message("popup.profile.copy.description"),
    PlatformIcons.COPY_ICON,
  ) {
    override fun actionPerformed(e: AnActionEvent) {
      copySelectedProfile()
    }

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = selectedProfile()?.let { profile -> currentEditorDraft(profile) != null } == true
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
  }
}

private fun JPanel.addLabeledField(row: Int, labelText: @Nls String, component: JComponent): JBLabel {
  val label = JBLabel(labelText).apply { labelFor = component }
  add(label, GridBagConstraints().apply {
    gridx = 0
    gridy = row
    anchor = GridBagConstraints.WEST
    insets = JBUI.insets(0, 0, 8, 8)
  })
  add(component, GridBagConstraints().apply {
    gridx = 1
    gridy = row
    fill = GridBagConstraints.HORIZONTAL
    weightx = 1.0
    insets = JBUI.insetsBottom(8)
  })
  return label
}

private data class ProviderOption(
  @JvmField val providerId: String,
  @JvmField val displayName: @NlsSafe String,
  @JvmField val isAvailable: Boolean,
  @JvmField val icon: Icon,
) {
  override fun toString(): String = displayName
}

private data class ModelOption(
  @JvmField val modelId: String?,
  @JvmField val displayName: @NlsSafe String,
  @JvmField val separatorGroup: AgentPromptGenerationModelGroup? = null,
  @JvmField val selectable: Boolean = true,
  @JvmField val retryProviderId: String? = null,
  @JvmField val icon: Icon? = null,
) {
  override fun toString(): String = displayName
}

private data class ReasoningEffortOption(
  @JvmField val effort: AgentPromptReasoningEffort,
  @JvmField val displayName: @NlsSafe String,
) {
  override fun toString(): String = displayName
}

private data class PlanEffortOption(
  @JvmField val planReasoningEffort: AgentPromptReasoningEffort?,
  @JvmField val displayName: @NlsSafe String,
) {
  override fun toString(): String = displayName
}

private class ProviderOptionRenderer : PromptProfileComboRenderer<ProviderOption>() {
  override fun text(value: ProviderOption): @NlsSafe String {
    return if (value.isAvailable) value.displayName
    else AgentPromptBundle.message("popup.profile.editor.unavailable.format",
                                   value.displayName)
  }

  override fun icon(value: ProviderOption): Icon = value.icon
}

private class ModelOptionRenderer(component: JComponent) : GroupedComboBoxRenderer<ModelOption>(component) {
  override fun getText(item: ModelOption): @NlsSafe String = item.displayName

  override fun getIcon(item: ModelOption): Icon? = item.icon

  override fun separatorFor(value: ModelOption): ListSeparator? {
    return value.separatorGroup?.let { group -> ListSeparator(group.modelSelectorText()) }
  }
}

private class ReasoningEffortOptionRenderer : PromptProfileComboRenderer<ReasoningEffortOption>() {
  override fun text(value: ReasoningEffortOption): @NlsSafe String = value.displayName
}

private class PlanEffortOptionRenderer : PromptProfileComboRenderer<PlanEffortOption>() {
  override fun text(value: PlanEffortOption): @NlsSafe String = value.displayName
}

private abstract class PromptProfileComboRenderer<T> : javax.swing.DefaultListCellRenderer() {
  @Suppress("HardCodedStringLiteral")
  override fun getListCellRendererComponent(
    list: javax.swing.JList<*>,
    value: Any?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel

    @Suppress("UNCHECKED_CAST")
    val typedValue = value as? T
    component.text = typedValue?.let(::text).orEmpty()
    component.icon = typedValue?.let(::icon)
    return component
  }

  protected abstract fun text(value: T): @NlsSafe String

  protected open fun icon(value: T): Icon? = null
}

private fun launchModeText(mode: AgentSessionLaunchMode): @Nls String {
  return when (mode) {
    AgentSessionLaunchMode.STANDARD -> AgentPromptBundle.message("popup.profile.manage.mode.standard")
    AgentSessionLaunchMode.YOLO -> AgentPromptBundle.message("popup.profile.manage.mode.yolo")
  }
}

private fun planReasoningEffortPopupText(planReasoningEffort: AgentPromptReasoningEffort?): @Nls String {
  return when (planReasoningEffort) {
    null -> AgentPromptBundle.message("popup.generation.plan.reasoning.popup.same")
    AgentPromptReasoningEffort.AUTO -> AgentPromptBundle.message("popup.generation.plan.reasoning.popup.provider.default")
    else -> reasoningEffortPopupText(planReasoningEffort)
  }
}
