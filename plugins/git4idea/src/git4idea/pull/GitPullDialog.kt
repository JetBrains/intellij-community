// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.pull

import com.intellij.codeInsight.hint.HintUtil
import com.intellij.dvcs.DvcsUtil.sortRepositories
import com.intellij.ide.actions.RefreshAction
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task.Backgroundable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.util.text.HtmlChunk.Element.html
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.DropDownLink
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import git4idea.GitNotificationIdsHolder.Companion.FETCH_ERROR
import git4idea.GitRemoteBranch
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchUtil
import git4idea.config.GitExecutableManager
import git4idea.config.GitPullSettings
import git4idea.config.GitVersionSpecialty.NO_VERIFY_SUPPORTED
import git4idea.fetch.GitFetchSupport
import git4idea.i18n.GitBundle
import git4idea.merge.GIT_REF_PROTOTYPE_VALUE
import git4idea.merge.createRepositoryField
import git4idea.merge.createSouthPanelWithOptionsDropDown
import git4idea.merge.dialog.*
import git4idea.merge.validateBranchField
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.ComboBoxWithAutoCompletion
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.Insets
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.SwingConstants

class GitPullDialog(private val project: Project,
                    private val roots: List<VirtualFile>,
                    private val defaultRoot: VirtualFile) : DialogWrapper(project) {

  val selectedOptions = mutableSetOf<GitPullOption>()

  private val fetchSupport = project.service<GitFetchSupport>()

  private val pullSettings = project.service<GitPullSettings>()

  private val repositories = sortRepositories(GitRepositoryManager.getInstance(project).repositories)

  private val branches = collectBranches().toMutableMap()

  private val optionInfos = mutableMapOf<GitPullOption, OptionInfo<GitPullOption>>()

  private val popupBuilder = createPopupBuilder()

  private val repositoryField = createRepoField()
  private val remoteField = createRemoteField()
  private val branchField = createBranchField()

  private val commandPanel = createCommandPanel()
  private val optionsPanel = GitOptionsPanel(::optionChosen, ::getOptionInfo)

  private val panel = createPanel()

  private val isNoVerifySupported = NO_VERIFY_SUPPORTED.existsIn(GitExecutableManager.getInstance().getVersion(project))

  init {
    updateTitle()
    setOKButtonText(GitBundle.message("pull.button"))
    loadSettings()
    updateRemotesField()
    init()
    updateUi()
  }

  override fun createCenterPanel() = panel

  override fun getPreferredFocusedComponent() = branchField

  override fun createSouthPanel() = createSouthPanelWithOptionsDropDown(super.createSouthPanel(), createOptionsDropDown())

  override fun getHelpId() = "reference.VersionControl.Git.Pull"

  override fun doValidateAll() = listOf(::validateRepositoryField, ::validateRemoteField, ::validateBranchField).mapNotNull { it() }

  override fun doOKAction() {
    try {
      saveSettings()
    }
    finally {
      super.doOKAction()
    }
  }

  fun gitRoot() = getSelectedRepository()?.root ?: error("No selected repository found")

  fun getSelectedRemote(): GitRemote = remoteField.item ?: error("No selected remote found")

  fun getSelectedBranch(): GitRemoteBranch {
    val repository = getSelectedRepository() ?: error("No selected repository found")
    val remote = getSelectedRemote()

    val branchName = "${remote.name}/${branchField.item}"
    return repository.branches.findRemoteBranch(branchName)
           ?: error("Unable to find remote branch: $branchName")
  }

  fun isCommitAfterMerge() = GitPullOption.NO_COMMIT !in selectedOptions

  private fun getRemote(): GitRemote? = remoteField.item

  private fun loadSettings() {
    selectedOptions += pullSettings.options
  }

  private fun saveSettings() {
    pullSettings.options = selectedOptions
  }

  private fun collectBranches() = repositories.associateWith { repository -> getBranchesInRepo(repository) }

  private fun getBranchesInRepo(repository: GitRepository) = repository.branches.remoteBranches
    .sortedBy { branch -> branch.nameForRemoteOperations }
    .groupBy { branch -> branch.remote }

  private fun validateRepositoryField(): ValidationInfo? {
    return if (getSelectedRepository() != null)
      null
    else
      ValidationInfo(GitBundle.message("pull.repository.not.selected.error"), repositoryField)
  }

  private fun validateRemoteField(): ValidationInfo? {
    return if (getRemote() != null)
      null
    else
      ValidationInfo(GitBundle.message("pull.remote.not.selected"), remoteField)
  }

  private fun validateBranchField() = validateBranchField(branchField, "pull.branch.not.selected.error")

  private fun getSelectedRepository(): GitRepository? = repositoryField.item

  private fun updateRemotesField() {
    val repository = getSelectedRepository()

    val model = remoteField.model as MutableCollectionComboBoxModel
    model.update(repository?.remotes?.toList() ?: emptyList())
    model.selectedItem = getCurrentOrDefaultRemote(repository)
  }

  private fun updateBranchesField() {
    var branchToSelect = branchField.item

    val repository = getSelectedRepository() ?: return
    val remote = getRemote() ?: return

    val branches = GitBranchUtil.sortBranchNames(getRemoteBranches(repository, remote))

    val model = branchField.model as MutableCollectionComboBoxModel
    model.update(branches)

    if (branchToSelect == null || branchToSelect !in branches) {
      branchToSelect = repository.currentBranch?.findTrackedBranch(repository)?.nameForRemoteOperations
                       ?: branches.find { branch -> branch == repository.currentBranchName }
                       ?: ""
    }

    if (branchToSelect.isEmpty()) {
      startTrackingValidation()
    }

    branchField.selectedItem = branchToSelect
  }

  private fun getRemoteBranches(repository: GitRepository, remote: GitRemote): List<String> {
    return branches[repository]?.get(remote)?.map { it.nameForRemoteOperations } ?: emptyList()
  }

  private fun getCurrentOrDefaultRemote(repository: GitRepository?): GitRemote? {
    val remotes = repository?.remotes ?: return null
    if (remotes.isEmpty()) {
      return null
    }
    return GitUtil.getTrackInfoForCurrentBranch(repository)?.remote
           ?: GitUtil.getDefaultOrFirstRemote(remotes)
  }

  private fun optionChosen(option: GitPullOption) {
    if (option !in selectedOptions) {
      selectedOptions += option
    }
    else {
      selectedOptions -= option
    }
    updateUi()
  }

  private fun performFetch() {
    if (fetchSupport.isFetchRunning) {
      return
    }
    val repository = getSelectedRepository()
    val remote = getRemote()
    if (repository == null || remote == null) {
      VcsNotifier.getInstance(project).notifyError(FETCH_ERROR,
                                                   GitBundle.message("pull.fetch.failed.notification.title"),
                                                   GitBundle.message("pull.fetch.failed.notification.text"))
      return
    }
    GitVcs.runInBackground(getFetchTask(repository, remote))
  }

  private fun getFetchTask(repository: GitRepository, remote: GitRemote) = object : Backgroundable(project,
                                                                                                   GitBundle.message("fetching"),
                                                                                                   true) {

    override fun run(indicator: ProgressIndicator) {
      fetchSupport.fetch(repository, remote)
    }

    override fun onSuccess() {
      branches[repository] = getBranchesInRepo(repository)

      if (getSelectedRepository() == repository && getRemote() == remote) {
        updateBranchesField()
      }
    }
  }

  private fun createPopupBuilder() = GitOptionsPopupBuilder(
    project,
    GitBundle.message("pull.options.modify.popup.title"),
    ::getOptions, ::getOptionInfo, ::isOptionSelected, ::isOptionEnabled, ::optionChosen
  )

  private fun isOptionSelected(option: GitPullOption) = option in selectedOptions

  private fun createOptionsDropDown() = DropDownLink(GitBundle.message("merge.options.modify")) {
    popupBuilder.createPopup()
  }.apply {
    mnemonic = KeyEvent.VK_M
  }

  private fun getOptionInfo(option: GitPullOption) = optionInfos.computeIfAbsent(option) {
    OptionInfo(option, option.option, option.description)
  }

  private fun getOptions(): List<GitPullOption> = GitPullOption.values().toMutableList().apply {
    if (!isNoVerifySupported) {
      remove(GitPullOption.NO_VERIFY)
    }
  }

  private fun updateUi() {
    optionsPanel.rerender(selectedOptions)
    rerender()
  }

  private fun rerender() {
    window.pack()
    window.revalidate()
    pack()
    repaint()
  }

  private fun isOptionEnabled(option: GitPullOption) = selectedOptions.all { it.isOptionSuitable(option) }

  private fun updateTitle() {
    val currentBranchName = getSelectedRepository()?.currentBranchName
    title = (if (currentBranchName.isNullOrEmpty())
      GitBundle.message("pull.dialog.title")
    else
      GitBundle.message("pull.dialog.with.branch.title", currentBranchName))
  }

  private fun createPanel() = JPanel().apply {
    layout = MigLayout(LC().insets("0").hideMode(3), AC().grow())
    add(commandPanel, CC().growX())
    add(optionsPanel, CC().newline().width("100%").alignY("top"))
  }

  private fun showRootField() = roots.size > 1

  private fun createCommandPanel() = JPanel().apply {
    val colConstraints = if (showRootField())
      AC().grow(100f, 0, 3)
    else
      AC().grow(100f, 2)

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
            .minWidth("${JBUI.scale(115)}px")
            .growX())
    }

    add(createCmdLabel(),
        CC()
          .gapAfter("0")
          .alignY("top")
          .minWidth("${JBUI.scale(85)}px"))

    add(remoteField,
        CC()
          .alignY("top")
          .minWidth("${JBUI.scale(90)}px"))

    add(branchField,
        CC()
          .alignY("top")
          .minWidth("${JBUI.scale(250)}px")
          .growX())
  }

  private fun createCmdLabel() = CmdLabel("git pull",
                                          Insets(1, if (showRootField()) 0 else 1, 1, 0),
                                          JBDimension(JBUI.scale(85), branchField.preferredSize.height, true))

  private fun createRepoField() = createRepositoryField(repositories, defaultRoot).apply {
    addActionListener {
      updateTitle()
      updateRemotesField()
    }
  }

  private fun createRemoteField() = ComboBox<GitRemote>(MutableCollectionComboBoxModel()).apply {
    isSwingPopup = false
    renderer = SimpleListCellRenderer.create(
      HtmlChunk.text(GitBundle.message("util.remote.renderer.none")).italic().wrapWith(html()).toString()
    ) { it.name }
    @Suppress("UsePropertyAccessSyntax")
    setUI(FlatComboBoxUI(
      outerInsets = Insets(BW.get(), 0, BW.get(), 0),
      popupEmptyText = GitBundle.message("pull.branch.no.matching.remotes")))

    item = getCurrentOrDefaultRemote(getSelectedRepository())

    addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED) {
        updateBranchesField()
      }
    }
  }

  private fun createBranchField() = ComboBoxWithAutoCompletion(MutableCollectionComboBoxModel(mutableListOf<String>()),
                                                               project).apply {
    prototypeDisplayValue = GIT_REF_PROTOTYPE_VALUE
    setPlaceholder(GitBundle.message("pull.branch.field.placeholder"))
    object : RefreshAction() {
      override fun actionPerformed(e: AnActionEvent) {
        popup?.hide()
        performFetch()
      }

      override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
      }
    }.registerCustomShortcutSet(getFetchActionShortcut(), this)

    @Suppress("UsePropertyAccessSyntax")
    setUI(FlatComboBoxUI(
      Insets(1, 0, 1, 1),
      Insets(BW.get(), 0, BW.get(), BW.get()),
      GitBundle.message("pull.branch.nothing.to.pull"),
      this@GitPullDialog::createBranchFieldPopupComponent))
  }

  private fun createBranchFieldPopupComponent(content: JComponent) = JPanel().apply {
    layout = MigLayout(LC().insets("0"))

    add(content, CC().width("100%"))

    val hintLabel = HintUtil.createAdComponent(
      GitBundle.message("pull.dialog.fetch.shortcuts.hint", getFetchActionShortcutText()),
      JBUI.CurrentTheme.BigPopup.advertiserBorder(),
      SwingConstants.LEFT)

    hintLabel.preferredSize = JBDimension.create(hintLabel.preferredSize, true)
      .withHeight(17)

    add(hintLabel, CC().newline().width("100%"))
  }

  private fun getFetchActionShortcut(): ShortcutSet {
    val refreshActionShortcut = ActionManager.getInstance().getAction(IdeActions.ACTION_REFRESH).shortcutSet
    if (refreshActionShortcut.shortcuts.isNotEmpty()) {
      return refreshActionShortcut
    }
    else {
      return FETCH_ACTION_SHORTCUT
    }
  }

  private fun getFetchActionShortcutText() = KeymapUtil.getPreferredShortcutText(getFetchActionShortcut().shortcuts)

  companion object {
    private val FETCH_ACTION_SHORTCUT = if (SystemInfo.isMac)
      CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_R, KeyEvent.META_DOWN_MASK))
    else
      CustomShortcutSet(KeyStroke.getKeyStroke(KeyEvent.VK_F5, KeyEvent.CTRL_DOWN_MASK))
  }
}