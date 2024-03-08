// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.BranchChangeListener

class RecentProjectBranchUpdater(private val project: Project) : BranchChangeListener {
  override fun branchWillChange(branchName: String) {}

  override fun branchHasChanged(branchName: String) {
    updateRecentProjectBranch(project)
  }

  companion object {
    fun updateRecentProjectBranch(project: Project) {
      RecentProjectsManagerBase.getInstanceEx().updateRecentMetadata(project) {
        currentBranch = VcsRepositoryManager.getInstance(project).repositories.firstNotNullOfOrNull { it.currentBranchName }
      }
    }
  }
}