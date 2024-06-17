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

  override fun getCurrentBranch(): GitLocalBranch? {
    throw UnsupportedOperationException()
  }

  override fun getBranches(): GitBranchesCollection {
    throw UnsupportedOperationException()
  }

  override fun getRemotes(): Collection<GitRemote> {
    throw UnsupportedOperationException()
  }

  override fun getBranchTrackInfos(): Collection<GitBranchTrackInfo> {
    throw UnsupportedOperationException()
  }

  override fun getBranchTrackInfo(localBranchName: String): GitBranchTrackInfo? {
    throw UnsupportedOperationException()
  }

  override fun isRebaseInProgress(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun isOnBranch(): Boolean {
    throw UnsupportedOperationException()
  }

  override fun getRoot(): VirtualFile {
    return root
  }

  override fun getPresentableUrl(): String {
    return root.presentableUrl
  }

  override fun getProject(): Project {
    return project
  }

  override fun getState(): Repository.State {
    throw UnsupportedOperationException()
  }

  override fun getCurrentBranchName(): String? {
    throw UnsupportedOperationException()
  }

  override fun getVcs(): GitVcs {
    throw UnsupportedOperationException()
  }

  override fun getSubmodules(): Collection<GitSubmoduleInfo> {
    throw UnsupportedOperationException()
  }

  override fun getCurrentRevision(): String? {
    throw UnsupportedOperationException()
  }

  override fun isFresh(): Boolean {
    throw UnsupportedOperationException()
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
    return GitTagHolder(this)
  }

  override fun dispose() {
  }
}
