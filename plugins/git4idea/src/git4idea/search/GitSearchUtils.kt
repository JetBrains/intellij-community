// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.search

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.vcs.log.VcsRef
import git4idea.branch.GitBranchUtil
import git4idea.log.GitRefManager
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object GitSearchUtils {
  @NlsSafe
  fun getTrackingRemoteBranchName(vcsRef: VcsRef, project: Project): String? {
    if (vcsRef.type != GitRefManager.LOCAL_BRANCH) {
      return null
    }
    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRootQuick(vcsRef.root) ?: return null
    return GitBranchUtil.getTrackInfo(repository, vcsRef.name)?.remoteBranch?.name
  }
}