// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.ui.actions.VcsLogRefreshActionListener
import git4idea.repo.GitRepositoryManager

internal class GitLogRefreshActionListener : VcsLogRefreshActionListener {
  override fun beforeRefresh(project: Project, roots: Collection<VirtualFile>) {
    val repositoryManager = GitRepositoryManager.getInstance(project)
    for (root in roots) {
      repositoryManager.getRepositoryForRootQuick(root)?.update()
    }
  }
}
