// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.workingTree

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.observable.properties.GraphProperty
import com.intellij.openapi.observable.properties.PropertyGraph
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.ValidationInfoBuilder
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.ui.JBUI
import com.intellij.vcs.git.ui.GitBranchesTreeIconProvider
import com.intellij.vcsUtil.VcsUtil
import git4idea.*
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.workingTrees.GitWorkingTreesService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.launch
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Dimension
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.JList
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

internal class GitWorkingTreeDialog(
  private val data: GitWorkingTreePreDialogData,
) : DialogWrapper(data.project, false) {
  private val uiScope: CoroutineScope = GitWorkingTreesService.getInstance(data.project).coroutineScope.childScope("GitWorkingTreeDialog")
  private val branchToWorkingTreeMap = data.repository.workingTreeHolder.getWorkingTrees()
    .filter { it.currentBranch != null }
    .associateBy { it.currentBranch!! }
  private val localBranchNames = data.repository.branches.localBranches.map { it.name }

  private lateinit var parentPathCell: Cell<TextFieldWithBrowseButton>
  private lateinit var projectNameCell: Cell<JBTextField>

  private val existingBranchWithWorkingTree: GraphProperty<BranchWithWorkingTree?>
  private val projectName: GraphProperty<String>
  private val parentPath: GraphProperty<String>
  private val createNewBranch: GraphProperty<Boolean>
  private val newBranchName: GraphProperty<String>

  private val lastPathValidationChannel = Channel<PathValidationMessage>(Channel.CONFLATED)

  init {
    val propertyGraph = PropertyGraph("Git Working Tree Dialog")
    existingBranchWithWorkingTree = propertyGraph.property(data.initialExistingBranch?.toBranchWithWorkingTree())
    createNewBranch = propertyGraph.property(false)
    newBranchName = propertyGraph.property("")
    projectName = propertyGraph.property("")
    parentPath = propertyGraph.property(data.initialParentPath?.path ?: "")
    propertyGraph.dependsOn(projectName, existingBranchWithWorkingTree, true, ::suggestProjectName)
    propertyGraph.dependsOn(projectName, createNewBranch, true, ::suggestProjectName)
    propertyGraph.dependsOn(projectName, newBranchName, true, ::suggestProjectName)

    init()
    title = GitBundle.message("working.tree.dialog.title")
    setOKButtonText(GitBundle.message("working.tree.dialog.button.ok"))

    Disposer.register(disposable) {
      uiScope.cancel()
      lastPathValidationChannel.close()
    }
  }

  private data class BranchWithWorkingTree(val branch: GitBranch, val workingTree: GitWorkingTree?)

  private fun GitBranch.toBranchWithWorkingTree(): BranchWithWorkingTree {
    return if (this is GitStandardLocalBranch) {
      BranchWithWorkingTree(this, branchToWorkingTreeMap[this])
    }
    else {
      BranchWithWorkingTree(this, null)
    }
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(GitBundle.message("working.tree.dialog.label.existing.branch")) {
        val localBranchesWithTrees: List<BranchWithWorkingTree?> = computeBranchesWithWorkingTrees()
        val comboBox = comboBox(localBranchesWithTrees, BranchWithTreeCellRenderer(data.project, data.repository))
        comboBox.bindItem(existingBranchWithWorkingTree).align(Align.FILL).validationOnApply { validateExistingBranchOnApply() }
        comboBox.component.isSwingPopup = false
      }

      row {
        checkBox(GitBundle.message("working.tree.dialog.checkbox.new.branch")).bindSelected(createNewBranch).gap(RightGap.SMALL)

        textField().bindText(newBranchName).align(Align.FILL).validationOnApply { validateNewBranchNameOnApply() }
          .enabledIf(createNewBranch)
      }
        .bottomGap(BottomGap.MEDIUM)
        .layout(RowLayout.LABEL_ALIGNED)

      row(GitBundle.message("working.tree.dialog.label.name")) {
        projectNameCell = textField().bindText(projectName).align(Align.FILL).validationOnApply { validateProjectNameOnApply() }
      }
      row(GitBundle.message("working.tree.dialog.label.location")) {
        val descriptor = FileChooserDescriptorFactory.singleDir()
          .withTitle(GitBundle.message("working.tree.dialog.label.location.file.chooser.title"))
        parentPathCell = textFieldWithBrowseButton(descriptor, data.project)
          .bindText(parentPath).align(Align.FILL).validationOnApply { validateLocationOnApply() }
          .comment("")

        supportFieldCommentsAndPathValidation()
      }
    }
  }

  private fun ValidationInfoBuilder.validateExistingBranchOnApply(): ValidationInfo? {
    val value = existingBranchWithWorkingTree.get()
    if (value == null) {
      return error(GitBundle.message("working.tree.dialog.location.validation.select.branch"))
    }
    if (value.workingTree != null && !createNewBranch.get()) {
      return error(GitBundle.message("working.tree.dialog.branch.validation.already.checked.out.in.working.tree",
                                     value.branch.name, value.workingTree.path.name))
    }
    val branch = value.branch
    if (branch is GitRemoteBranch && !createNewBranch.get()) {
      val defaultLocalBranchName = branch.nameForRemoteOperations
      // can have remote conflict if git-svn is used - suggested local name will be equal to selected remote,
      // see git4idea.remote.hosting.GitRemoteBranchesUtil.checkoutRemoteBranch
      if (GitReference.BRANCH_NAME_HASHING_STRATEGY.equals(defaultLocalBranchName, branch.name)) {
        return error(GitBundle.message("working.tree.dialog.branch.validation.provide.explicit.local.branch.name", branch.name))
      }
      if (localBranchNames.contains(defaultLocalBranchName)) {
        return error(GitBundle.message("working.tree.dialog.branch.validation.default.exists", branch.name))
      }
    }
    return null
  }

  private fun ValidationInfoBuilder.validateProjectNameOnApply(): ValidationInfo? {
    return if (projectName.get().isBlank()) {
      error(GitBundle.message("working.tree.dialog.location.validation.provide.name"))
    }
    else {
      null
    }
  }

  private fun ValidationInfoBuilder.validateNewBranchNameOnApply(): ValidationInfo? {
    val name = newBranchName.get()
    return when {
      !createNewBranch.get() -> null
      name.isBlank() -> error(GitBundle.message("working.tree.dialog.location.validation.provide.new.branch.name"))
      localBranchNames.contains(name) -> {
        error(GitBundle.message("working.tree.dialog.branch.validation.already.exists", name))
      }
      else -> null
    }
  }

  private fun ValidationInfoBuilder.validateLocationOnApply(): ValidationInfo? {
    if (parentPath.get().isBlank()) return error(GitBundle.message("working.tree.dialog.location.validation.empty"))

    val validation = lastPathValidationChannel.tryReceive().getOrNull() ?: return null
    if (parentPath.get() == validation.parentPath && projectName.get() == validation.dirName && validation.message != null) {
      return error(validation.message)
    }
    return null
  }

  private fun supportFieldCommentsAndPathValidation() {
    updateParentPathCellComment()
    parentPath.afterChange {
      updateParentPathCellComment()
      precomputePathValidation()
    }
    projectName.afterChange {
      updateParentPathCellComment()
      precomputePathValidation()
      if (hasErrors(parentPathCell.component.textField)) {
        initValidation()
      }
    }
  }

  private fun computeBranchesWithWorkingTrees(): List<BranchWithWorkingTree> {
    val branches = data.repository.branches
    val result = branches.localBranches.sortedBy { it.name }
      .map { it.toBranchWithWorkingTree() }.toMutableList()
    if (result.isEmpty()) {
      // see com.intellij.vcs.git.repo.GitRepositoryState.getLocalBranchesOrCurrent
      result.addIfNotNull(data.repository.currentBranch?.toBranchWithWorkingTree())
    }
    val remotes = branches.remoteBranches.sortedBy { it.name }
      .map { BranchWithWorkingTree(it, null) }
    result.addAll(remotes)
    return result
  }

  private fun suggestProjectName(): String {
    val branchNameToCreate = newBranchName.get()
    val existingBranchName = existingBranchWithWorkingTree.get()?.branch?.name
    val branchToUse = if (createNewBranch.get() && branchNameToCreate.isNotEmpty()) branchNameToCreate else existingBranchName

    return if (branchToUse.isNullOrEmpty()) {
      ""
    }
    else {
      data.projectNameBase.name + "-" + branchToUse.substringAfterLast("/")
    }
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

  private class BranchWithTreeCellRenderer(project: Project, repository: GitRepository) :
    ColoredListCellRenderer<BranchWithWorkingTree?>() {

    private val repositoryModel = GitWorkingTreesService.getInstance(project).repositoryToModel(repository)

    override fun customizeCellRenderer(
      list: JList<out BranchWithWorkingTree?>,
      value: BranchWithWorkingTree?,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean,
    ) {
      if (value == null) {
        append(GitBundle.message("working.tree.dialog.existing.branch.combo.box.empty.text"))
        return
      }
      val branch = value.branch
      append(branch.name)

      val isCurrent = repositoryModel?.state?.isCurrentRef(branch) ?: false
      val isFavorite = repositoryModel?.favoriteRefs?.contains(branch) ?: false

      icon = GitBranchesTreeIconProvider.forRef(branch, current = isCurrent, favorite = isFavorite,
                                                favoriteToggleOnClick = false, selected = selected)

      val workingTreeName = value.workingTree?.path?.name ?: return
      append("   ")
      append(workingTreeName, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
  }

  fun getWorkTreeData(): GitWorkingTreeDialogData {
    val path = VcsUtil.getFilePath(Paths.get(parentPath.get()).resolve(projectName.get()), true)
    return if (createNewBranch.get()) {
      GitWorkingTreeDialogData.createForNewBranch(path, existingBranchWithWorkingTree.get()!!.branch, newBranchName.get())
    }
    else {
      GitWorkingTreeDialogData.createForExistingBranch(path, existingBranchWithWorkingTree.get()!!.branch)
    }
  }

  private fun precomputePathValidation() {
    val parentPath = parentPath.get()
    val dirName = projectName.get()
    uiScope.launch(Dispatchers.IO + ModalityState.current().asContextElement()) {
      val message = getPathValidationMessage(parentPath, dirName)
      try {
        lastPathValidationChannel.send(PathValidationMessage(parentPath, dirName, message))
      }
      catch (e: ClosedSendChannelException) {
        LOG.error(e)
      }
    }
  }

  private data class PathValidationMessage(val parentPath: String, val dirName: String, val message: @NlsContexts.DialogMessage String?)

  override fun getDimensionServiceKey(): String = "Git.CreateWorkingTreeDialog"

  override fun getInitialSize(): Dimension {
    return Dimension(JBUI.DialogSizes.medium().width, -1)
  }

  companion object {
    private val LOG: Logger = logger<GitWorkingTreeDialog>()

    @VisibleForTesting
    internal fun getPathValidationMessage(parentPath: String, dirName: String): @NlsContexts.DialogMessage String? {
      if (dirName.isBlank()) return null
      if (parentPath.isBlank()) return GitBundle.message("working.tree.dialog.location.validation.empty")

      val fullPath = Paths.get(parentPath).resolve(dirName)
      return when {
        !fullPath.exists() -> null
        !fullPath.isDirectory() -> GitBundle.message("working.tree.dialog.location.validation.is.a.file",
                                                     getPresentablePath(fullPath.toString()))
        fullPath.listDirectoryEntries().isNotEmpty() -> GitBundle.message("working.tree.dialog.location.validation.is.not.empty",
                                                                          getPresentablePath(fullPath.toString()))
        else -> null
      }
    }
  }
}