// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.branch

import com.intellij.dvcs.repo.VcsRepositoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.vcs.branch.BranchData
import com.intellij.vcs.branch.BranchDataImpl
import com.intellij.vcs.branch.BranchStateProvider
import org.zmlx.hg4idea.HgVcs
import org.zmlx.hg4idea.repo.HgRepository

internal class HgBranchStateProvider(val project: Project) : BranchStateProvider {
  override fun getCurrentBranch(path: FilePath): BranchData? {
    if (!ProjectLevelVcsManager.getInstance(project).checkVcsIsActive(HgVcs.VCS_NAME)) {
      return null
    }

    val repository = VcsRepositoryManager.getInstance(project).getRepositoryForFile(path, true) as? HgRepository
    return repository?.let { BranchDataImpl(it.root.presentableName, it.currentBranchName) }
  }
}