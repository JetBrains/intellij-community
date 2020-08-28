// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.merge

import com.intellij.dvcs.DvcsUtil
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil.invokeLaterIfNeeded
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitBranchUtil.equalBranches
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitExecutableManager
import git4idea.config.GitMergeSettings
import git4idea.config.GitVersionSpecialty.NO_VERIFY_SUPPORTED
import git4idea.i18n.GitBundle
import git4idea.merge.dialog.*
import git4idea.rebase.ComboBoxPrototypeRenderer
import git4idea.rebase.ComboBoxPrototypeRenderer.Companion.COMBOBOX_VALUE_PROTOTYPE
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitUIUtil
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.CalledInBackground
import java.awt.BorderLayout
import java.awt.Insets
import java.awt.event.InputEvent
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
import java.util.Collections.synchronizedMap
import java.util.regex.Pattern
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

  val selectedOptions = mutableSetOf<GitMergeOption>()

  private val repositories = DvcsUtil.sortRepositories(GitRepositoryManager.getInstance(project).repositories)

  private val allBranches = collectAllBranches()

  private val unmergedBranches = synchronizedMap(HashMap<GitRepository, List<String>?>())

  private val optionInfos = mutableMapOf<GitMergeOption, OptionInfo<GitMergeOption>>()

  private val rootField = createRootField()
  private val branchField = createBranchField()
  private val commandPanel = createCommandPanel()

  private val optionsPanel: JPanel = createOptionsPanel()

  private val commitMsgField = JBTextArea("")
  private val commitMsgPanel = createCommitMsgPanel()

  private val panel = createPanel()

  private val mergeSettings = project.service<GitMergeSettings>()

  private val isNoVerifySupported = NO_VERIFY_SUPPORTED.existsIn(GitExecutableManager.getInstance().getVersion(project))

  init {
    loadUnmergedBranchesInBackground()
    updateTitle()
    setOKButtonText(GitBundle.message("merge.action.name"))
    updateBranchesField()
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

  override fun doOKAction() {
    try {
      saveSettings()
    }
    finally {
      super.doOKAction()
    }
  }

  @NlsSafe
  fun getCommitMessage(): String = commitMsgField.text

  fun getSelectedRoot(): VirtualFile = rootField.item.root

  fun getSelectedBranch() = getSelectedRepository().branches.findBranchByName(branchField.item)
                            ?: error("Unable to find branch: ${branchField.item}")

  fun shouldCommitAfterMerge() = GitMergeOption.NO_COMMIT !in selectedOptions

  private fun saveSettings() {
    mergeSettings.branch = branchField.item
    mergeSettings.options = selectedOptions
  }

  private fun loadSettings() {
    branchField.item = mergeSettings.branch
    mergeSettings.options
      .filter { option -> option != GitMergeOption.NO_VERIFY || isNoVerifySupported }
      .forEach { option -> selectedOptions += option }
  }

  private fun collectAllBranches(): Map<GitRepository, List<@NlsSafe String>?> {
    val branches = mutableMapOf<GitRepository, List<String>?>()

    for (repo in repositories) {
      branches[repo] = repo.branches
        .let { it.localBranches + it.remoteBranches }
        .map { it.name }
    }

    return branches.toMap()
  }

  private fun loadUnmergedBranchesInBackground() {
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, GitBundle.message("merge.branch.loading.branches.progress"), true) {
        override fun run(indicator: ProgressIndicator) {
          val sortedRoots = LinkedHashSet<VirtualFile>(roots.size).apply {
            add(defaultRoot)
            addAll(roots)
          }

          sortedRoots.forEach { root ->
            loadUnmergedBranches(root)?.let { branches ->
              unmergedBranches[getRepository(root)] = branches

              invokeLaterIfNeeded {
                if (getSelectedRoot() == root) {
                  updateBranchesField()
                }
              }
            }
          }
        }
      })
  }

  @CalledInBackground
  private fun loadUnmergedBranches(root: VirtualFile): List<@NlsSafe String>? {
    var result: List<String>? = null

    val handler = GitLineHandler(project, root, GitCommand.BRANCH).apply {
      addParameters("--no-color", "-a", "--no-merged")
    }
    try {
      result = Git.getInstance().runCommand(handler).getOutputOrThrow()
        .lines()
        .filter { branch -> !LINK_REF_REGEX.matcher(branch).matches() }
        .map { it.trim() }
    }
    catch (e: Exception) {
      LOG.warn("Failed to load unmerged branches for root: ${root}", e)
    }

    return result
  }

  private fun validateBranchField(): ValidationInfo? {
    val item = branchField.item ?: ""
    val text = GitUIUtil.getTextField(branchField).text
    val value = if (item == text) item else text

    if (value.isNullOrEmpty()) {
      return ValidationInfo(GitBundle.message("merge.no.branch.selected.error"), branchField)
    }

    val items = (branchField.model as CollectionComboBoxModel).items
    if (items.none { equalBranches(it, value) }) {
      return ValidationInfo(GitBundle.message("merge.no.matching.branch.error"), branchField)
    }

    return null
  }

  private fun updateBranchesField() {
    var branchToSelect = branchField.item

    val branches = splitAndSortBranches(getBranches())

    val model = branchField.model as MutableCollectionComboBoxModel
    model.update(branches)

    if (branchToSelect == null || branchToSelect !in branches) {
      val repository = getSelectedRepository()
      val currentRemoteBranch = repository.currentBranch?.findTrackedBranch(repository)?.nameForRemoteOperations

      branchToSelect = branches.find { branch -> branch == currentRemoteBranch } ?: branches.getOrElse(0) { "" }
    }

    if (branchToSelect.isEmpty()) {
      startTrackingValidation()
    }

    model.selectedItem = branchToSelect
  }

  private fun splitAndSortBranches(branches: List<@NlsSafe String>): List<@NlsSafe String> {
    val local = mutableListOf<String>()
    val remote = mutableListOf<String>()

    for (branch in branches) {
      if (branch.startsWith(REMOTE_REF)) {
        remote += branch.substring(REMOTE_REF.length)
      }
      else {
        local += branch
      }
    }

    return GitBranchUtil.sortBranchNames(local) + GitBranchUtil.sortBranchNames(remote)
  }

  private fun getBranches(): List<@NlsSafe String> {
    val repository = getSelectedRepository()
    return unmergedBranches[repository] ?: allBranches[repository] ?: emptyList()
  }

  private fun getRepository(root: VirtualFile) = repositories.find { repo -> repo.root == root }
                                                 ?: error("Unable to find repository for root: ${root.presentableUrl}")

  private fun getSelectedRepository() = getRepository(getSelectedRoot())

  private fun updateTitle() {
    val currentBranchName = getSelectedRepository().currentBranchName
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

  private fun createRootField(): ComboBox<GitRepository> {
    val model = CollectionComboBoxModel(repositories)
    return ComboBox(model).apply {
      item = repositories.find { repo -> repo.root == defaultRoot } ?: repositories.first()
      isSwingPopup = false
      renderer = SimpleListCellRenderer.create("") { DvcsUtil.getShortRepositoryName(it) }
      @Suppress("UsePropertyAccessSyntax")
      setUI(FlatComboBoxUI(outerInsets = Insets(BW.get(), BW.get(), BW.get(), 0)))

      addItemListener { e ->
        if (e.stateChange == ItemEvent.SELECTED
            && e.item != null) {
          updateTitle()
          updateBranchesField()
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
      prototypeDisplayValue = COMBOBOX_VALUE_PROTOTYPE
      renderer = ComboBoxPrototypeRenderer.create(this) { it }
      @Suppress("UsePropertyAccessSyntax")
      setUI(FlatComboBoxUI(
        outerInsets = Insets(BW.get(), 0, BW.get(), BW.get()),
        popupEmptyText = GitBundle.message("merge.branch.popup.empty.text")))
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

  private fun createOptionsDropDown() = DropDownLink(GitBundle.message("merge.options.modify")) { createOptionsPopup() }.apply {
    mnemonic = KeyEvent.VK_M
  }

  private fun createOptionsPopup() = object : ListPopupImpl(project, createOptionPopupStep()) {
    override fun getListElementRenderer() = OptionListCellRenderer(
      ::getOptionInfo,
      { selectedOptions },
      { option -> isOptionEnabled(option) })

    override fun handleSelect(handleFinalChoices: Boolean) {
      if (handleFinalChoices) {
        handleSelect()
      }
    }

    override fun handleSelect(handleFinalChoices: Boolean, e: InputEvent?) {
      if (handleFinalChoices) {
        handleSelect()
      }
    }

    private fun handleSelect() {
      (selectedValues.firstOrNull() as? GitMergeOption)?.let { option -> optionChosen(option) }

      list.repaint()
    }
  }

  private fun getOptionInfo(option: GitMergeOption) = optionInfos.computeIfAbsent(option) {
    OptionInfo(option, option.option, option.description)
  }

  private fun createOptionPopupStep() = object : BaseListPopupStep<GitMergeOption>(GitBundle.message("merge.options.modify.popup.title"),
                                                                                   getOptions()) {

    override fun isSelectable(value: GitMergeOption?) = isOptionEnabled(value!!)

    override fun onChosen(selectedValue: GitMergeOption?, finalChoice: Boolean) = doFinalStep(Runnable { optionChosen(selectedValue) })
  }

  private fun getOptions() = GitMergeOption.values().toMutableList().apply {
    if (!isNoVerifySupported) {
      remove(GitMergeOption.NO_VERIFY)
    }
  }

  private fun isOptionEnabled(option: GitMergeOption) = selectedOptions.all { it.isOptionSuitable(option) }

  private fun optionChosen(option: GitMergeOption?) {
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

    val shownOptions = mutableSetOf<GitMergeOption>()

    listOf(*optionsPanel.components).forEach { c ->
      @Suppress("UNCHECKED_CAST")
      val optionButton = c as OptionButton<GitMergeOption>
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
    val useCommitMsg = GitMergeOption.COMMIT_MESSAGE in selectedOptions
    commitMsgPanel.isVisible = useCommitMsg
    if (!useCommitMsg) {
      commitMsgField.text = ""
    }
  }

  private fun createOptionButton(option: GitMergeOption) = OptionButton(option, option.option) { optionChosen(option) }

  companion object {
    private val LOG = logger<GitMergeDialog>()
    private val LINK_REF_REGEX = Pattern.compile(".+\\s->\\s.+")

    @NlsSafe
    private const val REMOTE_REF = "remotes/"
  }
}