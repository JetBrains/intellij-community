// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.log

import com.intellij.openapi.vcs.RemoteFilePath
import com.intellij.openapi.vcs.vfs.AbstractVcsVirtualFile
import com.intellij.openapi.vcs.vfs.VcsFileSystem
import com.intellij.openapi.vcs.vfs.VcsVirtualFile
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsCommitMetadata
import git4idea.GitFileRevision
import git4idea.GitRevisionNumber
import git4idea.index.GitIndexUtil
import git4idea.repo.GitRepository

class GitDirectoryVirtualFile(
  private val repo: GitRepository,
  parent: VirtualFile?,
  name: String,
  private val commit: VcsCommitMetadata
) : AbstractVcsVirtualFile(parent, name, VcsFileSystem.getInstance()) {
  override fun isDirectory(): Boolean = true

  override fun contentsToByteArray(): ByteArray {
    throw UnsupportedOperationException()
  }

  private val cachedChildren by lazy {
    val gitRevisionNumber = GitRevisionNumber(commit.id.asString())
    val remotePath = if (path.isEmpty()) "" else path + "/"

    val tree = GitIndexUtil.listTree(repo, listOf(RemoteFilePath(remotePath, true)), gitRevisionNumber)
    val result = tree.map {
      when(it) {
        is GitIndexUtil.StagedDirectory -> GitDirectoryVirtualFile(repo, this, it.path.name, commit)
        else -> VcsVirtualFile(this, it.path.name,
                               GitFileRevision(repo.project, RemoteFilePath(it.path.path, false), gitRevisionNumber),
                               VcsFileSystem.getInstance())

      }
    }
     result.toTypedArray<VirtualFile>()
  }

  override fun getChildren(): Array<VirtualFile> = cachedChildren
}
