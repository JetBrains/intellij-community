// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.vcs.VcsException
import com.intellij.vcsUtil.VcsFileUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.index.ui.GitFileStatusNode
import git4idea.index.vfs.GitIndexFileSystemRefresher

abstract class StagingAreaOperationAction(private val operation: StagingAreaOperation)
  : GitFileStatusNodeAction(operation.actionText, Presentation.NULL_STRING, operation.icon) {

  override fun matches(statusNode: GitFileStatusNode): Boolean = operation.matches(statusNode)

  override fun perform(project: Project, nodes: List<GitFileStatusNode>) = performStageOperation(project, nodes, operation)
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

fun <T> runProcess(project: Project, @NlsContexts.ProgressTitle title: String, canBeCancelled: Boolean, process: () -> T): T {
  return ProgressManager.getInstance().runProcessWithProgressSynchronously<T, Exception>(ThrowableComputable { process() },
                                                                                         title, canBeCancelled, project)
}