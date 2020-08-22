// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase

import com.intellij.icons.AllIcons
import com.intellij.ide.ui.laf.darcula.DarculaUIUtil.BW
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.popup.IconButton
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.InplaceButton
import com.intellij.ui.MutableCollectionComboBoxModel
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBTextField
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.BooleanFunction
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import git4idea.*
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitRebaseParams
import git4idea.config.GitRebaseSettings
import git4idea.config.GitVersionSpecialty.REBASE_MERGES_REPLACES_PRESERVE_MERGES
import git4idea.i18n.GitBundle
import git4idea.merge.dialog.*
import git4idea.rebase.ComboBoxPrototypeRenderer.Companion.COMBOBOX_VALUE_PROTOTYPE
import git4idea.repo.GitRepositoryManager
import git4idea.util.GitUIUtil.getTextField
import net.miginfocom.layout.AC
import net.miginfocom.layout.CC
import net.miginfocom.layout.LC
import net.miginfocom.swing.MigLayout
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Container
import java.awt.Dimension
import java.awt.Insets
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.DefaultListCellRenderer
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.plaf.basic.BasicComboBoxEditor

internal class GitRebaseDialog(private val project: Project,
                               private val roots: List<VirtualFile>,
                               private val defaultRoot: VirtualFile?) : DialogWrapper(project) {

  private val repositoryManager: GitRepositoryManager = GitUtil.getRepositoryManager(project)

  private val rebaseSettings = project.service<GitRebaseSettings>()

  private val selectedOptions = mutableSetOf<GitRebaseOption>()
  private val optionInfos = mutableMapOf<GitRebaseOption, OptionInfo<GitRebaseOption>>()

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

    updateUi()
    init()

    startTrackingValidation()
  }

  override fun createCenterPanel() = panel

  override fun doValidateAll(): MutableList<ValidationInfo> {
    val validationResult = mutableListOf<ValidationInfo>()

    validateNewBase()?.let { validationResult += it }
    validateOnto()?.let { validationResult += it }
    validateUpstream()?.let { validationResult += it }
    validateRebaseInProgress()?.let { validationResult += it }
    validateBranch()?.let { validationResult += it }

    okActionTriggered = false

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

  override fun createDefaultActions() {
    super.createDefaultActions()

    myOKAction = object : OkAction() {
      override fun doAction(e: ActionEvent?) {
        okActionTriggered = true
        super.doAction(e)
      }
    }
  }

  fun gitRoot(): VirtualFile = rootField.item

  fun getSelectedParams(): GitRebaseParams {
    val branch = branchField.item

    val newBase = if (GitRebaseOption.ONTO in selectedOptions) getTextField(ontoField).text else null
    val upstream = getTextField(upstreamField).text

    return GitRebaseParams(gitVersion, branch, newBase, upstream, selectedOptions intersect REBASE_FLAGS)
  }

  private fun saveSettings() {
    rebaseSettings.options = selectedOptions intersect REBASE_FLAGS
    rebaseSettings.newBase = if (GitRebaseOption.ONTO in selectedOptions)
      getTextField(ontoField).text
    else
      getTextField(upstreamField).text
  }

  private fun loadSettings() {
    rebaseSettings.options.forEach { option -> selectedOptions += option }
    val newBase = rebaseSettings.newBase
    if (!newBase.isNullOrEmpty() && isValidRevision(newBase)) {
      findRef(newBase)?.let { ref ->
        upstreamField.item = PresentableRef(ref)
      }
    }
  }

  private fun findRef(refName: String): GitReference? {
    val predicate: (GitReference) -> Boolean = { ref -> ref.name == refName }
    return localBranches.find(predicate)
           ?: remoteBranches.find(predicate)
           ?: tags.find(predicate)
  }

  private fun validateNewBase(): ValidationInfo? {
    val field = if (GitRebaseOption.ONTO in selectedOptions) ontoField else upstreamField
    if (getTextField(field).text.isEmpty()) {
      return ValidationInfo(GitBundle.message("rebase.dialog.error.base.not.selected"), field)
    }
    return null
  }

  private fun validateUpstream(): ValidationInfo? {
    val upstream = getTextField(upstreamField).text

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
      val newBase = getTextField(ontoField).text

      if (newBase.isNullOrEmpty()) {
        return ValidationInfo(GitBundle.message("rebase.dialog.error.base.not.selected"), ontoField)
      }

      return ontoValidator.validate()
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
    if (GitRebaseOption.SWITCH_BRANCH !in selectedOptions) {
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
    val repository = repositoryManager.getRepositoryForRootQuick(root)
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
    branchField.removeAllItems()
    for (b in localBranches) {
      branchField.addItem(b.name)
    }
    branchField.item = null

    updateBaseFields()
  }

  private fun updateBaseFields() {
    val upstream = upstreamField.item
    val onto = ontoField.item

    upstreamField.removeAllItems()
    ontoField.removeAllItems()

    addRefsToOntoAndFrom(localBranches)
    addRefsToOntoAndFrom(remoteBranches)
    addRefsToOntoAndFrom(tags)

    upstreamField.item = upstream
    ontoField.item = onto
  }

  private fun addRefsToOntoAndFrom(refs: Collection<GitReference>) = refs.forEach { gitRef ->
    val ref = PresentableRef(gitRef)
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
                                           JBDimension(JBUI.scale(80), branchField.preferredSize.height, true)).apply {
    isVisible = false

    addComponent(createOntoHelpButton())
  }

  private fun createOntoHelpButton() = InplaceButton(
    IconButton(GitBundle.message("rebase.dialog.help"), AllIcons.General.ContextHelp),
    ActionListener {
      showRebaseHelpPopup()
    }
  ).apply {
    border = JBUI.Borders.empty(1)
  }

  private fun showRebaseHelpPopup() {
    val helpPopup = GitRebaseHelpPopupPanel()
    JBPopupFactory
      .getInstance()
      .createComponentPopupBuilder(helpPopup, helpPopup.helpLink)
      .setMayBeParent(true)
      .setFocusable(true)
      .setRequestFocus(true)
      .setCancelOnWindowDeactivation(false)
      .createPopup()
      .showUnderneathOf(rootPane)
  }

  private fun createOntoField() = ComboBox<PresentableRef>(MutableCollectionComboBoxModel()).apply {
    setMinimumAndPreferredWidth(JBUI.scale(if (showRootField()) 220 else 310))
    isSwingPopup = false
    isEditable = true
    isVisible = false
    editor = createFieldEditor(GitBundle.message("rebase.dialog.new.base"))
    prototypeDisplayValue = PresentableRef(GitLocalBranch(COMBOBOX_VALUE_PROTOTYPE))
    renderer = ComboBoxPrototypeRenderer.create(this, PresentableRef::toString)
    @Suppress("UsePropertyAccessSyntax")
    setUI(FlatComboBoxUI(outerInsets = Insets(BW.get(), 0, BW.get(), 0)))
  }

  private fun createUpstreamField() = ComboBox<PresentableRef>(MutableCollectionComboBoxModel()).apply {
    setMinimumAndPreferredWidth(JBUI.scale(185))
    isSwingPopup = false
    isEditable = true
    editor = createFieldEditor(GitBundle.message("rebase.dialog.target"))
    prototypeDisplayValue = PresentableRef(GitLocalBranch(COMBOBOX_VALUE_PROTOTYPE))
    renderer = ComboBoxPrototypeRenderer.create(this, PresentableRef::toString)
    @Suppress("UsePropertyAccessSyntax")
    setUI(FlatComboBoxUI(outerInsets = Insets(BW.get(), 0, BW.get(), 0)))
  }

  private fun createRootField() = ComboBox(CollectionComboBoxModel(roots)).apply {
    isSwingPopup = false
    renderer = SimpleListCellRenderer.create(GitBundle.message("rebase.dialog.invalid.root")) { it.name }
    @Suppress("UsePropertyAccessSyntax")
    setUI(FlatComboBoxUI(outerInsets = Insets(BW.get(), BW.get(), BW.get(), 0)))
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
    editor = createFieldEditor(GitBundle.message("rebase.dialog.branch.field"))
    @Suppress("UsePropertyAccessSyntax")
    setUI(FlatComboBoxUI(
      outerInsets = Insets(BW.get(), 0, BW.get(), 0),
      popupEmptyText = GitBundle.message("merge.branch.popup.empty.text")))
  }

  private fun createFieldEditor(@Nls placeHolder: String) = object : BasicComboBoxEditor() {
    override fun createEditorComponent() = object : JBTextField() {
      init {
        putClientProperty("StatusVisibleFunction", BooleanFunction<JBTextField> { textField -> textField.text.isNullOrEmpty() })
        emptyText.text = placeHolder
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
      (selectedValues.firstOrNull() as? GitRebaseOption)?.let { option -> optionChosen(option) }

      list.repaint()
    }
  }

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

  private fun createOptionPopupStep() = object : BaseListPopupStep<GitRebaseOption>(GitBundle.message("rebase.options.modify.dialog.title"),
                                                                                    GitRebaseOption.values().toMutableList()) {

    override fun onChosen(selectedValue: GitRebaseOption?, finalChoice: Boolean) = doFinalStep(Runnable { optionChosen(selectedValue!!) })

    override fun isSelectable(value: GitRebaseOption?) = isOptionEnabled(value!!)
  }

  private fun optionChosen(option: GitRebaseOption) {
    if (option !in selectedOptions) {
      selectedOptions += option
    }
    else {
      selectedOptions -= option
    }
    if (option in REBASE_FLAGS) {
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
    updatePlaceholders()
    rerender()
  }

  private fun updatePlaceholders() {
    (getTextField(upstreamField) as JBTextField).apply {
      emptyText.text = if (GitRebaseOption.ONTO in selectedOptions)
        GitBundle.message("rebase.dialog.old.base")
      else
        GitBundle.message("rebase.dialog.target")
    }
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

  private fun updateOptionsPanel() {
    val selectedOptionsToShow = selectedOptions intersect REBASE_FLAGS

    val shownOptions = mutableSetOf<GitRebaseOption>()
    optionsPanel.components.forEach { c ->
      @Suppress("UNCHECKED_CAST")
      val optionButton = c as OptionButton<GitRebaseOption>
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

  private fun createOptionButton(option: GitRebaseOption) = OptionButton(option, option.getOption(gitVersion)) { optionChosen(option) }

  private fun isAlreadyAdded(component: JComponent, container: Container) = component.parent == container

  internal inner class RevValidator(private val field: ComboBox<PresentableRef>) {

    private var lastValidatedRevision = ""
    private var lastValid = true

    fun validate(): ValidationInfo? {
      val revision = getTextField(field).text

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

  data class PresentableRef(private val ref: GitReference) {
    override fun toString() = ref.name
  }
}

internal abstract class ComboBoxPrototypeRenderer<E> private constructor(private val comboBox: ComboBox<E>) : SimpleListCellRenderer<E>() {

  private val rememberedSize = sizeToPair(calcPrototypeSize())

  override fun getPreferredSize(): Dimension {
    if (comboBox.prototypeDisplayValue == null) {
      return super.getPreferredSize()
    }
    return pairToSize(rememberedSize)
  }

  private fun calcPrototypeSize(): Dimension {
    return DefaultListCellRenderer()
      .getListCellRendererComponent(comboBox.popup?.list, comboBox.prototypeDisplayValue, -1, false, false)
      .preferredSize
  }

  companion object {
    const val COMBOBOX_VALUE_PROTOTYPE = "origin/quite-long-branch-name"

    fun <T> create(comboBox: ComboBox<T>,
                   renderer: (T) -> @NlsContexts.Label String): ComboBoxPrototypeRenderer<T> {

      return object : ComboBoxPrototypeRenderer<T>(comboBox) {
        override fun customize(list: JList<out T>, value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
          text = if (value == null) "" else renderer(value)
        }
      }
    }

    private fun sizeToPair(size: Dimension) = Pair(size.width, size.height)

    private fun pairToSize(pair: Pair<Int, Int>) = Dimension(pair.first, pair.second)
  }
}