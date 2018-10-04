// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo

import com.intellij.dvcs.repo.Repository
import com.intellij.vcs.log.Hash
import git4idea.GitLocalBranch
import git4idea.GitReference
import git4idea.GitRemoteBranch
import gnu.trove.THashMap

import java.util.*

class GitRepoInfo(val currentBranch: GitLocalBranch?,
                  val currentRevision: String?,
                  val state: Repository.State,
                  remotes: Collection<GitRemote>,
                  localBranches: Map<GitLocalBranch, Hash>,
                  remoteBranches: Map<GitRemoteBranch, Hash>,
                  branchTrackInfos: Collection<GitBranchTrackInfo>,
                  val submodules: Collection<GitSubmoduleInfo>,
                  val hooksInfo: GitHooksInfo,
                  val isShallow: Boolean) {
  private val myRemotes: Set<GitRemote>
  val localBranchesWithHashes: Map<GitLocalBranch, Hash>
  val remoteBranchesWithHashes: Map<GitRemoteBranch, Hash>
  private val myBranchTrackInfos: Set<GitBranchTrackInfo>
  private val myBranchTrackInfosMap: MutableMap<String, GitBranchTrackInfo>

  val remotes: Collection<GitRemote>
    get() = myRemotes

  val remoteBranches: Collection<GitRemoteBranch>
    @Deprecated("")
    get() = remoteBranchesWithHashes.keys

  val branchTrackInfos: Collection<GitBranchTrackInfo>
    get() = myBranchTrackInfos

  val branchTrackInfosMap: Map<String, GitBranchTrackInfo>
    get() = myBranchTrackInfosMap

  init {
    myRemotes = LinkedHashSet(remotes)
    localBranchesWithHashes = HashMap(localBranches)
    remoteBranchesWithHashes = HashMap(remoteBranches)
    myBranchTrackInfos = LinkedHashSet(branchTrackInfos)

    myBranchTrackInfosMap = THashMap(GitReference.BRANCH_NAME_HASHING_STRATEGY)
    for (info in branchTrackInfos) {
      myBranchTrackInfosMap[info.localBranch.name] = info
    }
  }

  override fun equals(o: Any?): Boolean {
    if (this === o) return true
    if (o == null || javaClass != o.javaClass) return false

    val info = o as GitRepoInfo?

    if (state !== info!!.state) return false
    if (if (currentRevision != null) currentRevision != info!!.currentRevision else info!!.currentRevision != null) return false
    if (if (currentBranch != null) currentBranch != info.currentBranch else info.currentBranch != null) return false
    if (myRemotes != info.myRemotes) return false
    if (myBranchTrackInfos != info.myBranchTrackInfos) return false
    if (localBranchesWithHashes != info.localBranchesWithHashes) return false
    if (remoteBranchesWithHashes != info.remoteBranchesWithHashes) return false
    if (submodules != info.submodules) return false
    if (hooksInfo != info.hooksInfo) return false
    return if (isShallow != info.isShallow) false else true

  }

  override fun hashCode(): Int {
    var result = currentBranch?.hashCode() ?: 0
    result = 31 * result + (currentRevision?.hashCode() ?: 0)
    result = 31 * result + state.hashCode()
    result = 31 * result + myRemotes.hashCode()
    result = 31 * result + localBranchesWithHashes.hashCode()
    result = 31 * result + remoteBranchesWithHashes.hashCode()
    result = 31 * result + myBranchTrackInfos.hashCode()
    result = 31 * result + submodules.hashCode()
    result = 31 * result + hooksInfo.hashCode()
    result = 31 * result + if (isShallow) 1 else 0
    return result
  }

  override fun toString(): String {
    return String.format("GitRepoInfo{current=%s, remotes=%s, localBranches=%s, remoteBranches=%s, trackInfos=%s, submodules=%s, hooks=%s}",
                         currentBranch, myRemotes, localBranchesWithHashes, remoteBranchesWithHashes, myBranchTrackInfos, submodules,
                         hooksInfo)
  }
}
