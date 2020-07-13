// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBTextField
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import git4idea.*
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitRebaseParams
import git4idea.config.GitConfigUtil
import git4idea.config.GitRebaseSettings
import git4idea.i18n.GitBundle
import git4idea.log.GitRefManager.ORIGIN_MASTER_REF
import git4idea.merge.dialog.*
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitUIUtil
import git4idea.util.GitUIUtil.getTextField
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Insets
import java.awt.event.ActionListener
import java.awt.event.ItemEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.plaf.basic.BasicComboBoxEditor

class GitRebaseDialog(private val project: Project,
                      private val roots: List<VirtualFile>,
                      private val defaultRoot: VirtualFile?) : DialogWrapper(project) {

  private val repositoryManager: GitRepositoryManager = GitUtil.getRepositoryManager(project)

  private val rebaseSettings = project.service<GitRebaseSettings>()

  private val selectedOptions = mutableSetOf<RebaseOption>()
  private val optionInfos = mutableMapOf<RebaseOption, OptionInfo<RebaseOption>>()

  private val localBranches = mutableListOf<GitBranch>()
  private val remoteBranches = mutableListOf<GitBranch>()
  private val tags = mutableListOf<GitTag>()

  private var currentBranch: GitBranch? = null

  private val rootField = createRootField()
  private val branchField = createBranchField()
  private val upstreamField = createUpstreamField()

  private val ontoLabel = createOntoLabel()
  private val ontoField = createOntoField()

  private val topPanel = createTopPanel()
  private val bottomPanel = createBottomPanel()
  private val optionsPanel: JPanel = createOptionsPanel()

  private val panel = createPanel()

  private var updatingFields = false

  init {
    title = GitBundle.message("rebase.dialog.title")
    setOKButtonText(GitBundle.message("rebase.dialog.start.rebase"))

    loadRefs()
    updateBranches()
    loadSettings()

    updateUi()
    init()
  }

  override fun createCenterPanel() = panel

  override fun doValidateAll(): MutableList<ValidationInfo> {
    val validationResult = mutableListOf<ValidationInfo>()

    validateNewBase()?.let { validationResult += it }
    validateOnto()?.let { validationResult += it }
    validateUpstream()?.let { validationResult += it }
    validateRebaseInProgress()?.let { validationResult += it }
    validateBranch()?.let { validationResult += it }

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

  fun gitRoot(): VirtualFile = rootField.item

  fun getSelectedParams(): GitRebaseParams {
    val selectedBranch = branchField.item
    val branch = if (currentBranch?.name != selectedBranch) selectedBranch else null

    val showOnto = RebaseOption.ONTO in selectedOptions
    val upstreamField = if (showOnto) ontoField else upstreamField
    val upstream = getTextField(upstreamField).text
    val newBase = if (showOnto) getTextField(ontoField).text else null

    return GitRebaseParams(GitVcs.getInstance(project).version, branch, newBase, upstream,
                           RebaseOption.INTERACTIVE in selectedOptions,
                           RebaseOption.PRESERVE_MERGES in selectedOptions)
  }

  private fun saveSettings() {
    rebaseSettings.options = selectedOptions
    rebaseSettings.newBase = if (RebaseOption.ONTO in selectedOptions)
      getTextField(ontoField).text
    else
      getTextField(upstreamField).text
  }

  private fun loadSettings() {
    rebaseSettings.options.forEach { option -> selectedOptions += option }
    val newBase = rebaseSettings.newBase
    if (!newBase.isNullOrEmpty() && isValidRevision(newBase)) {
      findRef(newBase)?.let { ref ->
        upstreamField.item = ref
      }
    }
  }

  private fun findRef(refName: String): GitReference? {
    val predicate: (GitReference) -> Boolean = { ref -> ref.fullName == refName }
    return localBranches.find(predicate)
           ?: remoteBranches.find(predicate)
           ?: tags.find(predicate)
  }

  private fun validateNewBase(): ValidationInfo? {
    val field = if (RebaseOption.ONTO in selectedOptions) ontoField else upstreamField
    if (getTextField(field).text.isEmpty()) {
      return ValidationInfo(GitBundle.message("rebase.dialog.error.base.not.selected"), field)
    }
    return null
  }

  private fun validateUpstream(): ValidationInfo? {
    val upstream = getTextField(upstreamField).text

    if (upstream.isNullOrEmpty()) {
      return if (RebaseOption.ONTO in selectedOptions)
        ValidationInfo(GitBundle.message("rebase.dialog.error.upstream.not.selected"), upstreamField)
      else
        ValidationInfo(GitBundle.message("rebase.dialog.error.base.not.selected"), upstreamField)
    }

    if (!isValidRevision(upstream)) {
      return ValidationInfo(GitBundle.message("rebase.dialog.error.branch.or.tag.not.exist"), upstreamField)
    }

    return null
  }

  private fun validateOnto(): ValidationInfo? {
    if (RebaseOption.ONTO in selectedOptions) {
      val newBase = getTextField(ontoField).text

      if (newBase.isNullOrEmpty()) {
        return ValidationInfo(GitBundle.message("rebase.dialog.error.base.not.selected"), ontoField)
      }

      if (!isValidRevision(newBase)) {
        return ValidationInfo(GitBundle.message("rebase.dialog.error.branch.or.tag.not.exist"), ontoField)
      }
    }
    return null
  }

  private fun isValidRevision(revision: String): Boolean {
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
    if (RebaseOption.SWITCH_BRANCH !in selectedOptions) {
      return null
    }
    val selectedBranch = getTextField(branchField).text
    if (selectedBranch.isNullOrEmpty()) {
      return ValidationInfo(GitBundle.message("rebase.dialog.error.branch.not.selected"), branchField)
    }
    if (selectedBranch !in localBranches.map { it.name }) {
      return ValidationInfo(GitBundle.message("rebase.dialog.error.branch.invalid", selectedBranch), branchField)
    }
    return null
  }

  private fun validateRebaseInProgress(): ValidationInfo? {
    if (repositoryManager.getRepositoryForRootQuick(gitRoot())!!.isRebaseInProgress) {
      return ValidationInfo(GitBundle.message("rebase.dialog.error.rebase.in.progress"))
    }
    return null
  }

  private fun loadRefs() {
    localBranches.clear()
    remoteBranches.clear()
    tags.clear()

    val root = gitRoot()
    val repository = GitUtil.getRepositoryManager(project).getRepositoryForRootQuick(root)
    check(repository != null) { "Repository is null for root $root" }

    currentBranch = repository.currentBranch

    localBranches += GitBranchUtil.sortBranchesByName(repository.branches.localBranches)
    remoteBranches += GitBranchUtil.sortBranchesByName(repository.branches.remoteBranches)

    tags += loadTags(root)
  }

  private fun loadTags(root: VirtualFile): List<GitTag> {
    val task = ThrowableComputable<List<GitTag>, VcsException> {
      GitBranchUtil.getAllTags(project, root).map { GitTag(it) }
    }
    return ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(task, GitBundle.message("rebase.dialog.progress.loading.tags"), true, project)
  }

  private fun updateBranches() {
    updatingFields = true

    branchField.removeAllItems()
    for (b in localBranches) {
      branchField.addItem(b.name)
    }
    if (currentBranch != null) {
      branchField.selectedItem = currentBranch!!.name
    }
    else {
      branchField.selectedItem = null
    }
    updateBaseFields()
    updateTrackedBranch()

    updatingFields = false
  }

  private fun loadBranchConfig(root: VirtualFile): Pair<String?, String?> {
    val task = ThrowableComputable<Pair<String?, String?>, VcsException> {
      val remote = GitConfigUtil.getValue(project, root, "branch.$currentBranch.remote")
      val mergeBranch = GitConfigUtil.getValue(project, root, "branch.$currentBranch.merge")

      remote to mergeBranch
    }

    return ProgressManager.getInstance()
      .runProcessWithProgressSynchronously(task, GitBundle.message("rebase.dialog.progress.loading.branch.info"), true, project)
  }

  private fun updateTrackedBranch() {
    try {
      val root = gitRoot()
      val currentBranch = branchField.item
      var trackedBranch: GitBranch? = null

      if (currentBranch != null) {
        var (remote, mergeBranch) = loadBranchConfig(root)

        val repository = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(root)
        check(repository != null) { GitBundle.message("repository.not.found.error", root.presentableUrl) }

        if (remote == null || mergeBranch == null) {
          trackedBranch = repository.branches.findBranchByName("master")
        }
        else {
          mergeBranch = GitBranchUtil.stripRefsPrefix(mergeBranch)
          if (remote == ".") {
            trackedBranch = GitSvnRemoteBranch(mergeBranch)
          }
          else {
            val r = GitUtil.findRemoteByName(repository, remote)
            if (r != null) {
              trackedBranch = GitStandardRemoteBranch(r, mergeBranch)
            }
          }
        }
      }

      val newBaseField = if (RebaseOption.ONTO in selectedOptions) ontoField else upstreamField
      if (trackedBranch != null) {
        newBaseField.setSelectedItem(trackedBranch)
      }
      else {
        getTextField(newBaseField).text = ""
      }
    }
    catch (e: VcsException) {
      GitUIUtil.showOperationError(project, e, "git config")
    }
  }

  private fun updateBaseFields() {
    val upstream = upstreamField.item
    val onto = ontoField.item

    upstreamField.removeAllItems()
    ontoField.removeAllItems()

    addRefsToOntoAndFrom(localBranches)
    addRefsToOntoAndFrom(remoteBranches)
    addRefsToOntoAndFrom(tags)

    upstreamField.item = remoteBranches.find { branch -> branch.fullName == ORIGIN_MASTER_REF } ?: upstream
    ontoField.item = onto
  }

  private fun addRefsToOntoAndFrom(refs: Collection<GitReference>) = refs.forEach { ref ->
    upstreamField.addItem(ref)
    ontoField.addItem(ref)
  }

  private fun showRootField() = roots.size > 1

  private fun createTopPanel() = JPanel().apply {
    layout = MigLayout(
      LC()
        .fillX()
        .insets("0")
        .gridGap("0", "0")
        .hideMode(3)
        .noVisualPadding())

    if (showRootField()) {
      add(rootField,
          CC()
            .gapAfter("0")
            .minWidth("${JBUI.scale(110)}px"))
    }

    add(createCmdLabel(),
        CC()
          .alignY("top")
          .gapAfter("0")
          .minWidth("${JBUI.scale(100)}px"))

    add(ontoLabel,
        CC()
          .alignY("top")
          .gapAfter("0")
          .minWidth("${JBUI.scale(60)}px"))

    add(ontoField,
        CC()
          .gapAfter("0")
          .minWidth("${JBUI.scale(if (showRootField()) 220 else 310)}px")
          .growX()
          .pushX())

    add(upstreamField, getUpstreamFieldConstraints())
  }

  private fun createBottomPanel() = JPanel().apply {
    layout = MigLayout(
      LC()
        .fillX()
        .insets("0")
        .gridGap("0", "0")
        .hideMode(3)
        .noVisualPadding())

    isVisible = false
  }

  private fun createOptionsPanel() = JPanel(MigLayout(LC().insets("0").noGrid())).apply { isVisible = false }

  private fun createCmdLabel() = CmdLabel("git rebase",
                                          Insets(1, if (showRootField()) 0 else 1, 1, 0),
                                          JBDimension(JBUI.scale(85), branchField.preferredSize.height, true))

  private fun createOntoLabel() = CmdLabel("--onto",
                                           Insets(1, 1, 1, 0),
                                           JBDimension(JBUI.scale(60), branchField.preferredSize.height)).apply {
    isVisible = false
  }

  private fun createOntoField() = ComboBox<GitReference>(MutableCollectionComboBoxModel()).apply {
    setMinimumAndPreferredWidth(JBUI.scale(if (showRootField()) 220 else 310))
    isEditable = true
    isVisible = false
    editor = object : BasicComboBoxEditor() {
      override fun createEditorComponent() = JBTextField().apply {
        emptyText.text = GitBundle.message("rebase.dialog.onto.field")

        document.addDocumentListener(object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            startTrackingValidation()
          }
        })
      }
    }
    ui = FlatComboBoxUI(outerInsets = Insets(BW.get(), 0, BW.get(), 0))
  }

  private fun createUpstreamField() = ComboBox<GitReference>(MutableCollectionComboBoxModel()).apply {
    setMinimumAndPreferredWidth(JBUI.scale(185))
    isEditable = true
    editor = object : BasicComboBoxEditor() {
      override fun createEditorComponent() = JBTextField().apply {
        emptyText.text = GitBundle.message("rebase.dialog.upstream.field.placeholder")

        document.addDocumentListener(object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            startTrackingValidation()
          }
        })
      }
    }
    ui = FlatComboBoxUI(outerInsets = Insets(BW.get(), 0, BW.get(), 0))
  }

  private fun createRootField() = ComboBox(CollectionComboBoxModel(roots)).apply {
    isSwingPopup = false
    renderer = SimpleListCellRenderer.create("(invalid)") { it.name }
    ui = FlatComboBoxUI(outerInsets = Insets(BW.get(), BW.get(), BW.get(), 0))
    item = defaultRoot ?: roots[0]

    val listener = ActionListener {
      loadRefs()
      updateBranches()
    }
    addActionListener(listener)
  }

  private fun createBranchField() = ComboBox<String>(MutableCollectionComboBoxModel()).apply {
    isSwingPopup = false
    isEditable = true
    editor = object : BasicComboBoxEditor() {
      override fun createEditorComponent() = JBTextField().apply {
        emptyText.text = GitBundle.message("rebase.dialog.branch.field")

        document.addDocumentListener(object : DocumentAdapter() {
          override fun textChanged(e: DocumentEvent) {
            startTrackingValidation()
          }
        })
      }
    }
    ui = FlatComboBoxUI(
      outerInsets = Insets(BW.get(), 0, BW.get(), 0),
      popupEmptyText = GitBundle.message("merge.branch.popup.empty.text"))

    addItemListener { e ->
      if (!updatingFields && e.stateChange == ItemEvent.SELECTED) {
        updateTrackedBranch()
      }
    }
  }

  private fun createPanel() = JPanel().apply {
    layout = MigLayout(LC().insets("0").hideMode(3), AC().grow())

    add(topPanel, CC().growX())
    add(bottomPanel, CC().newline().growX())
    add(optionsPanel, CC().newline())
  }

  private fun createOptionsDropDown() = DropDownLink(GitBundle.message("merge.options.modify")) { createOptionsPopup() }.apply {
    mnemonic = KeyEvent.VK_M
  }

  private fun createOptionsPopup() = object : ListPopupImpl(project, createOptionPopupStep()) {
    override fun getListElementRenderer() = OptionListCellRenderer(
      ::getOptionInfo,
      { selectedOptions },
      { isOptionEnabled(it) })
  }

  private fun isOptionEnabled(option: RebaseOption) = selectedOptions.all { it.isOptionSuitable(option) }

  private fun getOptionInfo(option: RebaseOption) = optionInfos.computeIfAbsent(option) {
    OptionInfo(option, option.option, GitBundle.message(option.descriptionKey))
  }

  private fun createOptionPopupStep() = object : BaseListPopupStep<RebaseOption>(GitBundle.message("rebase.options.modify.dialog.title"),
                                                                                 RebaseOption.values().toMutableList()) {

    override fun onChosen(selectedValue: RebaseOption?, finalChoice: Boolean) = doFinalStep(Runnable { optionChosen(selectedValue!!) })

    override fun isSelectable(value: RebaseOption?) = isOptionEnabled(value!!)
  }

  private fun optionChosen(option: RebaseOption) {
    if (option !in selectedOptions) {
      selectedOptions += option
    }
    else {
      selectedOptions -= option
    }
    if (option in getOptionsToShowInPanel()) {
      updateOptionsPanel()
      rerender()
    }
    else {
      updateUi()
    }
  }

  private fun updateUi() {
    updateTopPanel()
    updateBottomPanel()
    updateOptionsPanel()
    rerender()
  }

  private fun rerender() {
    window.pack()
    window.revalidate()
    pack()
    repaint()
  }

  private fun updateTopPanel() {
    val showOntoField = RebaseOption.ONTO in selectedOptions
    ontoLabel.isVisible = showOntoField
    ontoField.isVisible = showOntoField

    if (!showOntoField && !isAlreadyAdded(upstreamField, topPanel)) {
      topPanel.add(upstreamField, getUpstreamFieldConstraints())
    }

    val showBranchField = !showRootField()
                          && !showOntoField
                          && RebaseOption.SWITCH_BRANCH in selectedOptions

    var isDirty = false
    if (showBranchField) {
      if (!isAlreadyAdded(branchField, topPanel)) {
        topPanel.add(branchField, CC().alignY("top"))

        val layout = topPanel.layout as MigLayout

        val constraints = CC().minWidth("${JBUI.scale(185)}px").growX().pushX().alignY("top")
        layout.setComponentConstraints(upstreamField, constraints)
        layout.setComponentConstraints(branchField, constraints)

        isDirty = true
      }
    }
    else {
      topPanel.remove(branchField)
      isDirty = true
    }

    if (isDirty && isAlreadyAdded(upstreamField, topPanel)) {
      (upstreamField.ui as FlatComboBoxUI).apply {
        border = Insets(1, 1, 1, if (!showBranchField) 1 else 0)
      }
      if (!showBranchField) {
        (topPanel.layout as MigLayout).setComponentConstraints(upstreamField, getUpstreamFieldConstraints())
      }
    }
  }

  private fun updateBottomPanel() {
    val showRoot = showRootField()
    val showOnto = RebaseOption.ONTO in selectedOptions
    val showBranch = (showRoot || showOnto) && RebaseOption.SWITCH_BRANCH in selectedOptions

    if (showOnto) {
      if (!isAlreadyAdded(upstreamField, bottomPanel)) {
        bottomPanel.add(upstreamField, 0)
      }
      (upstreamField.ui as FlatComboBoxUI).apply {
        border = Insets(1, 1, 1, if (!showBranch) 1 else 0)
      }
    }
    if (showBranch) {
      if (!isAlreadyAdded(branchField, bottomPanel)) {
        bottomPanel.add(branchField)
      }
    }
    else {
      bottomPanel.remove(branchField)
    }

    bottomPanel.isVisible = bottomPanel.components.isNotEmpty()

    val layout = bottomPanel.layout as MigLayout
    bottomPanel.components.forEach { component ->
      layout.setComponentConstraints(component, getBottomPanelComponentConstraints(bottomPanel.componentCount == 1))
    }
  }

  private fun getBottomPanelComponentConstraints(singleInRow: Boolean) = CC().alignY("top").width(if (singleInRow) "100%" else "50%")

  private fun updateOptionsPanel() {
    val selectedOptionsToShow = selectedOptions intersect getOptionsToShowInPanel()

    val shownOptions = mutableSetOf<RebaseOption>()
    optionsPanel.components.forEach { c ->
      @Suppress("UNCHECKED_CAST")
      val optionButton = c as OptionButton<RebaseOption>
      val rebaseOption = optionButton.option

      if (rebaseOption in selectedOptionsToShow) {
        shownOptions.add(rebaseOption)
      }
      else {
        optionsPanel.remove(optionButton)
      }
    }

    selectedOptionsToShow.forEach { option ->
      if (option !in shownOptions) {
        optionsPanel.add(createOptionButton(option))
      }
    }

    optionsPanel.isVisible = selectedOptionsToShow.isNotEmpty()
  }

  private fun getUpstreamFieldConstraints() = CC()
    .alignY("top")
    .minWidth("${JBUI.scale(if (!showRootField()) 370 else 280)}px")
    .growX()
    .pushX()

  private fun getOptionsToShowInPanel() = setOf(RebaseOption.INTERACTIVE, RebaseOption.PRESERVE_MERGES)

  private fun createOptionButton(option: RebaseOption) = OptionButton(option, option.option) { optionChosen(option) }

  private fun isAlreadyAdded(component: JComponent, container: Container) = component.parent == container

  companion object {
    val LOG = Logger.getInstance(GitRebaseDialog::class.java)
  }
}