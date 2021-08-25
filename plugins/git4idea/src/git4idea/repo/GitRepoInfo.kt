// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo

import com.intellij.dvcs.repo.Repository
import com.intellij.util.containers.CollectionFactory
import com.intellij.vcs.log.Hash
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitRemoteBranch
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import org.jetbrains.annotations.NonNls

data class GitRepoInfo(val currentBranch: GitLocalBranch?,
                       val currentRevision: String?,
                       val state: Repository.State,
                       val remotes: Collection<GitRemote>,
                       val localBranchesWithHashes: Map<GitLocalBranch, Hash>,
                       val remoteBranchesWithHashes: Map<GitRemoteBranch, Hash>,
                       val branchTrackInfos: Collection<GitBranchTrackInfo>,
                       val submodules: Collection<GitSubmoduleInfo>,
                       val hooksInfo: GitHooksInfo,
                       val isShallow: Boolean) {
  val branchTrackInfosMap: Map<String, GitBranchTrackInfo> =
    branchTrackInfos.associateByTo(CollectionFactory.createCustomHashingStrategyMap(GitReference.BRANCH_NAME_HASHING_STRATEGY)) { it.localBranch.name }

  val remoteBranches: Collection<GitRemoteBranch>
    @Deprecated("")
    get() = remoteBranchesWithHashes.keys

  @NonNls
  override fun toString(): String {
    return "GitRepoInfo{current=$currentBranch, remotes=$remotes, localBranches=$localBranchesWithHashes, " +
           "remoteBranches=$remoteBranchesWithHashes, trackInfos=$branchTrackInfos, submodules=$submodules, hooks=$hooksInfo}"
  }
}
