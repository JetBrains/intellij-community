// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitExecutableManager
import git4idea.config.GitVersion
import git4idea.config.GitVersionSpecialty

const val NUL = "\u0000"
private val LOG = Logger.getInstance("#git4idea.index.GitIndexStatusUtil")

/*
 * `git status` output format (porcelain version 1):
 *
 * XY PATH
 * XY PATH\u0000ORIG_PATH
 *
 * X          Y     Meaning
 * -------------------------------------------------
 * 	        [AMD]   not updated
 * M        [ MD]   updated in index
 * A        [ MD]   added to index
 * D                deleted from index
 * R        [ MD]   renamed in index
 * C        [ MD]   copied in index
 * [MARC]           index and work tree matches
 * [ MARC]     M    work tree changed since index
 * [ MARC]     D    deleted in work tree
 * [ D]        R    renamed in work tree
 * [ D]        C    copied in work tree
 * -------------------------------------------------
 * D           D    unmerged, both deleted
 * A           U    unmerged, added by us
 * U           D    unmerged, deleted by them
 * U           A    unmerged, added by them
 * D           U    unmerged, deleted by us
 * A           A    unmerged, both added
 * U           U    unmerged, both modified
 * -------------------------------------------------
 * ?           ?    untracked
 * !           !    ignored
 * -------------------------------------------------
 */

fun getStatus(project: Project, root: VirtualFile, files: List<FilePath> = emptyList(),
              withRenames: Boolean = true, withUntracked: Boolean = true, withIgnored: Boolean = false): List<LightFileStatus.StatusRecord> {
  val result = mutableListOf<LightFileStatus.StatusRecord>()

  val pathsChunks = if (files.isNotEmpty()) VcsFileUtil.chunkPaths(root, files) else listOf(emptyList()) // empty list to read everything
  for (pathsChunk in pathsChunks) {
    val h = GitLineHandler(project, root, GitCommand.STATUS)
    h.setSilent(true)
    h.appendParameters(GitExecutableManager.getInstance().tryGetVersion(project) ?: GitVersion.NULL,
                       withRenames = withRenames, withUntracked = withUntracked, withIgnored = withIgnored)
    h.endOptions()
    h.addParameters(pathsChunk)

    val output: String = Git.getInstance().runCommand(h).getOutputOrThrow()
    parseGitStatusOutput(output, result=result)
  }

  return result
}

@Throws(VcsException::class)
fun getFileStatus(root: VirtualFile, filePath: FilePath, executable: String): LightFileStatus {
  val h = GitLineHandler(null, VfsUtilCore.virtualToIoFile(root), executable, GitCommand.STATUS, emptyList())
  h.setSilent(true)
  h.appendParameters(GitExecutableManager.getInstance().getVersion(executable),
                     withRenames = false, withUntracked = true, withIgnored = true)
  h.endOptions()
  h.addRelativePaths(filePath)

  val output: String = Git.getInstance().runCommand(h).getOutputOrThrow()
  if (output.isNotBlank()) {
    val gitStatusOutput = parseGitStatusOutput(output)
    return gitStatusOutput.firstOrNull() ?: LightFileStatus.Blank
  }

  val repositoryPath = getFilePath(root, filePath, executable) ?: return LightFileStatus.Blank
  return LightFileStatus.NotChanged(repositoryPath)
}

private fun GitLineHandler.appendParameters(gitVersion: GitVersion,
                                            withRenames: Boolean = true, withUntracked: Boolean = true, withIgnored: Boolean = false) {
  addParameters("--porcelain", "-z")
  if (!withRenames) {
    if (GitVersionSpecialty.STATUS_SUPPORTS_NO_RENAMES.existsIn(gitVersion)) {
      addParameters("--no-renames")
    }
  }
  addParameters("--untracked-files=${if (withUntracked) "all" else "no"}")
  if (GitVersionSpecialty.STATUS_SUPPORTS_IGNORED_MODES.existsIn(gitVersion)) {
    if (withIgnored) {
      addParameters("--ignored=matching")
    }
    else {
      addParameters("--ignored=no")
    }
  }
  else if (withIgnored) {
    addParameters("--ignored")
  }
}

@Throws(VcsException::class)
fun getFilePath(root: VirtualFile, filePath: FilePath, executable: String): String? {
  val handler = GitLineHandler(null, VfsUtilCore.virtualToIoFile(root),
                               executable, GitCommand.LS_FILES, emptyList())
  handler.addParameters("--full-name")
  handler.addRelativePaths(filePath)
  handler.setSilent(true)
  return Git.getInstance().runCommand(handler).getOutputOrThrow().lines().firstOrNull()
}

@Throws(VcsException::class)
private fun parseGitStatusOutput(output: String, result: MutableList<LightFileStatus.StatusRecord> = mutableListOf()): List<LightFileStatus.StatusRecord> {
  val split = output.split(NUL).toTypedArray()
  val it = split.iterator()
  while (it.hasNext()) {
    val line = it.next()
    if (StringUtil.isEmptyOrSpaces(line)) continue
    if (line.length < 4 || line[2] != ' ') {
      LOG.error("Could not parse status line '$line'")
      continue // ignoring broken lines for now
    }

    val xStatus = line[0]
    val yStatus = line[1]
    val pathPart = line.substring(3) // skipping the space
    if (isRenamed(xStatus) || isRenamed(yStatus)) {
      if (!it.hasNext()) {
        LOG.error("Missing original path for status line '$line'")
        continue
      }
      val origPath = it.next()
      result.add(LightFileStatus.StatusRecord(xStatus, yStatus, pathPart, origPath = origPath))
    }
    else {
      result.add(LightFileStatus.StatusRecord(xStatus, yStatus, pathPart))
    }
  }

  return result
}

@Throws(VcsException::class)
internal fun getFileStatus(status: StatusCode): FileStatus? {
  return when (status) {
    ' ' -> null
    'M', 'R', 'C', 'T' -> FileStatus.MODIFIED
    'A' -> FileStatus.ADDED
    'D' -> FileStatus.DELETED
    'U' -> FileStatus.MERGED_WITH_CONFLICTS
    '!' -> FileStatus.IGNORED
    '?' -> FileStatus.UNKNOWN
    else -> throw VcsException("Unexpected symbol as status: $status")
  }
}

typealias StatusCode = Char

internal fun isIgnored(status: StatusCode) = status == '!'
internal fun isUntracked(status: StatusCode) = status == '?'
fun isRenamed(status: StatusCode) = status == 'R' || status == 'C'
internal fun isAdded(status: StatusCode) = status == 'A'
internal fun isDeleted(status: StatusCode) = status == 'D'
internal fun isConflicted(index: StatusCode, workTree: StatusCode): Boolean {
  return (index == 'D' && workTree == 'D') ||
         (index == 'A' && workTree == 'A') ||
         (index == 'U' || workTree == 'U')
}

sealed class LightFileStatus {
  internal abstract fun getFileStatus(): FileStatus

  object Blank : LightFileStatus() {
    override fun getFileStatus(): FileStatus = FileStatus.NOT_CHANGED
  }

  data class NotChanged(val path: String) : LightFileStatus() {
    override fun getFileStatus(): FileStatus = FileStatus.NOT_CHANGED
  }

  data class StatusRecord(val index: StatusCode,
                          val workTree: StatusCode,
                          val path: String,
                          val origPath: String? = null) : LightFileStatus() {
    override fun getFileStatus(): FileStatus {
      if (isConflicted()) return FileStatus.MERGED_WITH_CONFLICTS
      return getFileStatus(index) ?: getFileStatus(workTree) ?: FileStatus.NOT_CHANGED
    }

    internal fun isConflicted(): Boolean = isConflicted(index, workTree)
  }
}

fun LightFileStatus.isTracked(): Boolean {
  return when (this) {
    LightFileStatus.Blank -> false
    is LightFileStatus.NotChanged -> true
    is LightFileStatus.StatusRecord -> !isIgnored(index) && !isUntracked(index)
  }
}

val LightFileStatus.repositoryPath: String?
  get() = when (this) {
    LightFileStatus.Blank -> null
    is LightFileStatus.NotChanged -> path
    is LightFileStatus.StatusRecord -> if (!isTracked() || index == 'A' || workTree == 'A') null else origPath ?: path
  }