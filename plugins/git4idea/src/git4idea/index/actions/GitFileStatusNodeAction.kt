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

abstract class GitFileStatusNodeAction(dynamicText: Supplier<String>) : DumbAwareAction(dynamicText) {
  protected abstract fun matches(statusNode: GitFileStatusNode): Boolean
  @Throws(VcsException::class)
  protected abstract fun processPaths(project: Project, root: VirtualFile, paths: List<FilePath>)
  protected abstract fun progressTitle(): String
  protected abstract fun showErrorMessage(project: Project, exceptions: Collection<VcsException>)

  override fun update(e: AnActionEvent) {
    val statusInfoStream = e.getData(GIT_FILE_STATUS_NODES_STREAM)
    e.presentation.isEnabledAndVisible = e.project != null && statusInfoStream != null &&
                                         statusInfoStream.anyMatch(this::matches)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project!!
    val paths = e.getRequiredData(GIT_FILE_STATUS_NODES_STREAM).filter(this::matches).map { it.filePath }.toList()
    FileDocumentManager.getInstance().saveAllDocuments()
    val exceptions = runProcess(project, progressTitle(), true) {
      performAction(project, paths)
    }
    if (exceptions.isNotEmpty()) {
      showErrorMessage(project, exceptions)
    }
  }

  private fun performAction(project: Project, paths: List<FilePath>): Collection<VcsException> {
    val exceptions = mutableListOf<VcsException>()
    VcsUtil.groupByRoots(project, paths) { it }.forEach { (vcsRoot, paths) ->
      try {
        processPaths(project, vcsRoot.path, paths)
        VcsFileUtil.markFilesDirty(project, paths)
        GitIndexFileSystemRefresher.getInstance(project).refresh { paths.contains(it.filePath) }
      }
      catch (ex: VcsException) {
        exceptions.add(ex)
      }
    }
    return exceptions
  }
}

class GitAddAction : GitFileStatusNodeAction(GitBundle.messagePointer("add.action.name")) {
  override fun matches(statusNode: GitFileStatusNode) = statusNode.kind == NodeKind.UNSTAGED || statusNode.kind == NodeKind.UNTRACKED

  override fun processPaths(project: Project, root: VirtualFile, paths: List<FilePath>) {
    GitFileUtils.addPaths(project, root, paths, false)
  }

  override fun progressTitle() = GitBundle.message("add.adding")

  override fun showErrorMessage(project: Project, exceptions: Collection<VcsException>) {
    showErrorMessage(project, VcsBundle.message("error.adding.files.title"), exceptions)
  }
}

class GitResetAction : GitFileStatusNodeAction(GitBundle.messagePointer("stage.reset.action.text")) {
  override fun matches(statusNode: GitFileStatusNode) = statusNode.kind == NodeKind.STAGED

  override fun processPaths(project: Project, root: VirtualFile, paths: List<FilePath>) {
    GitFileUtils.resetPaths(project, root, paths)
  }

  override fun progressTitle() = GitBundle.message("stage.reset.process")

  override fun showErrorMessage(project: Project, exceptions: Collection<VcsException>) {
    showErrorMessage(project, GitBundle.message("stage.reset.error.title"), exceptions)
  }
}

class GitRevertAction : GitFileStatusNodeAction(GitBundle.messagePointer("stage.revert.action.text")) {
  override fun matches(statusNode: GitFileStatusNode) = statusNode.kind == NodeKind.UNSTAGED

  override fun processPaths(project: Project, root: VirtualFile, paths: List<FilePath>) {
    GitFileUtils.revertUnstagedPaths(project, root, paths)
    LocalFileSystem.getInstance().refreshFiles(paths.mapNotNull { it.virtualFile })
  }

  override fun progressTitle() = GitBundle.message("stage.revert.process")

  override fun showErrorMessage(project: Project, exceptions: Collection<VcsException>) {
    showErrorMessage(project, GitBundle.message("stage.revert.error.title"), exceptions)
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