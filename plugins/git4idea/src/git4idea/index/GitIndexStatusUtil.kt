// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitExecutable
import git4idea.config.GitExecutableManager
import git4idea.config.GitVersion
import git4idea.config.GitVersionSpecialty
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls

const val NUL = "\u0000"

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

@Throws(VcsException::class)
fun getStatus(project: Project,
              root: VirtualFile,
              files: List<FilePath> = emptyList(),
              withRenames: Boolean,
              withUntracked: Boolean,
              withIgnored: Boolean): List<GitFileStatus> {
  return getFileStatus(project, root, files, withRenames, withUntracked, withIgnored)
    .map { GitFileStatus(root, it) }
}

@Throws(VcsException::class)
fun getFileStatus(project: Project,
                  root: VirtualFile,
                  files: List<FilePath>,
                  withRenames: Boolean,
                  withUntracked: Boolean,
                  withIgnored: Boolean): List<LightFileStatus.StatusRecord> {
  val h = GitUtil.createHandlerWithPaths(files) {
    val h = GitLineHandler(project, root, GitCommand.STATUS)
    h.setSilent(true)
    h.appendParameters(GitExecutableManager.getInstance().tryGetVersion(project) ?: GitVersion.NULL,
                       withRenames = withRenames, withUntracked = withUntracked, withIgnored = withIgnored)
    h
  }

  val output: String = Git.getInstance().runCommand(h).getOutputOrThrow()
  return parseGitStatusOutput(output)
}

@Throws(VcsException::class)
fun getFileStatus(root: VirtualFile, filePath: FilePath, executable: GitExecutable): LightFileStatus {
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
fun getFilePath(root: VirtualFile, filePath: FilePath, executable: GitExecutable): String? {
  val handler = GitLineHandler(null, VfsUtilCore.virtualToIoFile(root),
                               executable, GitCommand.LS_FILES, emptyList())
  handler.addParameters("--full-name")
  handler.addRelativePaths(filePath)
  handler.setSilent(true)
  return Git.getInstance().runCommand(handler).getOutputOrThrow().lines().firstOrNull()
}

@Throws(VcsException::class)
private fun parseGitStatusOutput(output: String): List<LightFileStatus.StatusRecord> {
  val result = mutableListOf<LightFileStatus.StatusRecord>()

  val split = output.split(NUL).toTypedArray()
  val it = split.iterator()
  while (it.hasNext()) {
    val line = it.next()
    if (StringUtil.isEmptyOrSpaces(line)) continue // skip empty lines if any (e.g. the whole output may be empty on a clean working tree).
    if (line.startsWith("starting fsmonitor-daemon in ")) continue // skip debug output from experimental daemon in git-for-windows-2.33
    // format: XY_filename where _ stands for space.
    if (line.length < 4 || line[2] != ' ') { // X, Y, space and at least one symbol for the file
      throwGFE(GitBundle.message("status.exception.message.line.is.too.short"), output, line, '0', '0')
    }

    val xStatus = line[0]
    val yStatus = line[1]
    if (!isKnownStatus(xStatus) || !isKnownStatus(yStatus)) {
      throwGFE(GitBundle.message("status.exception.message.unexpected"), output, line, xStatus, yStatus)
    }

    val pathPart = line.substring(3) // skipping the space
    if (isRenamed(xStatus) || isRenamed(yStatus)) {
      if (!it.hasNext()) {
        throwGFE(GitBundle.message("status.exception.message.missing.path"), output, line, xStatus, yStatus)
        continue
      }
      val origPath = it.next() // read the "from" filepath which is separated also by NUL character.
      result.add(LightFileStatus.StatusRecord(xStatus, yStatus, pathPart, origPath = origPath))
    }
    else {
      result.add(LightFileStatus.StatusRecord(xStatus, yStatus, pathPart))
    }
  }

  return result
}

private fun throwGFE(@Nls message: String, @NlsSafe output: String, @NlsSafe line: String, @NlsSafe xStatus: Char, @NlsSafe yStatus: Char) {
  throw VcsException(GitBundle.message("status.exception.message.format.message.xstatus.ystatus.line.output",
                                       message, xStatus, yStatus, line, output))
}

private fun isKnownStatus(status: Char): Boolean {
  return status == ' ' || status == 'M' || status == 'A' || status == 'D' || status == 'C' || status == 'R' || status == 'U' || status == 'T' || status == '!' || status == '?'
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
    else -> throw VcsException(GitBundle.message("status.exception.message.unexpected.status", status))
  }
}

typealias StatusCode = Char

internal fun isIgnored(status: StatusCode) = status == '!'
internal fun isUntracked(status: StatusCode) = status == '?'
fun isRenamed(status: StatusCode) = status == 'R' || status == 'C'
internal fun isAdded(status: StatusCode) = status == 'A'
internal fun isIntendedToBeAdded(index: StatusCode, workTree: StatusCode) = index == ' ' && workTree == 'A'
internal fun isDeleted(status: StatusCode) = status == 'D'
internal fun isConflicted(index: StatusCode, workTree: StatusCode): Boolean {
  return (index == 'D' && workTree == 'D') ||
         (index == 'A' && workTree == 'A') ||
         (index == 'T' && workTree == 'T') ||
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