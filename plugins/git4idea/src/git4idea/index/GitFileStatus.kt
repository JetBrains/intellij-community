// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitContentRevision

data class GitFileStatus(val index: StatusCode,
                         val workTree: StatusCode,
                         val path: FilePath,
                         val origPath: FilePath? = null) {

  constructor(root: VirtualFile, record: LightFileStatus.StatusRecord) :
    this(record.index, record.workTree, GitContentRevision.createPath(root, record.path), record.origPath?.let { GitContentRevision.createPath(root, it) })

  fun isConflicted(): Boolean = isConflicted(index, workTree)

  fun isUntracked() = isUntracked(index) || isUntracked(workTree)
  fun isIgnored() = isIgnored(index) || isIgnored(workTree)
  fun isTracked() = !isIgnored(index) && !isUntracked(index)

  fun getStagedStatus(): FileStatus? = if (isIgnored(index) || isUntracked(index) || isConflicted()) null else getFileStatus(index)
  fun getUnStagedStatus(): FileStatus? = if (isIgnored(workTree) || isUntracked(workTree) || isConflicted()) null
  else getFileStatus(workTree)
}

fun untrackedStatus(filePath: FilePath) = GitFileStatus('?', '?', filePath, null)
fun ignoredStatus(filePath: FilePath) = GitFileStatus('!', '!', filePath, null)
fun notChangedStatus(filePath: FilePath) = GitFileStatus(' ', ' ', filePath, null)

fun GitFileStatus.has(contentVersion: ContentVersion): Boolean {
  return when (contentVersion) {
    ContentVersion.HEAD -> isTracked() && !isAdded(index)
    ContentVersion.STAGED -> isTracked() && !isDeleted(index)
    ContentVersion.LOCAL -> !isDeleted(workTree)
  }
}

fun GitFileStatus.path(contentVersion: ContentVersion): FilePath {
  return when (contentVersion) {
    ContentVersion.HEAD -> origPath ?: path
    ContentVersion.STAGED -> if (isRenamed(index)) path else origPath ?: path
    ContentVersion.LOCAL -> path
  }
}

enum class ContentVersion { HEAD, STAGED, LOCAL }