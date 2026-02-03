// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.containers.MultiMap
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.GitContentRevision
import git4idea.GitDisposable
import git4idea.index.ui.GitFileStatusNode
import git4idea.index.ui.stagingAreaActionInvoked
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import kotlinx.coroutines.launch

class GitAddAction : StagingAreaOperationAction(GitAddOperation)
class GitAddWithoutContent : StagingAreaOperationAction(GitAddWithoutContentOperation)
class GitResetAction : StagingAreaOperationAction(GitResetOperation)
class GitRevertAction : StagingAreaOperationAction(GitRevertOperation)

abstract class StagingAreaOperationAction(private val operation: StagingAreaOperation)
  : GitFileStatusNodeAction(operation.actionText, Presentation.NULL_STRING, operation.icon) {

  override fun matches(statusNode: GitFileStatusNode): Boolean = operation.matches(statusNode)

  override fun perform(project: Project, nodes: List<GitFileStatusNode>) = performStageOperation(project, nodes, operation)
}

fun performStageOperation(project: Project, nodes: List<GitFileStatusNode>, operation: StagingAreaOperation) {
  FileDocumentManager.getInstance().saveAllDocuments()

  GitDisposable.getInstance(project).coroutineScope.launch {
    withBackgroundProgress(project, operation.progressTitle) {
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

      val successfulRoots = linkedSetOf<VirtualFile>()
      val exceptions = MultiMap<VirtualFile, VcsException>()
      pathsByRoot.forEach { (repo, nodes) ->
        try {
          operation.processPaths(project, repo.root, nodes)
          successfulRoots.add(repo.root)
          VcsFileUtil.markFilesDirty(project, nodes.map { it.filePath })
        }
        catch (ex: VcsException) {
          exceptions.putValue(repo.root, ex)
        }
      }

      submodulesByRoot.forEach { (repo, submodules) ->
        try {
          operation.processPaths(project, repo.root, submodules)
          successfulRoots.add(repo.root)
          VcsFileUtil.markFilesDirty(project, submodules.mapNotNull { it.filePath.parentPath })
        }
        catch (ex: VcsException) {
          exceptions.putValue(repo.root, ex)
        }
      }

      operation.reportResult(project, nodes, successfulRoots, exceptions)
      stagingAreaActionInvoked()
    }
  }
}
