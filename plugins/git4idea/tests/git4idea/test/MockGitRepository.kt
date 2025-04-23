// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.test

import com.intellij.dvcs.repo.Repository
import com.intellij.ide.vfs.rpcId
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.rpc.RepositoryId
import git4idea.GitLocalBranch
import git4idea.GitVcs
import git4idea.branch.GitBranchesCollection
import git4idea.ignore.GitRepositoryIgnoredFilesHolder
import git4idea.merge.GitResolvedMergeConflictsFilesHolder
import git4idea.repo.*
import git4idea.status.GitStagingAreaHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope

class MockGitRepository(private val project: Project, private val root: VirtualFile) : GitRepository {
  var currentBranch: GitLocalBranch? = null
    @JvmName("currentBranch_") get
  var state: Repository.State = Repository.State.NORMAL
    @JvmName("state_") get
  var remotes: Collection<GitRemote> = emptyList()
    @JvmName("remotes_") get
  var tagHolder: GitTagHolder? = null
    @JvmName("tagHolder_") get

  override fun getGitDir(): VirtualFile {
    throw UnsupportedOperationException()
  }

  override fun getRepositoryFiles(): GitRepositoryFiles {
    throw UnsupportedOperationException()
  }

  override fun getStagingAreaHolder(): GitStagingAreaHolder {
    throw UnsupportedOperationException()
  }

  override fun getUntrackedFilesHolder(): GitUntrackedFilesHolder {
    throw UnsupportedOperationException()
  }

  override fun getResolvedConflictsFilesHolder(): GitResolvedMergeConflictsFilesHolder {
    throw UnsupportedOperationException()
  }

  override fun getInfo(): GitRepoInfo {
    throw UnsupportedOperationException()
  }

  override fun getCurrentBranch(): GitLocalBranch? = currentBranch

  override fun getBranches(): GitBranchesCollection {
    return GitBranchesCollection(emptyMap(), emptyMap(), emptyList())
  }

  override fun getRemotes(): Collection<GitRemote> = remotes

  override fun getBranchTrackInfos(): Collection<GitBranchTrackInfo> {
    throw UnsupportedOperationException()
  }

  override fun getBranchTrackInfo(localBranchName: String): GitBranchTrackInfo? {
    throw UnsupportedOperationException()
  }

  override fun isRebaseInProgress(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun isOnBranch(): Boolean = currentBranch != null

  override fun getRoot(): VirtualFile {
    return root
  }

  override fun getPresentableUrl(): String {
    return root.presentableUrl
  }

  override fun getProject(): Project {
    return project
  }

  override fun getState(): Repository.State = state

  override fun getCurrentBranchName(): String? = currentBranch?.name

  override fun getVcs(): GitVcs {
    throw UnsupportedOperationException()
  }

  override fun getSubmodules(): Collection<GitSubmoduleInfo> {
    throw UnsupportedOperationException()
  }

  override fun getCurrentRevision(): String? {
    return "0".repeat(40)
  }

  override fun isFresh(): Boolean {
    return false
  }

  override fun update() {
    throw UnsupportedOperationException()
  }

  override fun toLogString(): String {
    throw UnsupportedOperationException()
  }

  override fun getIgnoredFilesHolder(): GitRepositoryIgnoredFilesHolder {
    throw UnsupportedOperationException()
  }

  override fun getCoroutineScope(): CoroutineScope {
    return GlobalScope
  }

  override fun getTagHolder(): GitTagHolder {
    return tagHolder ?: GitTagHolder(this)
  }

  override fun getRpcId(): RepositoryId {
    return RepositoryId(projectId = project.projectId(), rootPath = root.rpcId())
  }

  override fun dispose() {
  }
}
