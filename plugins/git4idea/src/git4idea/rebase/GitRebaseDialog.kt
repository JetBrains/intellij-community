// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase

import com.intellij.dvcs.DvcsUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.InplaceButton
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.IconUtil
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import git4idea.GitBranch
import git4idea.GitRevisionNumber
import git4idea.GitTag
import git4idea.GitVcs
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitRebaseParams
import git4idea.config.GitRebaseSettings
import git4idea.config.GitVersionSpecialty.REBASE_MERGES_REPLACES_PRESERVE_MERGES
import git4idea.i18n.GitBundle
import git4idea.merge.GIT_REF_PROTOTYPE_VALUE
import git4idea.merge.createRepositoryField
import git4idea.merge.createSouthPanelWithOptionsDropDown
import git4idea.merge.dialog.*
import git4idea.repo.GitRepositoryManager
import git4idea.ui.ComboBoxWithAutoCompletion
import java.awt.Insets
import java.awt.event.*
import java.util.Collections.synchronizedMap
import javax.swing.JComboBox
import javax.swing.SwingUtilities

internal class GitRebaseDialog(private val project: Project,
                               private val roots: List<VirtualFile>,
                               private val defaultRoot: VirtualFile?) : DialogWrapper(project) {

  private val rebaseSettings = project.service<GitRebaseSettings>()

  private val repositories = DvcsUtil.sortRepositories(GitRepositoryManager.getInstance(project).repositories)

  private val selectedOptions = mutableSetOf<GitRebaseOption>()

  private val popupBuilder = createPopupBuilder()

  private val optionInfos = mutableMapOf<GitRebaseOption, OptionInfo<GitRebaseOption>>()

  private val localBranches = mutableListOf<GitBranch>()
  private val remoteBranches = mutableListOf<GitBranch>()
  private val tags = synchronizedMap(HashMap<VirtualFile, List<GitTag>>())

  private var currentBranch: GitBranch? = null

  private val rootField = createRepoField()
  private val branchField = createBranchField()
  private val upstreamField = createUpstreamField()

  private val ontoLabel = createOntoLabel()
  private val ontoField = createOntoField()

  private lateinit var topUpstreamFieldPlaceholder: Placeholder
  private lateinit var topBranchFieldPlaceholder: Placeholder
  private lateinit var bottomUpstreamFieldPlaceholder: Placeholder
  private lateinit var bottomBranchFieldPlaceholder: Placeholder
  private val optionsPanel = GitOptionsPanel(::optionChosen, ::getOptionInfo)
  private val panel: DialogPanel = createPanel()

  private var okActionTriggered = false

  private var upstreamValidator = RevValidator(upstreamField)
  private var ontoValidator = RevValidator(ontoField)

  private val gitVersion = GitVcs.getInstance(project).version
  private val rebaseMergesAvailable = REBASE_MERGES_REPLACES_PRESERVE_MERGES.existsIn(gitVersion)

  init {
    title = GitBundle.message("rebase.dialog.title")
    setOKButtonText(GitBundle.message("rebase.dialog.start.rebase"))

    loadRefs()
    updateBranches()
    loadSettings()

    init()

    updateUi()

    updateOkActionEnabled()

    invokeLater(ModalityState.stateForComponent(rootPane)) { loadTagsInBackground() }
  }

  override fun createCenterPanel() = panel

  override fun doValidateAll(): List<ValidationInfo> {
    val result = listOf(::validateUpstream,
                        ::validateOnto,
                        ::validateRebaseInProgress,
                        ::validateBranch).mapNotNull { it() }

    okActionTriggered = false

    return result
  }

  override fun createSouthPanel() = createSouthPanelWithOptionsDropDown(super.createSouthPanel(), createOptionsDropDown())

  override fun getHelpId() = "reference.VersionControl.Git.Rebase"

  override fun getPreferredFocusedComponent() = upstreamField

  override fun doOKAction() {
    try {
      saveSettings()
    }
    finally {
      super.doOKAction()
    }
  }

  override fun createDefaultActions() {
    super.createDefaultActions()

    myOKAction = object : OkAction() {
      override fun doAction(e: ActionEvent?) {
        okActionTriggered = true
        super.doAction(e)
      }
    }
  }

  fun gitRoot(): VirtualFile = rootField.item.root

  fun getSelectedParams(): GitRebaseParams {
    val branch = branchField.item

    val newBase = if (GitRebaseOption.ONTO in selectedOptions) ontoField.getText() else null
    val upstream = if (GitRebaseOption.ROOT !in selectedOptions) upstreamField.getText().orEmpty() else null

    return GitRebaseParams(gitVersion, branch, newBase, upstream, selectedOptions intersect REBASE_FLAGS)
  }

  private fun getSelectedRepo() = rootField.item

  private fun saveSettings() {
    rebaseSettings.options = selectedOptions intersect REBASE_FLAGS
    rebaseSettings.newBase = if (GitRebaseOption.ONTO in selectedOptions)
      ontoField.getText()
    else
      upstreamField.getText()
  }

  private fun loadSettings() {
    rebaseSettings.options.forEach { option -> selectedOptions += option }
    val newBase = rebaseSettings.newBase
    if (!newBase.isNullOrEmpty() && isValidRevision(newBase)) {
      upstreamField.item = newBase
    }
  }

  private fun updateOkActionEnabled() {
    isOKActionEnabled = listOf(::validateUpstream, ::validateOnto).mapNotNull { it() }.isEmpty()
  }

  private fun getTags() = tags[getSelectedRepo().root] ?: emptyList()

  private fun validateUpstream(): ValidationInfo? {
    if (GitRebaseOption.ROOT in selectedOptions) return null

    val upstream = upstreamField.getText()

    if (upstream.isNullOrEmpty()) {
      return if (GitRebaseOption.ONTO in selectedOptions)
        ValidationInfo(GitBundle.message("rebase.dialog.error.upstream.not.selected"), upstreamField)
      else
        ValidationInfo(GitBundle.message("rebase.dialog.error.base.not.selected"), upstreamField)
    }

    return upstreamValidator.validate()
  }

  private fun validateOnto(): ValidationInfo? {
    if (GitRebaseOption.ONTO in selectedOptions) {
      val newBase = ontoField.getText()

      if (newBase.isNullOrEmpty() && GitRebaseOption.ROOT !in selectedOptions) {
        return ValidationInfo(GitBundle.message("rebase.dialog.error.base.not.selected"), ontoField)
      }

      return ontoValidator.validate()
    }
    return null
  }

  private fun isValidRevision(revision: String): Boolean {
    if (revision.isEmpty()) return true

    var result = false
    try {
      val task = ThrowableComputable<GitRevisionNumber, VcsException> { GitRevisionNumber.resolve(project, gitRoot(), revision) }
      ProgressManager.getInstance()
        .runProcessWithProgressSynchronously(task, GitBundle.message("reference.validating.progress.indicator.title"), true, project)

      result = true
    }
    catch (ignored: VcsException) {
    }
    return result
  }

  private fun validateBranch(): ValidationInfo? {
    if (GitRebaseOption.SWITCH_BRANCH !in selectedOptions) {
      return null
    }
    val selectedBranch = branchField.getText()
    if (selectedBranch.isNullOrEmpty()) {
      return ValidationInfo(GitBundle.message("rebase.dialog.error.branch.not.selected"), branchField)
    }
    if (selectedBranch !in localBranches.map { it.name }) {
      return ValidationInfo(GitBundle.message("rebase.dialog.error.branch.invalid", selectedBranch), branchField)
    }
    return null
  }

  private fun validateRebaseInProgress(): ValidationInfo? {
    if (getSelectedRepo().isRebaseInProgress) {
      return ValidationInfo(GitBundle.message("rebase.dialog.error.rebase.in.progress"))
    }
    return null
  }

  private fun loadRefs() {
    localBranches.clear()
    remoteBranches.clear()

    val repository = getSelectedRepo()

    currentBranch = repository.currentBranch

    localBranches += GitBranchUtil.sortBranchesByName(repository.branches.localBranches)
    remoteBranches += GitBranchUtil.sortBranchesByName(repository.branches.remoteBranches)
  }

  private fun loadTagsInBackground() {
    val selectedRoot = getSelectedRepo().root
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, GitBundle.message("rebase.dialog.progress.loading.tags"), true) {
        override fun run(indicator: ProgressIndicator) {
          val sortedRoots = LinkedHashSet<VirtualFile>(roots.size).apply {
            add(selectedRoot)
            if (defaultRoot != null) {
              add(defaultRoot)
            }
            addAll(roots)
          }

          sortedRoots.forEach { root ->
            val tagsInRepo = loadTags(root)

            tags[root] = tagsInRepo

            if (selectedRoot == root) {
              UIUtil.invokeLaterIfNeeded {
                updateBaseFields()
              }
            }
          }
        }

        override fun onSuccess() {
          updateBaseFields()
        }
      })
  }

  @RequiresBackgroundThread
  private fun loadTags(root: VirtualFile): List<GitTag> {
    try {
      return GitBranchUtil.getAllTags(project, root).map { GitTag(it) }
    }
    catch (e: VcsException) {
      LOG.warn("Failed to load tags for root: ${root.presentableUrl}", e)
    }
    return emptyList()
  }

  private fun updateBranches() {
    branchField.mutableModel?.update(localBranches.map { it.name })

    updateBaseFields()
  }

  private fun updateBaseFields() {
    val newRefs = sequenceOf(localBranches, remoteBranches, getTags()).flatten().map { it.name }.toList()

    upstreamField.updatePreserving { upstreamField.mutableModel?.update(newRefs) }
    ontoField.updatePreserving { ontoField.mutableModel?.update(newRefs) }
  }

  private fun showRootField() = roots.size > 1

  private fun createPanel() = panel {
    customizeSpacingConfiguration(EmptySpacingConfiguration()) {
      row {
        if (showRootField()) {
          cell(rootField)
            .columns(COLUMNS_SHORT)
            .resizableColumn()
            .align(AlignX.FILL)
        }

        cell(createCmdLabel())

        cell(ontoLabel)

        cell(ontoField)
          .align(AlignX.FILL)
          .resizableColumn()
          .applyToComponent { setMinimumAndPreferredWidth(JBUI.scale(if (showRootField()) SHORT_FIELD_LENGTH else LONG_FIELD_LENGTH)) }

        topUpstreamFieldPlaceholder = placeholder()
          .align(AlignX.FILL)
          .resizableColumn()

        topBranchFieldPlaceholder = placeholder()
      }.customize(UnscaledGapsY(0, 6))

      row {
        bottomUpstreamFieldPlaceholder = placeholder()
          .align(AlignX.FILL)
          .resizableColumn()
        bottomBranchFieldPlaceholder = placeholder()
          .align(AlignX.FILL)
          .resizableColumn()
      }.customize(UnscaledGapsY(0, 6))

      row {
        cell(optionsPanel)
      }
    }.apply {
      addUpstreamField(true)
      updateUpstreamFieldConstraints()
    }
  }

  private fun createCmdLabel() = CmdLabel("git rebase",
                                          Insets(1, if (showRootField()) 0 else 1, 1, 0),
                                          JBDimension(JBUI.scale(85), branchField.preferredSize.height, true))

  private fun createOntoLabel() = CmdLabel("--onto",
                                           Insets(1, 1, 1, 0),
                                           JBDimension(JBUI.scale(80), branchField.preferredSize.height, true)).apply {
    isVisible = false
    addComponent(createOntoHelpButton())
  }

  private fun createOntoHelpButton() = InplaceButton(
    IconButton(GitBundle.message("rebase.dialog.help"), AllIcons.General.ContextHelp, HELP_BUTTON_ICON_FOCUSED),
    ActionListener {
      showRebaseHelpPopup()
    }
  ).apply {
    border = JBUI.Borders.empty(1)
    isFocusable = true

    addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) = repaint()
      override fun focusLost(e: FocusEvent?) = repaint()
    })

    addKeyListener(object : KeyAdapter() {
      override fun keyPressed(e: KeyEvent?) {
        if (e?.keyCode == KeyEvent.VK_SPACE) {
          e.consume()
          showRebaseHelpPopup()
        }
      }
    })
  }

  private fun showRebaseHelpPopup() {
    JBPopupFactory
      .getInstance()
      .createComponentPopupBuilder(GitRebaseHelpPopupPanel(), null)
      .setAdText(GitBundle.message("rebase.help.popup.ad.text"))
      .setFocusable(true)
      .setRequestFocus(true)
      .setCancelOnWindowDeactivation(true)
      .setCancelOnClickOutside(true)
      .createPopup()
      .showUnderneathOf(rootPane)
  }

  private fun createOntoField() = ComboBoxWithAutoCompletion<String>(MutableCollectionComboBoxModel(), project).apply {
    prototypeDisplayValue = GIT_REF_PROTOTYPE_VALUE
    isVisible = false
    setMinimumAndPreferredWidth(JBUI.scale(if (showRootField()) SHORT_FIELD_LENGTH else LONG_FIELD_LENGTH))
    setPlaceholder(GitBundle.message("rebase.dialog.new.base"))
    setUI(FlatComboBoxUI(outerInsets = Insets(BW.get(), 0, BW.get(), 0)))
    addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        updateOkActionEnabled()
      }
    })
  }

  private fun createUpstreamField() = ComboBoxWithAutoCompletion<String>(MutableCollectionComboBoxModel(), project).apply {
    prototypeDisplayValue = GIT_REF_PROTOTYPE_VALUE
    setMinimumAndPreferredWidth(JBUI.scale(SHORT_FIELD_LENGTH))
    setPlaceholder(GitBundle.message("rebase.dialog.target"))
    setUI(FlatComboBoxUI(outerInsets = Insets(BW.get(), 0, BW.get(), 0)))
    addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        updateOkActionEnabled()
      }
    })
  }

  private fun createRepoField() = createRepositoryField(repositories, defaultRoot).apply {
    val listener = ActionListener {
      loadRefs()
      updateBranches()
    }
    addActionListener(listener)
  }

  private fun createBranchField() = ComboBoxWithAutoCompletion<String>(MutableCollectionComboBoxModel(), project).apply {
    prototypeDisplayValue = GIT_REF_PROTOTYPE_VALUE
    setPlaceholder(GitBundle.message("rebase.dialog.branch.field"))
    setUI(FlatComboBoxUI(
      outerInsets = Insets(BW.get(), 0, BW.get(), 0),
      popupEmptyText = GitBundle.message("merge.branch.popup.empty.text")))
  }

  private fun createOptionsDropDown() = DropDownLink(GitBundle.message("merge.options.modify")) {
    popupBuilder.createPopup()
  }.apply {
    mnemonic = KeyEvent.VK_M
  }

  private fun createPopupBuilder() = GitOptionsPopupBuilder(
    project,
    GitBundle.message("rebase.options.modify.dialog.title"),
    { GitRebaseOption.entries },
    ::getOptionInfo, ::isOptionSelected, ::isOptionEnabled, ::optionChosen, ::hasSeparatorAbove
  )

  private fun isOptionSelected(option: GitRebaseOption) = option in selectedOptions

  private fun isOptionEnabled(option: GitRebaseOption): Boolean {
    if (rebaseMergesAvailable) {
      return true
    }
    return !(option == GitRebaseOption.REBASE_MERGES && selectedOptions.contains(GitRebaseOption.INTERACTIVE)
             || option == GitRebaseOption.INTERACTIVE && selectedOptions.contains(GitRebaseOption.REBASE_MERGES))
  }

  private fun hasSeparatorAbove(option: GitRebaseOption): Boolean = option == GitRebaseOption.INTERACTIVE

  private fun getOptionInfo(option: GitRebaseOption) = optionInfos.computeIfAbsent(option) {
    OptionInfo(option, option.getOption(gitVersion), option.description)
  }

  private fun optionChosen(option: GitRebaseOption) {
    if (option !in selectedOptions) {
      selectedOptions += option
    }
    else {
      selectedOptions -= option
    }
    if (option == GitRebaseOption.ONTO) {
      moveNewBaseValue()
    }

    updateUi()

    updateOkActionEnabled()
  }

  private fun moveNewBaseValue() {
    if (GitRebaseOption.ONTO in selectedOptions) {
      ontoField.item = upstreamField.getText()
      upstreamField.item = null
    }
    else {
      upstreamField.item = ontoField.getText()
      ontoField.item = null
    }
  }

  private fun updateUpstreamField() {
    val enabled = GitRebaseOption.ROOT !in selectedOptions
    upstreamField.isEnabled = enabled
    upstreamField.isEditable = enabled
  }

  private fun updateUi() {
    updatePlaceholders()
    updateUpstreamField()
    updateTopPanel()
    updateBottomPanel()
    optionsPanel.rerender(selectedOptions intersect REBASE_FLAGS)
    panel.invalidate()

    SwingUtilities.invokeLater {
      validate()
      pack()
    }
  }

  private fun updatePlaceholders() {
    val placeHolder = if (GitRebaseOption.ONTO in selectedOptions)
      GitBundle.message("rebase.dialog.old.base")
    else
      GitBundle.message("rebase.dialog.target")
    upstreamField.setPlaceholder(placeHolder)
  }

  private fun updateTopPanel() {
    val showOntoField = GitRebaseOption.ONTO in selectedOptions
    ontoLabel.isVisible = showOntoField
    ontoField.isVisible = showOntoField

    if (!showOntoField && topUpstreamFieldPlaceholder.component != upstreamField) {
      addUpstreamField(true)
      updateUpstreamFieldConstraints()
    }

    val showBranchField = !showRootField()
                          && !showOntoField
                          && GitRebaseOption.SWITCH_BRANCH in selectedOptions

    var isDirty = false
    if (showBranchField) {
      if (topBranchFieldPlaceholder.component != branchField) {
        addBranchField(true)

        val minWidth = JBUI.scale(SHORT_FIELD_LENGTH)
        upstreamField.setMinimumAndPreferredWidth(minWidth)
        branchField.setMinimumAndPreferredWidth(minWidth)

        isDirty = true
      }
    }
    else {
      topBranchFieldPlaceholder.component = null
      isDirty = true
    }

    if (isDirty && topUpstreamFieldPlaceholder.component == upstreamField) {
      (upstreamField.ui as FlatComboBoxUI).apply {
        border = Insets(1, 1, 1, if (!showBranchField) 1 else 0)
      }
      if (!showBranchField) {
        updateUpstreamFieldConstraints()
      }
    }
  }

  private fun updateBottomPanel() {
    val showRoot = showRootField()
    val showOnto = GitRebaseOption.ONTO in selectedOptions
    val showBranch = (showRoot || showOnto) && GitRebaseOption.SWITCH_BRANCH in selectedOptions

    if (showOnto) {
      addUpstreamField(false)
      (upstreamField.ui as FlatComboBoxUI).apply {
        border = Insets(1, 1, 1, if (!showBranch) 1 else 0)
      }
    }
    if (showBranch) {
      addBranchField(false)
    }
    else {
      bottomBranchFieldPlaceholder.component = null
    }
  }

  private fun addBranchField(top: Boolean) {
    if (top) {
      bottomBranchFieldPlaceholder.component = null
      topBranchFieldPlaceholder.component = branchField
    } else {
      topBranchFieldPlaceholder.component = null
      bottomBranchFieldPlaceholder.component = branchField
    }
  }

  private fun addUpstreamField(top: Boolean) {
    if (top) {
      bottomUpstreamFieldPlaceholder.component = null
      topUpstreamFieldPlaceholder.component = upstreamField
    } else {
      topUpstreamFieldPlaceholder.component = null
      bottomUpstreamFieldPlaceholder.component = upstreamField
    }
  }

  private fun updateUpstreamFieldConstraints() {
    upstreamField.setMinimumAndPreferredWidth(JBUI.scale(if (!showRootField()) 370 else 280))
  }

  internal inner class RevValidator(private val field: ComboBoxWithAutoCompletion<String>) {

    private var lastValidatedRevision = ""
    private var lastValid = true

    fun validate(): ValidationInfo? {
      val revision = field.getText().orEmpty()

      if (!okActionTriggered) {
        return if (revision == lastValidatedRevision)
          getValidationResult()
        else
          null
      }

      lastValidatedRevision = revision
      lastValid = isValidRevision(lastValidatedRevision)

      return getValidationResult()
    }

    private fun getValidationResult() = if (lastValid)
      null
    else
      ValidationInfo(GitBundle.message("rebase.dialog.error.branch.or.tag.not.exist"), field)
  }

  companion object {
    private val LOG = logger<GitRebaseDialog>()

    private val HELP_BUTTON_ICON_FOCUSED = if (StartupUiUtil.isUnderDarcula)
      IconUtil.brighter(AllIcons.General.ContextHelp, 3)
    else
      IconUtil.darker(AllIcons.General.ContextHelp, 3)

    private const val SHORT_FIELD_LENGTH = 220
    private const val LONG_FIELD_LENGTH = 310
  }
}

private val JComboBox<String>.mutableModel get() = this.model.asSafely<MutableCollectionComboBoxModel<String>>()
