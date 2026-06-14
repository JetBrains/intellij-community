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
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.GroupedComboBoxRenderer
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBRadioButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
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
import javax.swing.event.PopupMenuListener

internal class AgentPromptLaunchProfileEditorDialog(
  project: Project,
  profiles: List<AgentPromptLaunchProfile>,
  activeProfileId: String?,
  defaultProfileId: String?,
  private var providerEntries: List<ProviderEntry>,
  private var currentDraftProfile: AgentPromptLaunchProfile?,
  modelCatalogProvider: (String) -> List<AgentPromptGenerationModel>?,
  private val modelCatalogStateProvider: (String) -> AgentPromptGenerationModelCatalogState? = { providerId ->
    modelCatalogProvider(providerId)?.let(AgentPromptGenerationModelCatalogState::Loaded)
  },
  private val requestModelCatalogRefresh: (String, () -> Unit) -> Unit = { _, _ -> },
  private val newUserProfileId: () -> String,
  private val onCreateProfile: (AgentPromptLaunchProfile) -> Unit,
  private val onUpdateProfile: (AgentPromptLaunchProfile) -> Unit,
  private val onDeleteProfile: (AgentPromptLaunchProfile) -> Unit,
  private val onSetDefaultProfile: (AgentPromptLaunchProfile) -> Unit,
  private val onSelectProfile: (AgentPromptLaunchProfile?) -> Unit,
  private val onDispose: () -> Unit = {},
) : DialogWrapper(project) {
  private var managedProfiles: List<AgentPromptLaunchProfile> = profiles
  private var selectedProfileId: String? = activeProfileId
  private var currentDefaultProfileId: String? = defaultProfileId
  private var currentLaunchModes: Set<AgentSessionLaunchMode> = emptySet()
  private var isUpdatingEditor = false
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
  private val statusLabel = JBLabel(" ")
  private val duplicateButton = JButton(AgentPromptBundle.message("popup.profile.duplicate"))
  private val saveButton = JButton(AgentPromptBundle.message("popup.profile.editor.save.changes"))
  private val createButton = JButton(AgentPromptBundle.message("popup.profile.editor.create"))
  private val revertButton = JButton(AgentPromptBundle.message("popup.profile.revert"))
  private val setDefaultButton = JButton(AgentPromptBundle.message("popup.profile.set.default"))

  init {
    title = AgentPromptBundle.message("popup.profile.manage.title")
    isModal = false
    initModels()
    profileList.addListSelectionListener {
      if (!it.valueIsAdjusting) {
        selectedProfileId = selectedProfile()?.id
        onSelectProfile(selectedProfile())
        renderSelectedProfile()
      }
    }
    installProfileListRenameActions()
    providerCombo.addActionListener {
      if (!isUpdatingEditor) {
        refreshLaunchModeOptions()
        refreshModelOptions()
        refreshEffortOptions()
        updateButtonState()
      }
    }
    modelCombo.addActionListener {
      if (!isUpdatingEditor) {
        val selectedOption = selectedModelOption()
        when {
          selectedOption?.retryProviderId != null -> {
            requestSelectedProviderModelRefresh()
            refreshModelOptions(selectedModelId = selectedModelIdForEditor)
          }
          selectedOption?.selectable == true -> {
            selectedModelIdForEditor = selectedOption.modelId
            refreshEffortOptions()
          }
          else -> {
            refreshModelOptions(selectedModelId = selectedModelIdForEditor)
          }
        }
        updateButtonState()
      }
    }
    modelCombo.addPopupMenuListener(object : PopupMenuListener {
      override fun popupMenuWillBecomeVisible(e: PopupMenuEvent?) {
        requestSelectedProviderModelRefresh()
      }

      override fun popupMenuWillBecomeInvisible(e: PopupMenuEvent?) {
      }

      override fun popupMenuCanceled(e: PopupMenuEvent?) {
      }
    })
    standardModeButton.addActionListener {
      if (!isUpdatingEditor) {
        updateButtonState()
      }
    }
    yoloModeButton.addActionListener {
      if (!isUpdatingEditor) {
        updateButtonState()
      }
    }
    nameField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        updateButtonState()
      }
    })
    duplicateButton.addActionListener { duplicateSelectedProfile() }
    createButton.addActionListener { createDraftProfile() }
    saveButton.addActionListener { saveSelectedProfile() }
    revertButton.addActionListener { renderSelectedProfile() }
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
    providerEntries: List<ProviderEntry>,
    currentDraftProfile: AgentPromptLaunchProfile?,
  ) {
    if (hasEditorChanges()) {
      return
    }
    managedProfiles = profiles
    selectedProfileId = activeProfileId
    currentDefaultProfileId = defaultProfileId
    this.providerEntries = providerEntries
    this.currentDraftProfile = currentDraftProfile
    reloadList()
    selectProfile(selectedProfileId ?: managedProfiles.firstOrNull()?.id)
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
    return selectedProfile()?.kind == AgentPromptLaunchProfileKind.USER
  }

  fun isSelectedProfileUnavailableForTest(): Boolean {
    return selectedProfile()?.let { profile -> providerOption(profile.providerId)?.isAvailable != true } == true
  }

  fun setSelectedProfileNameForTest(name: String) {
    nameField.text = name
  }

  fun saveSelectedProfileForTest() {
    saveSelectedProfile()
  }

  fun renameSelectedProfileForTest() {
    renameSelectedProfile()
  }

  fun isNameFieldTextSelectedForTest(): Boolean {
    return nameField.selectedText == nameField.text
  }

  fun isSelectedProfileRemovableForTest(): Boolean {
    return selectedProfile()?.kind == AgentPromptLaunchProfileKind.USER
  }

  fun deleteSelectedProfileForTest() {
    deleteSelectedProfile()
  }

  fun isDuplicateToCustomizeVisibleForTest(): Boolean {
    return duplicateButton.isVisible
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
    requestSelectedProviderModelRefresh()
  }

  private fun profileListRendererComponentForTest(profileId: String): Component? {
    val index = managedProfiles.indexOfFirst { profile -> profile.id == profileId }
    val profile = managedProfiles.getOrNull(index) ?: return null
    return profileList.cellRenderer.getListCellRendererComponent(profileList, profile, index, false, false)
  }

  private fun createProfileListPanel(): JComponent {
    return ToolbarDecorator.createDecorator(profileList)
      .setAddAction { startNewFromCurrentDraft() }
      .setAddActionName(AgentPromptBundle.message("popup.profile.editor.new.current"))
      .setAddActionUpdater { currentDraftProfile != null }
      .setEditAction { renameSelectedProfile() }
      .setEditActionName(AgentPromptBundle.message("popup.profile.rename"))
      .setEditActionUpdater { selectedProfile()?.kind == AgentPromptLaunchProfileKind.USER }
      .setRemoveAction { deleteSelectedProfile() }
      .setRemoveActionName(AgentPromptBundle.message("popup.profile.delete"))
      .setRemoveActionUpdater { selectedProfile()?.kind == AgentPromptLaunchProfileKind.USER }
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
      add(duplicateButton)
      add(createButton)
      add(saveButton)
      add(revertButton)
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
    modelCombo.renderer = ModelOptionRenderer(modelCombo)
    effortCombo.renderer = ReasoningEffortOptionRenderer()
  }

  private fun reloadList() {
    profileListModel.clear()
    managedProfiles.forEach(profileListModel::addElement)
  }

  private fun selectedProfile(): AgentPromptLaunchProfile? {
    return profileList.selectedValue
  }

  private fun selectProfile(profileId: String?) {
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

  private fun renderSelectedProfile() {
    val profile = selectedProfile()
    isUpdatingEditor = true
    try {
      nameField.text = profile?.name.orEmpty()
      refreshProviderOptions(profile)
      refreshLaunchModeOptions(profile)
      refreshModelOptions(profile)
      refreshEffortOptions(profile)
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
  }

  private fun requestSelectedProviderModelRefresh() {
    val providerId = selectedProviderOption()?.providerId ?: return
    requestModelCatalogRefresh(providerId) {
      if (isDisposed) {
        return@requestModelCatalogRefresh
      }
      refreshModelOptions(selectedModelId = selectedModelIdForEditor)
      updateButtonState()
    }
    refreshModelOptions(selectedModelId = selectedModelIdForEditor)
  }

  private fun buildModelOptions(
    providerId: String,
    catalogState: AgentPromptGenerationModelCatalogState?,
    selectedModelId: String?,
  ): List<ModelOption> {
    return buildGenerationModelSelectorEntries(providerId, catalogState, selectedModelId).map { entry ->
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
    val selectedProvider = selectedProviderOption()
    val providerEntry = providerEntry(selectedProvider?.providerId)
    val selectedModelId = selectedModelIdForEditor
    val models = selectedProvider?.providerId?.let { providerId -> modelCatalogStateProvider(providerId)?.modelsOrNull() }.orEmpty()
    val modelEfforts = selectedModelId
      ?.let { modelId -> models.firstOrNull { model -> model.id == modelId } }
      ?.supportedReasoningEfforts
      ?.takeIf { efforts -> efforts.isNotEmpty() }
    val supportedEfforts = modelEfforts
                           ?: models.catalogReasoningEfforts()
                           ?: providerEntry?.bridge?.supportedReasoningEfforts.orEmpty()
    val efforts = reasoningEffortOrder()
      .filter { effort -> effort == AgentPromptReasoningEffort.AUTO || effort in supportedEfforts }
      .map { effort -> ReasoningEffortOption(effort, reasoningEffortPopupText(effort)) }
    effortCombo.model = DefaultComboBoxModel(efforts.toTypedArray())
    effortCombo.selectedItem =
      efforts.firstOrNull { option -> option.effort == profile?.generationSettings?.reasoningEffort } ?: efforts.first()
  }

  private fun updateButtonState() {
    val profile = selectedProfile()
    val editable = profile?.kind == AgentPromptLaunchProfileKind.USER
    val draft = currentEditorDraft(profile)
    val draftValid = draft != null
    val modified = profile != null && draft != null && draft != profile
    nameField.isEnabled = editable || profile == null
    providerCombo.isEnabled = editable || profile == null
    updateLaunchModeControlState(editable || profile == null)
    modelCombo.isEnabled = editable || profile == null
    effortCombo.isEnabled = editable || profile == null
    duplicateButton.isVisible = profile != null && !editable
    duplicateButton.isEnabled = profile != null
    createButton.isVisible = profile == null
    createButton.isEnabled = draftValid
    saveButton.isVisible = profile != null
    saveButton.isEnabled = editable && modified
    revertButton.isVisible = profile != null
    revertButton.isEnabled = editable && modified
    setDefaultButton.isEnabled =
      profile != null && profile.id != currentDefaultProfileId && providerOption(profile.providerId)?.isAvailable == true
    statusLabel.text = statusText(profile, draftValid)
  }

  private fun hasEditorChanges(): Boolean {
    val profile = selectedProfile() ?: return nameField.text.trim().isNotEmpty()
    return nameField.text.trim() != profile.name ||
           selectedProviderOption()?.providerId != profile.providerId ||
           selectedLaunchMode() != profile.launchMode ||
           selectedModelIdForEditor != profile.generationSettings.modelId ||
           selectedReasoningEffortOption()?.effort != profile.generationSettings.reasoningEffort
  }

  private fun updateLaunchModeControlState(enabled: Boolean) {
    launchModePanel.isEnabled = enabled
    standardModeButton.isEnabled = enabled && AgentSessionLaunchMode.STANDARD in currentLaunchModes
    yoloModeButton.isEnabled = enabled && AgentSessionLaunchMode.YOLO in currentLaunchModes
  }

  private fun statusText(profile: AgentPromptLaunchProfile?, draftValid: Boolean): @Nls String {
    if (profile == null) {
      return AgentPromptBundle.message("popup.profile.editor.status.new")
    }
    if (providerOption(profile.providerId)?.isAvailable != true) {
      return AgentPromptBundle.message("popup.profile.editor.status.unavailable")
    }
    if (!draftValid) {
      return AgentPromptBundle.message("popup.profile.editor.status.invalid")
    }
    if (profile.id == currentDefaultProfileId) {
      return AgentPromptBundle.message("popup.profile.editor.status.default")
    }
    return AgentPromptBundle.message("popup.profile.editor.status.ready")
  }

  private fun startNewFromCurrentDraft() {
    val base = currentDraftProfile ?: return
    selectedProfileId = null
    profileList.clearSelection()
    isUpdatingEditor = true
    try {
      nameField.text = base.name
      refreshProviderOptions(base)
      refreshLaunchModeOptions(base)
      refreshModelOptions(base)
      refreshEffortOptions(base)
    }
    finally {
      isUpdatingEditor = false
    }
    updateButtonState()
  }

  private fun duplicateSelectedProfile() {
    val profile = selectedProfile() ?: return
    selectedProfileId = null
    profileList.clearSelection()
    isUpdatingEditor = true
    try {
      nameField.text = AgentPromptBundle.message("popup.profile.editor.copy.name", profile.name)
      refreshProviderOptions(profile)
      refreshLaunchModeOptions(profile)
      refreshModelOptions(profile)
      refreshEffortOptions(profile)
    }
    finally {
      isUpdatingEditor = false
    }
    updateButtonState()
  }

  private fun renameSelectedProfile(): Boolean {
    selectedProfile()?.takeIf { profile -> profile.kind == AgentPromptLaunchProfileKind.USER } ?: return false
    nameField.requestFocusInWindow()
    nameField.selectAll()
    return true
  }

  private fun createDraftProfile() {
    val profile = currentEditorDraft(null)?.copy(id = newUserProfileId(), kind = AgentPromptLaunchProfileKind.USER) ?: return
    onCreateProfile(profile)
    managedProfiles = managedProfiles + profile
    selectedProfileId = profile.id
    reloadList()
    selectProfile(profile.id)
    renderSelectedProfile()
  }

  private fun saveSelectedProfile() {
    val profile = selectedProfile() ?: return
    val updated = currentEditorDraft(profile) ?: return
    onUpdateProfile(updated)
    managedProfiles = managedProfiles.map { item -> if (item.id == updated.id) updated else item }
    selectedProfileId = updated.id
    reloadList()
    selectProfile(updated.id)
    renderSelectedProfile()
  }

  private fun setSelectedProfileAsDefault() {
    val profile = selectedProfile() ?: return
    onSetDefaultProfile(profile)
    currentDefaultProfileId = profile.id
    profileList.repaint()
    updateButtonState()
  }

  private fun deleteSelectedProfile() {
    val profile = selectedProfile()?.takeIf { it.kind == AgentPromptLaunchProfileKind.USER } ?: return
    onDeleteProfile(profile)
    managedProfiles = managedProfiles.filterNot { item -> item.id == profile.id }
    if (selectedProfileId == profile.id) {
      selectedProfileId = managedProfiles.firstOrNull()?.id
    }
    if (currentDefaultProfileId == profile.id) {
      currentDefaultProfileId = null
    }
    reloadList()
    selectProfile(selectedProfileId)
    renderSelectedProfile()
  }

  private fun currentEditorDraft(existing: AgentPromptLaunchProfile?): AgentPromptLaunchProfile? {
    val name = nameField.text.trim().takeIf { it.isNotEmpty() } ?: return null
    val provider = selectedProviderOption()?.takeIf { option -> option.isAvailable } ?: return null
    val launchMode = selectedLaunchMode() ?: return null
    val modelId = selectedModelIdForEditor
    val reasoningEffort = selectedReasoningEffortOption()?.effort ?: AgentPromptReasoningEffort.AUTO
    return AgentPromptLaunchProfile(
      id = existing?.id ?: "",
      name = name,
      kind = existing?.kind ?: AgentPromptLaunchProfileKind.USER,
      providerId = provider.providerId,
      launchMode = launchMode,
      generationSettings = AgentPromptGenerationSettings(
        modelId = modelId,
        reasoningEffort = reasoningEffort,
      ),
    )
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
}

private fun JPanel.addLabeledField(row: Int, labelText: @Nls String, component: JComponent) {
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
) {
  override fun toString(): String = displayName
}

private data class ReasoningEffortOption(
  @JvmField val effort: AgentPromptReasoningEffort,
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

  override fun separatorFor(value: ModelOption): ListSeparator? {
    return value.separatorGroup?.let { group -> ListSeparator(group.modelSelectorText()) }
  }
}

private class ReasoningEffortOptionRenderer : PromptProfileComboRenderer<ReasoningEffortOption>() {
  override fun text(value: ReasoningEffortOption): @NlsSafe String = value.displayName
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
