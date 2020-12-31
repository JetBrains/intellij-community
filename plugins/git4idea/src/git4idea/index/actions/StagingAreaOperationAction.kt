// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsContexts.NotificationContent
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.ui.VcsBalloonProblemNotifier
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.GitContentRevision
import git4idea.index.ui.GitFileStatusNode
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.util.*

class GitAddAction : StagingAreaOperationAction(GitAddOperation)
class GitResetAction : StagingAreaOperationAction(GitResetOperation)
class GitRevertAction : StagingAreaOperationAction(GitRevertOperation)

abstract class StagingAreaOperationAction(private val operation: StagingAreaOperation)
  : GitFileStatusNodeAction(operation.actionText, Presentation.NULL_STRING, operation.icon) {

  override fun matches(statusNode: GitFileStatusNode): Boolean = operation.matches(statusNode)

  override fun perform(project: Project, nodes: List<GitFileStatusNode>) = performStageOperation(project, nodes, operation)
}

fun performStageOperation(project: Project, nodes: List<GitFileStatusNode>, operation: StagingAreaOperation) {
  FileDocumentManager.getInstance().saveAllDocuments()

  runProcess(project, operation.progressTitle, true) {
    val repositoryManager = GitRepositoryManager.getInstance(project)

    val submodulesByRoot = mutableMapOf<GitRepository, MutableList<GitFileStatusNode>>()
    val pathsByRoot = mutableMapOf<GitRepository, MutableList<GitFileStatusNode>>()
    for (node in nodes) {
      val filePath = node.filePath
      val submodule = GitContentRevision.getRepositoryIfSubmodule(project, filePath)
      if (submodule != null) {
        val list = submodulesByRoot.computeIfAbsent(submodule.parent) { ArrayList() }
        list.add(node)
      }
      else {
        val repo = repositoryManager.getRepositoryForFileQuick(filePath)
        if (repo != null) {
          val list = pathsByRoot.computeIfAbsent(repo) { ArrayList() }
          list.add(node)
        }
      }
    }

    val exceptions = mutableListOf<VcsException>()
    pathsByRoot.forEach { (repo, nodes) ->
      try {
        operation.processPaths(project, repo.root, nodes)
        VcsFileUtil.markFilesDirty(project, nodes.map { it.filePath })
      }
      catch (ex: VcsException) {
        exceptions.add(ex)
      }
    }

    submodulesByRoot.forEach { (repo, submodules) ->
      try {
        operation.processPaths(project, repo.root, submodules)
        VcsFileUtil.markFilesDirty(project, submodules.mapNotNull { it.filePath.parentPath })
      }
      catch (ex: VcsException) {
        exceptions.add(ex)
      }
    }

    if (exceptions.isNotEmpty()) {
      showErrorMessage(project, operation.errorMessage, exceptions)
    }
  }
}

fun <T> runProcess(project: Project, @NlsContexts.ProgressTitle title: String, canBeCancelled: Boolean, process: () -> T): T {
  return ProgressManager.getInstance().runProcessWithProgressSynchronously(ThrowableComputable { process() },
                                                                           title, canBeCancelled, project)
}

private fun showErrorMessage(project: Project, @NotificationContent messageTitle: String, exceptions: Collection<Exception>) {
  val message = HtmlBuilder().append(HtmlChunk.text("$messageTitle:").bold())
    .br()
    .appendWithSeparators(HtmlChunk.br(), exceptions.map { HtmlChunk.text(it.localizedMessage) })

  VcsBalloonProblemNotifier.showOverVersionControlView(project, message.toString(), MessageType.ERROR)
}