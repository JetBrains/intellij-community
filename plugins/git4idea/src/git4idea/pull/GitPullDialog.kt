// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.pull

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.DvcsUtil.sortRepositories
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.labels.DropDownLink
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import git4idea.GitUtil
import git4idea.config.GitExecutableManager
import git4idea.config.GitVersionSpecialty.NO_VERIFY_SUPPORTED
import git4idea.i18n.GitBundle
import git4idea.merge.dialog.*
import git4idea.repo.GitRemote
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Insets
import java.awt.event.ItemEvent
import java.util.function.Function
import javax.swing.JComponent
import javax.swing.JPanel

class GitPullDialog(private val project: Project,
                    private val roots: List<VirtualFile>,
                    private val defaultRoot: VirtualFile) : DialogWrapper(project) {

  val selectedOptions = mutableSetOf<PullOption>()

  private val repositories = sortRepositories(GitRepositoryManager.getInstance(project).repositories)

  private val branches = repositories.associateWith { repository ->
    repository.branches.remoteBranches
      .sortedBy { branch -> branch.nameForRemoteOperations }
      .groupBy { branch -> branch.remote }
  }

  private val optionInfos = mutableMapOf<PullOption, OptionInfo<PullOption>>()

  private val repositoryField = createRepositoryField()
  private val remoteField = createRemoteField()
  private val branchField = createBranchField()

  private val commandPanel = createCommandPanel()
  private val optionsPanel = createOptionsPanel()

  private val panel = createPanel()

  private val isNoVerifySupported = NO_VERIFY_SUPPORTED.existsIn(GitExecutableManager.getInstance().getVersion(project))

  init {
    updateTitle()
    setOKButtonText(GitBundle.message("pull.button"))
    updateRemotesField()
    init()
  }

  override fun createCenterPanel() = panel

  override fun getPreferredFocusedComponent() = branchField

  override fun createSouthPanel(): JComponent {
    val southPanel = super.createSouthPanel()
    (southPanel.components[0] as JPanel).apply {
      (layout as BorderLayout).hgap = JBUI.scale(5)
      add(createOptionsDropDown(), BorderLayout.EAST)
    }
    return southPanel
  }

  override fun getHelpId() = "reference.VersionControl.Git.Pull"

  override fun getDimensionServiceKey(): String = GitPullDialog::class.java.name

  override fun doValidateAll(): MutableList<ValidationInfo> {
    val branchFieldValidation = validateBranchField()
    if (branchFieldValidation != null) {
      return mutableListOf(branchFieldValidation)
    }
    return mutableListOf()
  }

  fun gitRoot() = getRepository().root

  fun getRemote(): GitRemote = remoteField.item

  fun getSelectedBranches() = listOf(branchField.item)

  fun isCommitAfterMerge() = PullOption.NO_COMMIT !in selectedOptions

  private fun validateBranchField(): ValidationInfo? {
    if (branchField.item.isNullOrEmpty()) {
      return ValidationInfo(GitBundle.message("pull.branch.not.selected.error"), branchField)
    }
    if (branchField.item !in (branchField.model as CollectionComboBoxModel).items) {
      return ValidationInfo(GitBundle.message("pull.branch.no.matching.error"), branchField)
    }
    return null
  }

  private fun getRepository() = repositoryField.item

  private fun updateRemotesField() {
    val repository = getRepository()

    val model = remoteField.model as MutableCollectionComboBoxModel
    model.update(repository.remotes.toList())
    model.selectedItem = getCurrentOrDefaultRemote(repository)
  }

  private fun updateBranchesField() {
    val repository = getRepository()
    val remote = getRemote()

    val branches = getRemoteBranches(repository, remote)

    val model = branchField.model as MutableCollectionComboBoxModel

    model.update(branches)

    val matchingBranch = repository.currentBranch?.findTrackedBranch(repository)?.nameForRemoteOperations
                         ?: branches.find { branch -> branch == repository.currentBranchName }
                         ?: ""

    model.selectedItem = matchingBranch
  }

  private fun getRemoteBranches(repository: GitRepository, remote: GitRemote): List<String> {
    return branches[repository]?.get(remote)?.map { it.nameForRemoteOperations  } ?: emptyList()
  }

  private fun getCurrentOrDefaultRemote(repository: GitRepository): GitRemote? {
    val remotes = repository.remotes
    if (remotes.isEmpty()) {
      return null
    }
    return GitUtil.getTrackInfoForCurrentBranch(repository)?.remote
           ?: GitUtil.getDefaultOrFirstRemote(remotes)
  }

  private fun optionChosen(option: PullOption) {
    if (option !in selectedOptions) {
      selectedOptions += option
    }
    else {
      selectedOptions -= option
    }
    updateUi()
  }

  private fun createOptionsDropDown() = DropDownLink(GitBundle.message("pull.options.modify"),
                                                     Function<DropDownLink<*>?, ListPopupImpl> { createOptionsPopup() }).apply {
    isFocusable = true
  }

  private fun createOptionsPopup() = object : ListPopupImpl(project, createOptionPopupStep()) {
    override fun getListElementRenderer() = OptionListCellRenderer(
      ::getOptionInfo,
      { selectedOptions },
      ::isOptionEnabled
    )
  }

  private fun getOptionInfo(option: PullOption) = optionInfos.computeIfAbsent(option) {
    OptionInfo(option, option.option, GitBundle.message(option.descriptionKey))
  }

  private fun createOptionPopupStep() = object : BaseListPopupStep<PullOption>(GitBundle.message("pull.options.modify.popup.title"), getOptions()) {
    override fun isSelectable(value: PullOption?) = isOptionEnabled(value!!)

    override fun onChosen(selectedValue: PullOption, finalChoice: Boolean) = doFinalStep(Runnable { optionChosen(selectedValue) })
  }

  private fun getOptions() = PullOption.values().toMutableList().apply {
    if (!isNoVerifySupported) {
      remove(PullOption.NO_VERIFY)
    }
  }

  private fun updateUi() {
    updateOptionsPanel()
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

    val shownOptions = mutableSetOf<PullOption>()

    optionsPanel.components.forEach { c ->
      @Suppress("UNCHECKED_CAST") val optionButton = c as OptionButton<PullOption>
      val pullOption = optionButton.option

      if (pullOption !in selectedOptions) {
        optionsPanel.remove(optionButton)
      }
      else {
        shownOptions.add(pullOption)
      }
    }

    selectedOptions.forEach { option ->
      if (option !in shownOptions) {
        optionsPanel.add(createOptionButton(option))
      }
    }
  }

  private fun createOptionButton(option: PullOption) = OptionButton(option, option.option) { optionChosen(option) }

  private fun isOptionEnabled(option: PullOption) = selectedOptions.all { it.isOptionSuitable(option) }

  private fun updateTitle() {
    val currentBranchName = getRepository().currentBranchName
    title = if (currentBranchName.isNullOrEmpty()) {
      GitBundle.message("pull.dialog.title")
    }
    else {
      GitBundle.message("pull.dialog.with.branch.title", currentBranchName)
    }
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

  private fun createOptionsPanel() = JPanel(MigLayout(LC().insets("0").noGrid()))

  private fun createCmdLabel() = CmdLabel("git pull",
                                          Insets(1, if (showRootField()) 0 else 1, 1, 0),
                                          JBDimension(JBUI.scale(85), branchField.preferredSize.height, true))

  private fun createRepositoryField() = ComboBox(CollectionComboBoxModel(repositories)).apply {
    isSwingPopup = false
    renderer = SimpleListCellRenderer.create("") { DvcsUtil.getShortRepositoryName(it) }
    setUI(FlatComboBoxUI(outerInsets = Insets(1, 1, 1, 0)))

    item = repositories.find { repo -> repo.root == defaultRoot }

    addActionListener {
      updateTitle()
      updateRemotesField()
    }
  }

  private fun createRemoteField() = ComboBox<GitRemote>(MutableCollectionComboBoxModel()).apply {
    isSwingPopup = false
    renderer = SimpleListCellRenderer.create(GitBundle.message("util.remote.renderer.none")) { it.name }
    val bw = DarculaUIUtil.BW.get()
    setUI(FlatComboBoxUI(
      outerInsets = Insets(bw, 0, bw, 0),
      popupEmptyText = GitBundle.message("pull.branch.no.matching.remotes")))

    item = getCurrentOrDefaultRemote(getRepository())

    addItemListener { e ->
      if (e.stateChange == ItemEvent.SELECTED) {
        updateBranchesField()
      }
    }
  }

  private fun createBranchField() = ComboBox<String>(MutableCollectionComboBoxModel()).apply {
    isSwingPopup = false
    isEditable = true

    val bw = DarculaUIUtil.BW.get()
    setUI(FlatComboBoxUI(
      Insets(1, 0, 1, 1),
      Insets(bw, 0, bw, bw),
      GitBundle.message("pull.branch.nothing.to.pull")))
  }
}