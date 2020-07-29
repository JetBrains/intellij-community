// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import com.intellij.xml.util.XmlStringUtil
import git4idea.i18n.GitBundle
import git4idea.index.ui.GIT_FILE_STATUS_NODES_STREAM
import git4idea.index.ui.GitFileStatusNode
import git4idea.index.ui.NodeKind
import git4idea.index.vfs.GitIndexFileSystemRefresher
import git4idea.util.GitFileUtils
import java.util.function.Supplier
import kotlin.streams.toList

class GitAddAction : GitFileStatusNodeAction(GitAddOperation)
class GitResetAction : GitFileStatusNodeAction(GitResetOperation)
class GitRevertAction : GitFileStatusNodeAction(GitRevertOperation)

abstract class GitFileStatusNodeAction(private val operation: StagingAreaOperation)
  : DumbAwareAction(operation.actionText) {

  override fun update(e: AnActionEvent) {
    val statusInfoStream = e.getData(GIT_FILE_STATUS_NODES_STREAM)
    e.presentation.isEnabledAndVisible = e.project != null && statusInfoStream != null &&
                                         statusInfoStream.anyMatch(operation::matches)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val nodes = e.getRequiredData(GIT_FILE_STATUS_NODES_STREAM).filter(operation::matches).toList()

    performStageOperation(project, nodes, operation)
  }
}

object GitAddOperation : StagingAreaOperation {
  override val actionText get() = GitBundle.messagePointer("add.action.name")
  override val progressTitle get() = GitBundle.message("add.adding")

  override fun matches(statusNode: GitFileStatusNode) = statusNode.kind == NodeKind.UNSTAGED || statusNode.kind == NodeKind.UNTRACKED

  override fun processPaths(project: Project, root: VirtualFile, paths: List<FilePath>) {
    GitFileUtils.addPaths(project, root, paths, false)
  }

  override fun showErrorMessage(project: Project, exceptions: Collection<VcsException>) {
    showErrorMessage(project, VcsBundle.message("error.adding.files.title"), exceptions)
  }
}

object GitResetOperation : StagingAreaOperation {
  override val actionText get() = GitBundle.messagePointer("stage.reset.action.text")
  override val progressTitle get() = GitBundle.message("stage.reset.process")

  override fun matches(statusNode: GitFileStatusNode) = statusNode.kind == NodeKind.STAGED

  override fun processPaths(project: Project, root: VirtualFile, paths: List<FilePath>) {
    GitFileUtils.resetPaths(project, root, paths)
  }

  override fun showErrorMessage(project: Project, exceptions: Collection<VcsException>) {
    showErrorMessage(project, GitBundle.message("stage.reset.error.title"), exceptions)
  }
}

object GitRevertOperation : StagingAreaOperation {
  override val actionText get() = GitBundle.messagePointer("stage.revert.action.text")
  override val progressTitle get() = GitBundle.message("stage.revert.process")

  override fun matches(statusNode: GitFileStatusNode) = statusNode.kind == NodeKind.UNSTAGED

  override fun processPaths(project: Project, root: VirtualFile, paths: List<FilePath>) {
    GitFileUtils.revertUnstagedPaths(project, root, paths)
    LocalFileSystem.getInstance().refreshFiles(paths.mapNotNull { it.virtualFile })
  }

  override fun showErrorMessage(project: Project, exceptions: Collection<VcsException>) {
    showErrorMessage(project, GitBundle.message("stage.revert.error.title"), exceptions)
  }
}

fun performStageOperation(project: Project, nodes: List<GitFileStatusNode>, operation: StagingAreaOperation) {
  FileDocumentManager.getInstance().saveAllDocuments()

  runProcess(project, operation.progressTitle, true) {
    val paths = nodes.map { it.filePath }

    val exceptions = mutableListOf<VcsException>()
    VcsUtil.groupByRoots(project, paths) { it }.forEach { (vcsRoot, paths) ->
      try {
        operation.processPaths(project, vcsRoot.path, paths)
        VcsFileUtil.markFilesDirty(project, paths)
        GitIndexFileSystemRefresher.getInstance(project).refresh { paths.contains(it.filePath) }
      }
      catch (ex: VcsException) {
        exceptions.add(ex)
      }
    }

    if (exceptions.isNotEmpty()) {
      operation.showErrorMessage(project, exceptions)
    }
  }
}

fun <T> runProcess(project: Project, title: @NlsContexts.ProgressTitle String, canBeCancelled: Boolean, process: () -> T): T {
  return ProgressManager.getInstance().runProcessWithProgressSynchronously<T, Exception>(ThrowableComputable { process() },
                                                                                         title, canBeCancelled, project)
}

private fun showErrorMessage(project: Project, messageTitle: String, exceptions: Collection<Exception>) {
  VcsBalloonProblemNotifier.showOverVersionControlView(project, XmlStringUtil.wrapInHtmlTag("$messageTitle:", "b")
                                                                + "\n" + exceptions.joinToString("\n") { it.localizedMessage },
                                                       MessageType.ERROR)
}

interface StagingAreaOperation {
  val actionText: Supplier<String>
  val progressTitle: String
  fun matches(statusNode: GitFileStatusNode): Boolean

  @Throws(VcsException::class)
  fun processPaths(project: Project, root: VirtualFile, paths: List<FilePath>)
  fun showErrorMessage(project: Project, exceptions: Collection<VcsException>)
}