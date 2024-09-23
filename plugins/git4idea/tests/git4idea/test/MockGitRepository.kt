/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.test

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitLocalBranch
import git4idea.GitVcs
import git4idea.branch.GitBranchesCollection
import git4idea.ignore.GitRepositoryIgnoredFilesHolder
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

  override fun dispose() {
  }
}
