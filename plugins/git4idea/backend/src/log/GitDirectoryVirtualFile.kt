// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.vfs.AbstractVcsVirtualFile
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
  filePath: FilePath,
  private val commit: VcsCommitMetadata,
) : AbstractVcsVirtualFile(parent, filePath) {

  override fun isDirectory(): Boolean = true

  override fun contentsToByteArray(): ByteArray {
    throw UnsupportedOperationException()
  }

  private val cachedChildren by lazy {
    val gitRevisionNumber = GitRevisionNumber(commit.id.asString())
    val dirPath = if (path.isEmpty()) "." else "$path/"

    val tree = GitIndexUtil.listTreeForRawPaths(repo, listOf(dirPath), gitRevisionNumber)
    val result = tree.map {
      when (it) {
        is GitIndexUtil.StagedDirectory -> GitDirectoryVirtualFile(repo, this, it.path, commit)
        else -> VcsVirtualFile(this, it.path,
                               GitFileRevision(repo.project, repo.root, it.path, gitRevisionNumber))

      }
    }
    result.toTypedArray<VirtualFile>()
  }

  override fun getChildren(): Array<VirtualFile> = cachedChildren

  override fun getLength(): Long = 0

  override fun equals(other: Any?): Boolean {
    val otherFile = other as? GitDirectoryVirtualFile ?: return false
    return repo == otherFile.repo && path == otherFile.path && commit.id == otherFile.commit.id
  }

  override fun hashCode(): Int {
    return repo.hashCode() * 31 * 31 + path.hashCode() * 31 + commit.id.hashCode()
  }
}
