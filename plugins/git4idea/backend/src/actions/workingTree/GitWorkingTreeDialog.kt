// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.workingTree

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.observable.util.whenDocumentChanged
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.ui.validation.WHEN_GRAPH_PROPAGATION_FINISHED
import com.intellij.openapi.ui.validation.WHEN_PROPERTY_CHANGED
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.bindItem
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.PathUtil.isValidFileName
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.textCompletion.TextFieldWithCompletion
import com.intellij.util.ui.JBUI
import com.intellij.vcs.git.ui.GitBranchesTreeIconProvider
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitReference
import git4idea.GitRemoteBranch
import git4idea.GitStandardLocalBranch
import git4idea.GitWorkingTree
import git4idea.branch.GitNewBranchDialog
import git4idea.branch.GitNewBranchDialog.Companion.cleanBranchNameAndAdjustCursorIfNeeded
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.validators.checkRefName
import git4idea.repo.tags
import git4idea.validators.GitRefNameValidator
import git4idea.workingTrees.GitWorkingTreesService
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Dimension
import java.nio.file.InvalidPathException
import java.nio.file.NoSuchFileException
import java.nio.file.NotDirectoryException
import java.nio.file.Paths
import java.util.Vector
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent
import javax.swing.JList
import kotlin.io.path.isWritable
import kotlin.io.path.name
import kotlin.io.path.useDirectoryEntries
import kotlin.math.min

internal class GitWorkingTreeDialog(
  private val data: GitWorkingTreePreDialogData,
) : DialogWrapper(data.project, false) {
  private val propertyGraph = PropertyGraph("Git Working Tree Dialog")
  private val branchToWorkingTreeMap = data.repository.workingTreeHolder.getWorkingTrees()
    .filter { it.currentBranch != null }
    .associateBy { it.currentBranch!! }
  private val localBranchNames = data.repository.branches.localBranches.map { it.name }

  private lateinit var parentPathCell: Cell<TextFieldWithBrowseButton>
  private lateinit var projectNameCell: Cell<JBTextField>

  private val validator = GitRefNameValidator.getInstance()
  private val existingRefWithWorkingTree: GraphProperty<RefWithWorkingTree?>
  private val projectName: GraphProperty<String>
  private val parentPath: GraphProperty<String>
  private val createNewBranch: GraphProperty<Boolean>
  private val newBranchName: GraphProperty<String>

  init {
    existingRefWithWorkingTree = propertyGraph.property(data.initialExistingRef?.toRefWithWorkingTree())
    createNewBranch = propertyGraph.property(false)
    newBranchName = propertyGraph.property("")
    projectName = propertyGraph.property(suggestProjectName())
    parentPath = propertyGraph.property(data.initialParentPath ?: "")
    listOf(existingRefWithWorkingTree, createNewBranch, newBranchName).forEach {
      propertyGraph.dependsOn(projectName, it, true, ::suggestProjectName)
    }
    init()
    title = GitBundle.message("working.tree.dialog.title")
    setOKButtonText(GitBundle.message("working.tree.dialog.button.ok"))
  }

  private data class RefWithWorkingTree(val ref: GitReference, val workingTree: GitWorkingTree?)

  private fun GitReference.toRefWithWorkingTree(): RefWithWorkingTree {
    return if (this is GitStandardLocalBranch) {
      RefWithWorkingTree(this, branchToWorkingTreeMap[this])
    }
    else {
      RefWithWorkingTree(this, null)
    }
  }

  override fun getDimensionServiceKey(): String = "Git.CreateWorkingTreeDialog"

  override fun getInitialSize(): Dimension {
    return Dimension(JBUI.DialogSizes.medium().width, -1)
  }

  override fun createCenterPanel(): JComponent {
    val newBranchNameField = TextFieldWithCompletion(data.project, createBranchNameCompletion(), "", true, true, false, false)
      .apply {
        minimumSize = JBUI.size(240, 0)
        setupCleanBranchNameAndAdjustCursorIfNeeded()
        whenDocumentChanged(disposable) {
          newBranchName.set(text)
          createNewBranch.set(text.isNotEmpty())
        }
    }

    return panel {
      row(GitBundle.message("working.tree.dialog.label.existing.branch")) {
        createRefComboBox()
          .bindItem(existingRefWithWorkingTree)
          .align(Align.FILL)
      }

      row {
        checkBox(GitBundle.message("working.tree.dialog.checkbox.new.branch"))
          .bindSelected(createNewBranch)
          .gap(RightGap.SMALL)

        cell(newBranchNameField)
          .align(Align.FILL)
          .validationRequestor(WHEN_GRAPH_PROPAGATION_FINISHED(propertyGraph))
          .validationOnInput { validateNewBranchNameField() }
          .validationOnApply { validateNewBranchNameField() }
      }
        .bottomGap(BottomGap.MEDIUM)
        .layout(RowLayout.LABEL_ALIGNED)

      row(GitBundle.message("working.tree.dialog.label.name")) {
        projectNameCell = textField()
          .bindText(projectName)
          .align(Align.FILL)
          .validationRequestor(WHEN_GRAPH_PROPAGATION_FINISHED(propertyGraph))
          .validationOnInput { validateProjectName() }
          .validationOnApply { validateProjectName() }
      }
      row(GitBundle.message("working.tree.dialog.label.location")) {
        val descriptor = FileChooserDescriptorFactory.singleDir()
          .withTitle(GitBundle.message("working.tree.dialog.label.location.file.chooser.title"))
        parentPathCell = textFieldWithBrowseButton(descriptor, data.project)
          .bindText(parentPath)
          .align(Align.FILL)
          .validationRequestor(WHEN_GRAPH_PROPAGATION_FINISHED(propertyGraph))
          .validationOnInput { validateLocationOnInput() }
          .validationOnApply { validateLocationOnApply() }
          .comment("", maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)

        supportPathComment()
      }
    }
  }

  private fun Row.createRefComboBox(): Cell<ComboBox<RefWithWorkingTree?>> {
    val localRefsWithTrees: List<RefWithWorkingTree?> = computeRefsWithWorkingTrees()
    val model = DefaultComboBoxModel(Vector(localRefsWithTrees))
    val component = object : ComboBox<RefWithWorkingTree?>(model) {
      override fun getPreferredSize(): Dimension? {
        val dimension = super.getPreferredSize()
        dimension.width = min(dimension.width, JBUI.scale(300))
        return dimension
      }
    }
    component.isSwingPopup = false
    component.isUsePreferredSizeAsMinimum = false
    component.renderer = RefWithTreeCellRenderer(data.project, data.repository)

    // Set prototype to calculate proper size upfront and prevent resizing on first selection
    val longestRef = localRefsWithTrees.maxByOrNull { it?.ref?.name?.length ?: 0 }
    if (longestRef != null) {
      component.prototypeDisplayValue = longestRef
    }

    return cell(component)
      .validationRequestor(WHEN_PROPERTY_CHANGED(createNewBranch))
      .validationRequestor(WHEN_PROPERTY_CHANGED(existingRefWithWorkingTree))
      .validationOnInput { validateExistingRefOnInput() }
      .validationOnApply { validateExistingRefOnApply() }
  }

  private fun ValidationInfoBuilder.validateExistingRefOnInput(): ValidationInfo? {
    val value = existingRefWithWorkingTree.get()
    return if (value?.workingTree != null && !createNewBranch.get()) {
      error(GitBundle.message("working.tree.dialog.branch.validation.already.checked.out.in.working.tree")).asWarning()
    }
    else {
      null
    }
  }

  private fun ValidationInfoBuilder.validateExistingRefOnApply(): ValidationInfo? {
    val value = existingRefWithWorkingTree.get()
    if (value == null) {
      return error(GitBundle.message("working.tree.dialog.location.validation.select.branch"))
    }
    val ref = value.ref
    if (ref is GitRemoteBranch && !createNewBranch.get()) {
      val defaultLocalBranchName = ref.nameForRemoteOperations
      // can have remote conflict if git-svn is used - suggested local name will be equal to selected remote,
      // see git4idea.remote.hosting.GitRemoteBranchesUtil.checkoutRemoteBranch
      if (GitReference.BRANCH_NAME_HASHING_STRATEGY.equals(defaultLocalBranchName, ref.name)) {
        return error(GitBundle.message("working.tree.dialog.branch.validation.provide.explicit.local.branch.name", ref.name))
      }
      if (localBranchNames.contains(defaultLocalBranchName)) {
        return error(GitBundle.message("working.tree.dialog.branch.validation.default.exists", ref.name))
      }
    }
    return null
  }

  private fun ValidationInfoBuilder.validateNewBranchNameField(): ValidationInfo? {
    if (!createNewBranch.get()){
      return null
    }
    val name = newBranchName.get()
    checkRefName(name)?.let { return it }
    if (localBranchNames.contains(name)){
      return error(GitBundle.message("working.tree.dialog.branch.validation.already.exists", name))
    }
    return null
  }

  private fun ValidationInfoBuilder.validateProjectName(): ValidationInfo? {
    if (!isValidFileName(projectName.get().trim())) {
      return error(GitBundle.message("working.tree.dialog.name.validation.invalid.format"))
    }
    return null
  }

  private fun ValidationInfoBuilder.validateLocationOnInput(): ValidationInfo? {
    return validateWorktreeParentPath(parentPath.get().trim())?.let { error(it) }
  }

  private fun ValidationInfoBuilder.validateLocationOnApply(): ValidationInfo? {
    return runWithModalProgressBlocking(data.project, GitBundle.message("working.tree.dialog.location.validation.progress")) {
      validateWorktreePath(parentPath.get().trim(), projectName.get().trim())
    }?.let { error(it) }
  }

  private fun supportPathComment() {
    updateParentPathCellComment()
    parentPath.afterChange { updateParentPathCellComment() }
    projectName.afterChange { updateParentPathCellComment() }
  }

  private fun updateParentPathCellComment() {
    val parent = parentPath.get()
    val child = projectName.get()
    val text = when {
      parent.isBlank() -> ""
      child.isBlank() -> GitBundle.message("working.tree.dialog.label.location.comment", getPresentablePath(parent))
      else -> {
        val path = getPresentablePath("${parent}/${child}")
        GitBundle.message("working.tree.dialog.label.location.comment", path)
      }
    }
    parentPathCell.comment?.text = text
  }

  private fun computeRefsWithWorkingTrees(): List<RefWithWorkingTree> {
    val branches = data.repository.branches
    val result = branches.localBranches.sortedBy { it.name }
      .map { it.toRefWithWorkingTree() }.toMutableList()
    if (result.isEmpty()) {
      // see com.intellij.vcs.git.repo.GitRepositoryState.getLocalBranchesOrCurrent
      result.addIfNotNull(data.repository.currentBranch?.toRefWithWorkingTree())
    }
    val remotes = branches.remoteBranches.sortedBy { it.name }
      .map { RefWithWorkingTree(it, null) }
    result.addAll(remotes)

    val tags = data.repository.tagsHolder.tags.sortedBy { it.name }
      .map { RefWithWorkingTree(it, null) }
    result.addAll(tags)

    return result
  }

  private fun suggestProjectName(): String {
    val branchNameToCreate = newBranchName.get()
    val existingRefName = existingRefWithWorkingTree.get()?.ref?.name
    val refToUse = if (createNewBranch.get() && branchNameToCreate.isNotEmpty()) branchNameToCreate else existingRefName

    return if (refToUse.isNullOrEmpty()) {
      ""
    }
    else {
      data.projectNameBase.name + "-" + refToUse.substringAfterLast("/")
    }
  }

  private fun createBranchNameCompletion(): GitNewBranchDialog.BranchNamesCompletion {
    val branches = data.repository.branches
    val localBranches = branches.localBranches.map { it.name }
    val remoteBranches = branches.remoteBranches.map { it.nameForRemoteOperations }
    val localDirectories = GitNewBranchDialog.collectDirectories(localBranches, true)
    val remoteDirectories = GitNewBranchDialog.collectDirectories(remoteBranches, true)
    val allSuggestions = buildSet {
      addAll(localBranches)
      addAll(remoteBranches)
      addAll(localDirectories)
      addAll(remoteDirectories)
    }
    return GitNewBranchDialog.BranchNamesCompletion(localDirectories.toList(), allSuggestions.toList())
  }

  private fun TextFieldWithCompletion.setupCleanBranchNameAndAdjustCursorIfNeeded() {
    whenDocumentChanged(disposable) {
      invokeLater {
        cleanBranchNameAndAdjustCursorIfNeeded(validator)
      }
    }
  }

  private class RefWithTreeCellRenderer(project: Project, repository: GitRepository) :
    ColoredListCellRenderer<RefWithWorkingTree?>() {

    private val repositoryModel = GitWorkingTreesService.getInstance(project).repositoryToModel(repository)

    override fun customizeCellRenderer(
      list: JList<out RefWithWorkingTree?>,
      value: RefWithWorkingTree?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean,
    ) {
      if (value == null) {
        append(GitBundle.message("working.tree.dialog.existing.branch.combo.box.empty.text"))
        return
      }

      val ref = value.ref
      val isCurrent = repositoryModel?.state?.isCurrentRef(ref) ?: false
      val isFavorite = repositoryModel?.favoriteRefs?.contains(ref) ?: false
      icon = GitBranchesTreeIconProvider.forRef(ref,
                                                current = isCurrent,
                                                favorite = isFavorite,
                                                favoriteToggleOnClick = false,
                                                selected = selected)

      append(ref.name)
      value.workingTree?.path?.name?.apply {
        append("   ")
        append(this, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
    }
  }

  fun getWorkTreeData(): GitWorkingTreeDialogData {
    val path = VcsUtil.getFilePath(Paths.get(parentPath.get()).resolve(projectName.get()), true)
    return if (createNewBranch.get()) {
      GitWorkingTreeDialogData.createForNewBranch(path, existingRefWithWorkingTree.get()!!.ref, newBranchName.get())
    }
    else {
      GitWorkingTreeDialogData.createForExistingBranch(path, existingRefWithWorkingTree.get()!!.ref)
    }
  }

  companion object {
    @VisibleForTesting
    internal fun validateWorktreeParentPath(parentPath: String): @NlsContexts.DialogMessage String? {
      if (parentPath.isBlank()) {
        return GitBundle.message("working.tree.dialog.location.validation.empty")
      }

      try {
        Paths.get(parentPath)
      } catch (_: InvalidPathException) {
        return GitBundle.message("working.tree.dialog.location.validation.invalid.path")
      }
      return null
    }

    @VisibleForTesting
    @RequiresBackgroundThread
    internal fun validateWorktreePath(parentPath: String, dirName: String): @NlsContexts.DialogMessage String? {
      validateWorktreeParentPath(parentPath)?.let { return it }
      val parent = Paths.get(parentPath)

      if (dirName.isNotBlank()) {
        val fullPath = try {
          parent.resolve(dirName)
        }
        catch (_: InvalidPathException) {
          return GitBundle.message("working.tree.dialog.location.validation.invalid.path")
        }

        // Check the full target path first — if it already exists, we can skip the parent check entirely.
        try {
          return if (fullPath.useDirectoryEntries { entries -> entries.any() }) {
            GitBundle.message("working.tree.dialog.location.validation.is.not.empty", getPresentablePath(fullPath.toString()))
          } else {
            null
          }
        }
        catch (_: NoSuchFileException) { /* target doesn't exist — fall through to parent validation */ }
        catch (_: NotDirectoryException) {
            return GitBundle.message("working.tree.dialog.location.validation.is.a.file", getPresentablePath(fullPath.toString()))
        }
      }

      return try {
        parent.useDirectoryEntries { }
        if (!parent.isWritable()) {
          GitBundle.message("working.tree.dialog.location.validation.not.writable", getPresentablePath(parentPath))
        } else {
          null
        }
      } catch (_: NoSuchFileException) {
        null
      } catch (_: NotDirectoryException) {
        GitBundle.message("working.tree.dialog.location.validation.not.a.directory", getPresentablePath(parentPath))
      }
    }
  }
}