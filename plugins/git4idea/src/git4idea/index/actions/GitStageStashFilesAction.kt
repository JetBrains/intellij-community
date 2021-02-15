// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.index.actions

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitStashUsageCollector
import git4idea.commands.Git
import git4idea.config.GitExecutableManager
import git4idea.config.GitVersionSpecialty
import git4idea.i18n.GitBundle
import git4idea.index.ui.GitFileStatusNode
import git4idea.index.ui.NodeKind
import git4idea.stash.createStashPushHandler
import git4idea.stash.refreshStash
import java.util.function.Supplier
import javax.swing.Icon

object GitStashOperation : StagingAreaOperation {
  override val actionText: Supplier<String> = GitBundle.messagePointer("stash.files.action.text")
  override val progressTitle: String = GitBundle.message("stash.files.progress.title")
  override val icon: Icon? = null
  override val errorMessage: String = GitBundle.message("stash.files.error.message")

  override fun matches(statusNode: GitFileStatusNode): Boolean {
    return statusNode.kind == NodeKind.UNSTAGED || statusNode.kind == NodeKind.UNTRACKED || statusNode.kind == NodeKind.STAGED
  }

  override fun processPaths(project: Project, root: VirtualFile, nodes: List<GitFileStatusNode>) {
    val activity = GitStashUsageCollector.logStashPush(project)
    try {
      val handler = createStashPushHandler(project, root, nodes.map { it.filePath }, "-u")
      Git.getInstance().runCommand(handler).throwOnError()
    }
    finally {
      activity.finished()
    }

    StagingAreaOperation.refreshVirtualFiles(nodes, true)
    refreshStash(project, root)
  }
}

class GitStageStashFilesAction : StagingAreaOperationAction(GitStashOperation) {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project != null &&
        !GitVersionSpecialty.STASH_PUSH_PATHSPEC_SUPPORTED.existsIn(GitExecutableManager.getInstance().getVersion(project))) {
      e.presentation.isEnabledAndVisible = false
    }
    else {
      super.update(e)
    }
  }
}