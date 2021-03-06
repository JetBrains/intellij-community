// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.update.FilePathChange
import com.intellij.openapi.vcs.update.RefreshVFsSynchronously
import com.intellij.openapi.vfs.VirtualFile
import git4idea.i18n.GitBundle
import git4idea.index.ContentVersion
import git4idea.index.path
import git4idea.index.ui.GitFileStatusNode
import git4idea.index.ui.NodeKind
import git4idea.util.GitFileUtils
import java.util.function.Supplier
import javax.swing.Icon

interface StagingAreaOperation {
  val actionText: Supplier<@NlsActions.ActionText String>
  val progressTitle: @NlsContexts.ProgressTitle String
  val icon: Icon?
  val errorMessage: @NlsContexts.NotificationContent String

  fun matches(statusNode: GitFileStatusNode): Boolean

  @Throws(VcsException::class)
  fun processPaths(project: Project, root: VirtualFile, nodes: List<GitFileStatusNode>)
}

object GitAddOperation : StagingAreaOperation {
  override val actionText get() = GitBundle.messagePointer("stage.add.action.text")
  override val progressTitle get() = GitBundle.message("stage.add.process")
  override val icon = AllIcons.General.Add
  override val errorMessage: String get() = GitBundle.message("stage.add.error.title")

  override fun matches(statusNode: GitFileStatusNode) = statusNode.kind == NodeKind.UNSTAGED || statusNode.kind == NodeKind.UNTRACKED

  override fun processPaths(project: Project, root: VirtualFile, nodes: List<GitFileStatusNode>) {
    GitFileUtils.addPaths(project, root, nodes.map { it.filePath }, true)
  }
}

object GitResetOperation : StagingAreaOperation {
  override val actionText get() = GitBundle.messagePointer("stage.reset.action.text")
  override val progressTitle get() = GitBundle.message("stage.reset.process")
  override val icon = AllIcons.General.Remove
  override val errorMessage: String get() = GitBundle.message("stage.reset.error.title")

  override fun matches(statusNode: GitFileStatusNode) = statusNode.kind == NodeKind.STAGED

  override fun processPaths(project: Project, root: VirtualFile, nodes: List<GitFileStatusNode>) {
    GitFileUtils.resetPaths(project, root, nodes.map { it.filePath })
  }
}

object GitRevertOperation : StagingAreaOperation {
  override val actionText get() = GitBundle.messagePointer("stage.revert.action.text")
  override val progressTitle get() = GitBundle.message("stage.revert.process")
  override val icon = AllIcons.Actions.Rollback
  override val errorMessage: String get() = GitBundle.message("stage.revert.error.title")

  override fun matches(statusNode: GitFileStatusNode) = statusNode.kind == NodeKind.UNSTAGED

  override fun processPaths(project: Project, root: VirtualFile, nodes: List<GitFileStatusNode>) {
    GitFileUtils.revertUnstagedPaths(project, root, nodes.map { it.filePath })
    RefreshVFsSynchronously.refresh(nodes.map { createChange(it) }, isRollback = true)
  }

  private fun createChange(node: GitFileStatusNode): FilePathChange =
    FilePathChange.Simple(node.status.path(ContentVersion.STAGED), node.status.path(ContentVersion.LOCAL))
}