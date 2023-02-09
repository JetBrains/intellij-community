// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.ScrollPaneFactory.createScrollPane
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBTextArea
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import git4idea.GitBranch
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
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitRepositoryReader
import git4idea.ui.ComboBoxWithAutoCompletion
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Insets
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
import java.util.Collections.synchronizedMap
import java.util.regex.Pattern
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
import javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED

internal const val GIT_REF_PROTOTYPE_VALUE = "origin/long-enough-branch-name"

internal fun createRepositoryField(repositories: List<GitRepository>,
                                   defaultRoot: VirtualFile? = null) = ComboBox(CollectionComboBoxModel(repositories)).apply {
  item = repositories.find { repo -> repo.root == defaultRoot } ?: repositories.first()
  renderer = SimpleListCellRenderer.create("") { DvcsUtil.getShortRepositoryName(it) }
  setUI(FlatComboBoxUI(outerInsets = Insets(BW.get(), BW.get(), BW.get(), 0)))
}

internal fun createSouthPanelWithOptionsDropDown(southPanel: JComponent, optionDropDown: DropDownLink<*>) = southPanel.apply {
  (southPanel.components[0] as JPanel).apply {
    (layout as BorderLayout).hgap = JBUI.scale(5)
    add(optionDropDown, BorderLayout.EAST)
  }
}

internal fun validateBranchExists(branchField: ComboBoxWithAutoCompletion<String>,
                                  emptyFieldMessage: @NlsContexts.DialogMessage String): ValidationInfo? {
  val value = branchField.getText()
  if (value.isNullOrEmpty()) {
    return ValidationInfo(emptyFieldMessage, branchField)
  }

  val items = (branchField.model as CollectionComboBoxModel).items
  if (items.none { equalBranches(it, value) }) {
    return ValidationInfo(GitBundle.message("merge.no.matching.branch.error"), branchField)
  }

  return null
}

class GitMergeDialog(private val project: Project,
                     private val defaultRoot: VirtualFile,
                     private val roots: List<VirtualFile>) : DialogWrapper(project) {

  val selectedOptions = mutableSetOf<GitMergeOption>()

  private val mergeSettings = project.service<GitMergeSettings>()

  private val repositories = DvcsUtil.sortRepositories(GitRepositoryManager.getInstance(project).repositories)

  private val allBranches = collectAllBranches()

  private val unmergedBranches = synchronizedMap(HashMap<GitRepository, Set<GitBranch>?>())

  private val optionInfos = mutableMapOf<GitMergeOption, OptionInfo<GitMergeOption>>()

  private val popupBuilder = createPopupBuilder()

  private val repositoryField = createRepoField()
  private val branchField = createBranchField()
  private val commandPanel = createCommandPanel()

  private val optionsPanel = GitOptionsPanel(::optionChosen, ::getOptionInfo)

  private val commitMsgField = JBTextArea("")
  private val commitMsgPanel = createCommitMsgPanel()

  private val panel = createPanel()

  private val isNoVerifySupported = NO_VERIFY_SUPPORTED.existsIn(GitExecutableManager.getInstance().getVersion(project))

  init {
    loadUnmergedBranchesInBackground()
    updateDialogTitle()
    setOKButtonText(GitBundle.message("merge.action.name"))
    loadSettings()
    updateBranchesField()

    // We call pack() manually.
    isAutoAdjustable = false

    init()
    window.minimumSize = JBDimension(200, 60)

    updateUi()
    validate()
    pack()
  }

  override fun createCenterPanel() = panel

  override fun getPreferredFocusedComponent() = branchField

  override fun doValidateAll(): List<ValidationInfo> = listOf(::validateBranchField).mapNotNull { it() }

  override fun createSouthPanel() = createSouthPanelWithOptionsDropDown(super.createSouthPanel(), createOptionsDropDown())

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

  fun getSelectedRoot(): VirtualFile = repositoryField.item.root

  fun getSelectedBranch(): GitBranch = tryGetSelectedBranch() ?: error("Unable to find branch: ${branchField.getText().orEmpty()}")

  private fun tryGetSelectedBranch() = getSelectedRepository().branches.findBranchByName(branchField.getText().orEmpty())

  fun shouldCommitAfterMerge() = !isOptionSelected(GitMergeOption.NO_COMMIT)

  private fun saveSettings() {
    mergeSettings.branch = branchField.getText()
    mergeSettings.options = selectedOptions
  }

  private fun loadSettings() {
    branchField.item = mergeSettings.branch
    mergeSettings.options
      .filter { option -> option != GitMergeOption.NO_VERIFY || isNoVerifySupported }
      .forEach { option -> selectedOptions += option }
  }

  private fun collectAllBranches() = repositories.associateWith { repo ->
    repo.branches
      .let { it.localBranches + it.remoteBranches }
      .map { it.name }
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
            val repository = getRepository(root)
            loadUnmergedBranchesForRoot(repository)?.let { branches ->
              unmergedBranches[repository] = branches
            }
          }
        }
      })
  }

  /**
   * ```
   * $ git branch --all --format=...
   * |refs/heads/master []
   * |refs/heads/feature []
   * |refs/heads/checked-out []
   * |refs/heads/checked-out-by-worktree []
   * |refs/remotes/origin/master []
   * |refs/remotes/origin/feature []
   * |refs/remotes/origin/HEAD [refs/remotes/origin/master]
   * ```
   */
  @RequiresBackgroundThread
  private fun loadUnmergedBranchesForRoot(repository: GitRepository): Set<GitBranch>? {
    val root = repository.root
    try {
      val handler = GitLineHandler(project, root, GitCommand.BRANCH)
      handler.addParameters(UNMERGED_BRANCHES_FORMAT, "--no-color", "--all", "--no-merged")

      val result = Git.getInstance().runCommand(handler)
      result.throwOnError()

      val remotes = repository.remotes
      return result.output.asSequence()
        .mapNotNull { line ->
          val matcher = BRANCH_NAME_REGEX.matcher(line)
          when {
            matcher.matches() -> matcher.group(1)
            else -> null
          }
        }
        .mapNotNull { refName -> GitRepositoryReader.parseBranchRef(remotes, refName) }
        .toSet()
    }
    catch (e: Exception) {
      LOG.warn("Failed to load unmerged branches for root: ${root}", e)
      return null
    }
  }

  private fun validateBranchField(): ValidationInfo? {
    val validationInfo = validateBranchExists(branchField, GitBundle.message("merge.no.branch.selected.error"))
    if (validationInfo != null) return validationInfo

    val selectedBranch = tryGetSelectedBranch() ?: return ValidationInfo(GitBundle.message("merge.no.matching.branch.error"))

    val selectedRepository = getSelectedRepository()
    val unmergedBranches = unmergedBranches[selectedRepository] ?: return null
    val selectedBranchMerged = !unmergedBranches.contains(selectedBranch)

    if (selectedBranchMerged) {
      return ValidationInfo(GitBundle.message("merge.branch.already.merged", selectedBranch), branchField)
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

    branchField.item = branchToSelect
    branchField.selectAll()
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
    return allBranches[repository] ?: emptyList()
  }

  private fun getRepository(root: VirtualFile) = repositories.find { repo -> repo.root == root }
                                                 ?: error("Unable to find repository for root: ${root.presentableUrl}")

  private fun getSelectedRepository() = getRepository(getSelectedRoot())

  private fun updateDialogTitle() {
    val currentBranchName = getSelectedRepository().currentBranchName
    title = (if (currentBranchName.isNullOrEmpty())
      GitBundle.message("merge.branch.title")
    else
      GitBundle.message("merge.branch.into.current.title", currentBranchName))
  }

  private fun createPanel() = JPanel().apply {
    layout = MigLayout(LC().insets("0").hideMode(3), AC().grow())

    add(commandPanel, CC().growX())
    add(optionsPanel, CC().newline().width("100%").alignY("top"))
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
      add(repositoryField,
          CC()
            .gapAfter("0")
            .minWidth("135")
            .growX())
    }

    add(createCmdLabel(),
        CC()
          .gapAfter("0")
          .alignY("top")
          .minWidth("100"))

    add(branchField,
        CC()
          .alignY("top")
          .minWidth("300")
          .growX())
  }

  private fun createRepoField() = createRepositoryField(repositories, defaultRoot).apply {
    addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED && e.item != null) {
        updateDialogTitle()
        updateBranchesField()
      }
    }
  }

  private fun createCmdLabel() = CmdLabel("git merge",
                                          Insets(1, if (showRootField()) 0 else 1, 1, 0),
                                          JBDimension(JBUI.scale(100), branchField.preferredSize.height, true))

  private fun createBranchField() = ComboBoxWithAutoCompletion(MutableCollectionComboBoxModel(mutableListOf<String>()),
                                                               project).apply {
    prototypeDisplayValue = GIT_REF_PROTOTYPE_VALUE
    setPlaceholder(GitBundle.message("merge.branch.field.placeholder"))
    setUI(FlatComboBoxUI(
      outerInsets = Insets(BW.get(), 0, BW.get(), BW.get()),
      popupEmptyText = GitBundle.message("merge.branch.popup.empty.text")))
  }

  private fun createCommitMsgPanel() = JPanel().apply {
    layout = MigLayout(LC().insets("0").fill())
    isVisible = false

    add(JLabel(GitBundle.message("merge.commit.message.label")), CC().alignY("top").wrap())
    add(createScrollPane(commitMsgField,
                         VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED),
        CC()
          .alignY("top")
          .grow()
          .push()
          .minHeight("75"))
  }

  private fun createPopupBuilder() = GitOptionsPopupBuilder(
    project,
    GitBundle.message("merge.options.modify.popup.title"),
    ::getOptions, ::getOptionInfo, ::isOptionSelected, ::isOptionEnabled, ::optionChosen
  )

  private fun createOptionsDropDown() = DropDownLink(GitBundle.message("merge.options.modify")) {
    popupBuilder.createPopup()
  }.apply {
    mnemonic = KeyEvent.VK_M
  }

  private fun isOptionSelected(option: GitMergeOption) = option in selectedOptions

  private fun getOptionInfo(option: GitMergeOption) = optionInfos.computeIfAbsent(option) {
    OptionInfo(option, option.option, option.description)
  }

  private fun getOptions(): List<GitMergeOption> = GitMergeOption.values().toMutableList().apply {
    if (!isNoVerifySupported) {
      remove(GitMergeOption.NO_VERIFY)
    }
  }

  private fun isOptionEnabled(option: GitMergeOption) = selectedOptions.all { it.isOptionSuitable(option) }

  private fun optionChosen(option: GitMergeOption) {
    if (!isOptionSelected(option)) {
      selectedOptions += option
    }
    else {
      selectedOptions -= option
    }
    updateUi()
    validate()
    pack()
  }

  private fun updateUi() {
    optionsPanel.rerender(selectedOptions)
    updateCommitMessagePanel()
    panel.invalidate()
  }

  private fun updateCommitMessagePanel() {
    val useCommitMsg = isOptionSelected(GitMergeOption.COMMIT_MESSAGE)
    commitMsgPanel.isVisible = useCommitMsg
    if (!useCommitMsg) {
      commitMsgField.text = ""
    }
  }

  companion object {
    private val LOG = logger<GitMergeDialog>()

    /**
     * Filter out 'symrefs' (ex: 'remotes/origin/HEAD -> origin/master')
     */
    @Suppress("SpellCheckingInspection")
    private val UNMERGED_BRANCHES_FORMAT = "--format=%(refname) [%(symref)]"
    private val BRANCH_NAME_REGEX = Pattern.compile("(\\S+) \\[]")

    @NlsSafe
    private const val REMOTE_REF = "remotes/"
  }
}