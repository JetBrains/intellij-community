// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.labels.DropDownLink
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import git4idea.GitUtil
import git4idea.config.GitExecutableManager
import git4idea.config.GitMergeSettings
import git4idea.config.GitVersionSpecialty.NO_VERIFY_SUPPORTED
import git4idea.i18n.GitBundle
import git4idea.merge.dialog.*
import git4idea.repo.GitRepository
import git4idea.util.GitUIUtil
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Insets
import java.awt.event.ItemEvent
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicComboBoxEditor

class GitMergeDialog(private val project: Project,
                     private val defaultRoot: VirtualFile,
                     private val roots: List<VirtualFile>) : DialogWrapper(project) {

  val selectedOptions = mutableSetOf<MergeOption>()

  private val rootsByNames = roots.associateBy { it.name }
  private val repositoryBranches = mutableMapOf<GitRepository, List<String>?>()

  private val optionInfos = mutableMapOf<MergeOption, OptionInfo<MergeOption>>()

  private val rootField = createRootField()
  private val branchField = createBranchField()
  private val commandPanel = createCommandPanel()

  private val optionsPanel: JPanel = createOptionsPanel()

  private val commitMsgField = JBTextArea("")
  private val commitMsgPanel = createCommitMsgPanel()

  private val panel = createPanel()

  private val repositoryManager = GitUtil.getRepositoryManager(project)

  private val mergeSettings = project.service<GitMergeSettings>()

  private val isNoVerifySupported = NO_VERIFY_SUPPORTED.existsIn(GitExecutableManager.getInstance().getVersion(project))

  init {
    updateTitle()
    setOKButtonText(GitBundle.message("merge.action.name"))
    updateBranches()
    loadSettings()
    updateUi()
    init()
  }

  override fun createCenterPanel() = panel

  override fun getPreferredFocusedComponent() = branchField

  override fun doValidateAll(): MutableList<ValidationInfo> {
    val validationResult = mutableListOf<ValidationInfo>()

    val branchFieldValidation = validateBranchField()
    if (branchFieldValidation != null) {
      validationResult += branchFieldValidation
    }

    return validationResult
  }

  override fun createSouthPanel(): JComponent {
    val southPanel = super.createSouthPanel()
    (southPanel.components[0] as JPanel).apply {
      (layout as BorderLayout).hgap = JBUI.scale(5)
      add(createOptionsDropDown(), BorderLayout.EAST)
    }
    return southPanel
  }

  override fun getHelpId() = "reference.VersionControl.Git.MergeBranches"

  override fun getDimensionServiceKey(): String = GitMergeDialog::class.java.name

  override fun doOKAction() {
    try {
      saveSettings()
    }
    finally {
      super.doOKAction()
    }
  }

  fun getCommitMessage(): String = commitMsgField.text

  fun getSelectedRoot(): VirtualFile = rootsByNames[rootField.item] ?: defaultRoot

  fun getSelectedBranches() = listOf(branchField.item)

  fun shouldCommitAfterMerge() = MergeOption.NO_COMMIT !in selectedOptions

  private fun saveSettings() {
    mergeSettings.options = selectedOptions
  }

  private fun loadSettings() = mergeSettings.options
    .filter { option -> option != MergeOption.NO_VERIFY || isNoVerifySupported }
    .forEach { option -> selectedOptions += option }

  private fun validateBranchField(): ValidationInfo? {
    val item = branchField.item ?: ""
    val text = GitUIUtil.getTextField(branchField).text
    val value = if (item == text) item else text

    if (value.isNullOrEmpty()) {
      return ValidationInfo(GitBundle.message("merge.no.branch.selected.error"), branchField)
    }

    val items = (branchField.model as CollectionComboBoxModel).items
    if (value !in items) {
      return ValidationInfo(GitBundle.message("merge.no.matching.branch.error"), branchField)
    }

    return null
  }

  private fun updateBranchesField(branches: List<String>) {
    val model = branchField.model as MutableCollectionComboBoxModel

    model.update(branches)

    val repository = getRepository()
    val matchingBranch = repository.currentBranch?.findTrackedBranch(repository)?.nameForRemoteOperations
                         ?: branches.find { branch -> branch == repository.currentBranchName }
                         ?: ""

    model.selectedItem = matchingBranch
  }

  private fun updateBranches() {
    val repository = getRepository()
    val branches = getRepositoryBranches(repository)

    updateBranchesField(branches)
  }

  private fun getRepository(): GitRepository {
    val root = getSelectedRoot()

    var repository = repositoryBranches.keys.find { it.root == root }
    if (repository != null) {
      return repository
    }

    repository = repositoryManager.getRepositoryForFileQuick(root)
    checkNotNull(repository) { "Unable to find repository with root: ${root}" }

    repositoryBranches[repository] = null

    return repository
  }

  private fun getRepositoryBranches(repository: GitRepository): List<String> {
    var branches = repositoryBranches[repository]
    if (branches != null) {
      return branches
    }

    branches = repository.branches
      .let { it.localBranches.sorted() + it.remoteBranches.sorted() }
      .map { it.name }

    repositoryBranches[repository] = branches

    return branches
  }

  private fun updateTitle() {
    val currentBranchName = getRepository().currentBranchName
    title = if (currentBranchName.isNullOrEmpty()) {
      GitBundle.message("merge.branch.title")
    }
    else {
      GitBundle.message("merge.branch.into.current.title", currentBranchName)
    }
  }

  private fun createPanel() = JPanel().apply {
    layout = MigLayout(LC().insets("0").hideMode(3), AC().grow())

    add(commandPanel, CC().growX())
    add(optionsPanel, CC().newline())
    add(commitMsgPanel, CC().newline().push().grow())
  }

  private fun showRootField() = roots.size > 1

  private fun createCommandPanel() = JPanel().apply {
    val colConstraints = if (showRootField())
      AC().grow(100f, 0, 2)
    else
      AC().grow(100f, 1)

    layout = MigLayout(
      LC()
        .fillX()
        .insets("0")
        .gridGap("0", "0")
        .noVisualPadding(),
      colConstraints)

    if (showRootField()) {
      add(rootField,
          CC()
            .gapAfter("0")
            .minWidth("${JBUI.scale(135)}px")
            .growX())
    }

    add(createCmdLabel(),
        CC()
          .gapAfter("0")
          .alignY("top")
          .minWidth("${JBUI.scale(100)}px"))

    add(branchField,
        CC()
          .alignY("top")
          .minWidth("${JBUI.scale(300)}px")
          .growX())
  }

  private fun createRootField(): ComboBox<String> {
    val model = CollectionComboBoxModel(rootsByNames.keys.toList())
    return ComboBox(model).apply {
      item = defaultRoot.name
      isSwingPopup = false
      ui = FlatComboBoxUI(outerInsets = Insets(BW.get(), BW.get(), BW.get(), 0))

      addItemListener { e ->
        if (e.stateChange == ItemEvent.SELECTED
            && e.item != null) {
          updateTitle()
          updateBranches()
        }
      }
    }
  }

  private fun createCmdLabel() = CmdLabel("git merge",
                                          Insets(1, if (showRootField()) 0 else 1, 1, 0),
                                          JBDimension(JBUI.scale(100), branchField.preferredSize.height, true))

  private fun createBranchField(): ComboBox<String> {
    val model = MutableCollectionComboBoxModel(mutableListOf<String>())
    return ComboBox(model).apply<ComboBox<String>> {
      isSwingPopup = false
      isEditable = true
      editor = object : BasicComboBoxEditor() {
        override fun createEditorComponent() = JBTextField().apply {
          emptyText.text = GitBundle.message("merge.branch.field.placeholder")

          document.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
              startTrackingValidation()
            }
          })
        }
      }

      ui = FlatComboBoxUI(
        outerInsets = Insets(BW.get(), 0, BW.get(), BW.get()),
        popupEmptyText = GitBundle.message("merge.branch.popup.empty.text"))
    }
  }

  private fun createOptionsPanel() = JPanel(MigLayout(LC().insets("0").noGrid())).apply { isVisible = false }

  private fun createCommitMsgLabel() = JLabel(GitBundle.message("merge.commit.message.label"))

  private fun createCommitMsgPanel() = JPanel().apply {
    layout = MigLayout(LC().insets("0").fill())
    isVisible = false

    add(createCommitMsgLabel(), CC().alignY("top").wrap())
    add(createScrollPane(commitMsgField,
                         VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED),
        CC()
          .alignY("top")
          .grow()
          .push()
          .minHeight("${JBUI.scale(75)}px"))
  }

  private fun createOptionsDropDown() = DropDownLink(GitBundle.message("merge.options.modify"),
                                                     Function<DropDownLink<*>?, ListPopupImpl> { createOptionsPopup() }).apply {
    isFocusable = true
}

  private fun createOptionsPopup() = object : ListPopupImpl(project, createOptionPopupStep()) {
    override fun getListElementRenderer() = OptionListCellRenderer(
      ::getOptionInfo,
      { selectedOptions },
      { option -> isOptionEnabled(option) })
  }

  private fun getOptionInfo(option: MergeOption) = optionInfos.computeIfAbsent(option) {
    OptionInfo(option, option.option, GitBundle.message(option.descriptionKey))
  }

  private fun createOptionPopupStep() = object : BaseListPopupStep<MergeOption>(GitBundle.message("merge.options.modify.popup.title"), getOptions()) {

    override fun isSelectable(value: MergeOption?) = isOptionEnabled(value!!)

    override fun onChosen(selectedValue: MergeOption?, finalChoice: Boolean) = doFinalStep(Runnable { optionChosen(selectedValue) })
  }

  private fun getOptions() = MergeOption.values().toMutableList().apply {
    if (!isNoVerifySupported) {
      remove(MergeOption.NO_VERIFY)
    }
  }

  private fun isOptionEnabled(option: MergeOption) = selectedOptions.all { it.isOptionSuitable(option) }

  private fun optionChosen(option: MergeOption?) {
    if (option !in selectedOptions) {
      selectedOptions += option!!
    }
    else {
      selectedOptions -= option!!
    }
    updateUi()
  }

  private fun updateUi() {
    updateOptionsPanel()
    updateCommitMessagePanel()
    rerender()
  }

  private fun rerender() {
    window.pack()
    window.revalidate()
    pack()
    repaint()
  }

  private fun updateOptionsPanel() {
    if (selectedOptions.isEmpty()) {
      optionsPanel.isVisible = false
      return
    }

    if (selectedOptions.isNotEmpty()) {
      optionsPanel.isVisible = true
    }

    val shownOptions = mutableSetOf<MergeOption>()

    listOf(*optionsPanel.components).forEach { c ->
      @Suppress("UNCHECKED_CAST")
      val optionButton = c as OptionButton<MergeOption>
      val mergeOption = optionButton.option

      if (mergeOption !in selectedOptions) {
        optionsPanel.remove(optionButton)
      }
      else {
        shownOptions.add(mergeOption)
      }
    }

    selectedOptions.forEach { option ->
      if (option !in shownOptions) {
        optionsPanel.add(createOptionButton(option))
      }
    }
  }

  private fun updateCommitMessagePanel() {
    val useCommitMsg = MergeOption.COMMIT_MESSAGE in selectedOptions
    commitMsgPanel.isVisible = useCommitMsg
    if (!useCommitMsg) {
      commitMsgField.text = ""
    }
  }

  private fun createOptionButton(option: MergeOption) = OptionButton(option, option.option) { optionChosen(option) }
}