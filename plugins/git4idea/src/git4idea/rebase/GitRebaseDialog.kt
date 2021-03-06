// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.dvcs.DvcsUtil
import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
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
import com.intellij.util.IconUtil
import com.intellij.util.castSafelyTo
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.StartupUiUtil
import com.intellij.util.ui.UIUtil
import git4idea.*
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
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import java.awt.Container
import java.awt.Insets
import java.awt.event.*
import java.util.Collections.synchronizedMap
import javax.swing.JComponent
import javax.swing.JPanel

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

  private val topPanel = createTopPanel()
  private val bottomPanel = createBottomPanel()
  private val optionsPanel = GitOptionsPanel(::optionChosen, ::getOptionInfo)

  private val panel = createPanel()

  private var okActionTriggered = false

  private var upstreamValidator = RevValidator(upstreamField)
  private var ontoValidator = RevValidator(ontoField)

  private val gitVersion = GitVcs.getInstance(project).version
  private val rebaseMergesAvailable = REBASE_MERGES_REPLACES_PRESERVE_MERGES.existsIn(gitVersion)

  init {
    loadTagsInBackground()

    title = GitBundle.message("rebase.dialog.title")
    setOKButtonText(GitBundle.message("rebase.dialog.start.rebase"))

    loadRefs()
    updateBranches()
    loadSettings()

    updateUi()
    init()

    updateOkActionEnabled()
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
    val upstream = upstreamField.getText()

    if (upstream.isNullOrEmpty() && GitRebaseOption.ROOT !in selectedOptions) {
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
    ProgressManager.getInstance().run(
      object : Task.Backgroundable(project, GitBundle.message("rebase.dialog.progress.loading.tags"), true) {
        override fun run(indicator: ProgressIndicator) {
          val sortedRoots = LinkedHashSet<VirtualFile>(roots.size).apply {
            if (defaultRoot != null) {
              add(defaultRoot)
            }
            addAll(roots)
          }

          sortedRoots.forEach { root ->
            val tagsInRepo = loadTags(root)

            tags[root] = tagsInRepo

            if (getSelectedRepo().root == root) {
              UIUtil.invokeLaterIfNeeded {
                addRefsToOntoAndFrom(tagsInRepo, replace = false)
              }
            }
          }
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
    branchField.model.castSafelyTo<MutableCollectionComboBoxModel<String>>()?.update(localBranches.map { it.name })

    updateBaseFields()
  }

  private fun updateBaseFields() {
    addRefsToOntoAndFrom(localBranches + remoteBranches + getTags(), replace = true)
  }

  private fun addRefsToOntoAndFrom(refs: Collection<GitReference>, replace: Boolean = true) {
    val upstream = upstreamField.item
    val onto = ontoField.item

    val existingRefs = upstreamField.model.castSafelyTo<MutableCollectionComboBoxModel<String>>()?.items?.toSet() ?: emptySet()
    val newRefs = refs.map { it.name }.toSet()

    val result = (if (replace) newRefs else existingRefs + newRefs).toList()

    upstreamField.model.castSafelyTo<MutableCollectionComboBoxModel<String>>()?.update(result)
    ontoField.model.castSafelyTo<MutableCollectionComboBoxModel<String>>()?.update(result)

    upstreamField.item = upstream
    ontoField.item = onto
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
    setMinimumAndPreferredWidth(JBUI.scale(if (showRootField()) 220 else 310))
    setPlaceholder(GitBundle.message("rebase.dialog.new.base"))
    @Suppress("UsePropertyAccessSyntax")
    setUI(FlatComboBoxUI(outerInsets = Insets(BW.get(), 0, BW.get(), 0)))
    addDocumentListener(object : DocumentListener {
      override fun documentChanged(event: DocumentEvent) {
        updateOkActionEnabled()
      }
    })
  }

  private fun createUpstreamField() = ComboBoxWithAutoCompletion<String>(MutableCollectionComboBoxModel(), project).apply {
    prototypeDisplayValue = GIT_REF_PROTOTYPE_VALUE
    setMinimumAndPreferredWidth(JBUI.scale(185))
    setPlaceholder(GitBundle.message("rebase.dialog.target"))
    @Suppress("UsePropertyAccessSyntax")
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
    @Suppress("UsePropertyAccessSyntax")
    setUI(FlatComboBoxUI(
      outerInsets = Insets(BW.get(), 0, BW.get(), 0),
      popupEmptyText = GitBundle.message("merge.branch.popup.empty.text")))
  }

  private fun createPanel() = JPanel().apply {
    layout = MigLayout(LC().insets("0").hideMode(3), AC().grow())

    add(topPanel, CC().growX())
    add(bottomPanel, CC().newline().growX())
    add(optionsPanel, CC().newline())
  }

  private fun createOptionsDropDown() = DropDownLink(GitBundle.message("merge.options.modify")) {
    popupBuilder.createPopup()
  }.apply {
    mnemonic = KeyEvent.VK_M
  }

  private fun createPopupBuilder() = GitOptionsPopupBuilder(project,
                                                            GitBundle.message("rebase.options.modify.dialog.title"),
                                                            { GitRebaseOption.values().toMutableList() },
                                                            OptionListCellRenderer(::getOptionInfo, ::isOptionSelected, ::isOptionEnabled),
                                                            ::optionChosen,
                                                            ::isOptionEnabled)

  private fun isOptionSelected(option: GitRebaseOption) = option in selectedOptions

  private fun isOptionEnabled(option: GitRebaseOption): Boolean {
    if (rebaseMergesAvailable) {
      return true
    }
    return !(option == GitRebaseOption.REBASE_MERGES && selectedOptions.contains(GitRebaseOption.INTERACTIVE)
             || option == GitRebaseOption.INTERACTIVE && selectedOptions.contains(GitRebaseOption.REBASE_MERGES))
  }

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
    if (option in REBASE_FLAGS) {
      updateUpstreamField()
      optionsPanel.rerender(selectedOptions intersect REBASE_FLAGS)
      rerender()
    }
    else {
      updateUi()
    }
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
    updateUpstreamField()
    updateTopPanel()
    updateBottomPanel()
    optionsPanel.rerender(selectedOptions intersect REBASE_FLAGS)
    updatePlaceholders()
    rerender()
  }

  private fun updatePlaceholders() {
    val placeHolder = if (GitRebaseOption.ONTO in selectedOptions)
      GitBundle.message("rebase.dialog.old.base")
    else
      GitBundle.message("rebase.dialog.target")
    upstreamField.setPlaceholder(placeHolder)
  }

  private fun rerender() {
    window.pack()
    window.revalidate()
    pack()
    repaint()
  }

  private fun updateTopPanel() {
    val showOntoField = GitRebaseOption.ONTO in selectedOptions
    ontoLabel.isVisible = showOntoField
    ontoField.isVisible = showOntoField

    if (!showOntoField && !isAlreadyAdded(upstreamField, topPanel)) {
      topPanel.add(upstreamField, getUpstreamFieldConstraints())
    }

    val showBranchField = !showRootField()
                          && !showOntoField
                          && GitRebaseOption.SWITCH_BRANCH in selectedOptions

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
    val showOnto = GitRebaseOption.ONTO in selectedOptions
    val showBranch = (showRoot || showOnto) && GitRebaseOption.SWITCH_BRANCH in selectedOptions

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

  private fun getUpstreamFieldConstraints() = CC()
    .alignY("top")
    .minWidth("${JBUI.scale(if (!showRootField()) 370 else 280)}px")
    .growX()
    .pushX()

  private fun isAlreadyAdded(component: JComponent, container: Container) = component.parent == container

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

    private val HELP_BUTTON_ICON_FOCUSED = if (StartupUiUtil.isUnderDarcula())
      IconUtil.brighter(AllIcons.General.ContextHelp, 3)
    else
      IconUtil.darker(AllIcons.General.ContextHelp, 3)
  }
}