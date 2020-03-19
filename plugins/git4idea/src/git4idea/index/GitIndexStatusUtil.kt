// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.PathUtil
import com.intellij.util.ui.UIUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitExecutableManager
import git4idea.config.GitVersionSpecialty
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import java.awt.Color

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

@Throws(VcsException::class)
fun getFileStatus(root: VirtualFile, filePath: FilePath, executable: String): GitFileStatus {
  val h = GitLineHandler(null, VfsUtilCore.virtualToIoFile(root), executable, GitCommand.STATUS, emptyList())
  h.setSilent(true)
  h.addParameters("--porcelain", "-z")

  val gitVersion = GitExecutableManager.getInstance().getVersion(executable)
  if (GitVersionSpecialty.STATUS_SUPPORTS_NO_RENAMES.existsIn(gitVersion)) {
    h.addParameters("--no-renames")
  }
  if (GitVersionSpecialty.STATUS_SUPPORTS_IGNORED_MODES.existsIn(gitVersion)) {
    h.addParameters("--ignored=matching")
  }
  else {
    h.addParameters("--ignored")
  }
  h.endOptions()
  h.addRelativePaths(filePath)

  val output: String = Git.getInstance().runCommand(h).getOutputOrThrow()
  if (output.isNotBlank()) {
    val gitStatusOutput = parseGitStatusOutput(output)
    return gitStatusOutput.firstOrNull() ?: GitFileStatus.Blank
  }
  
  val repositoryPath = getFilePath(root, filePath, executable) ?: return GitFileStatus.Blank
  return GitFileStatus.NotChanged(repositoryPath)
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
fun parseGitStatusOutput(output: String): List<GitFileStatus.StatusRecord> {
  val result = mutableListOf<GitFileStatus.StatusRecord>()

  val split = output.split(NUL).toTypedArray()
  for (line in split) {
    if (StringUtil.isEmptyOrSpaces(line)) continue
    if (line.length < 4 || line[2] != ' ') {
      LOG.error("Could not parse status line '$line'")
      continue // ignoring broken lines for now
    }

    val xStatus = line[0]
    val yStatus = line[1]
    val pathPart = line.substring(3) // skipping the space

    result.add(GitFileStatus.StatusRecord(xStatus, yStatus, pathPart))
  }

  return result;
}

@Throws(VcsException::class)
private fun getFileStatus(status: StatusCode): FileStatus? {
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

sealed class GitFileStatus {
  internal abstract fun getFileStatus(): FileStatus

  object Blank : GitFileStatus() {
    override fun getFileStatus(): FileStatus = FileStatus.NOT_CHANGED
  }

  data class NotChanged(val path: String) : GitFileStatus() {
    override fun getFileStatus(): FileStatus = FileStatus.NOT_CHANGED
  }

  data class StatusRecord(val index: StatusCode,
                          val workTree: StatusCode,
                          val path: String,
                          val origPath: String? = null) : GitFileStatus() {
    override fun getFileStatus(): FileStatus {
      if (isConflicted()) return FileStatus.MERGED_WITH_CONFLICTS
      return getFileStatus(index) ?: getFileStatus(workTree) ?: FileStatus.NOT_CHANGED
    }

    internal fun isConflicted(): Boolean {
      return (index == 'D' && workTree == 'D') ||
             (index == 'A' && workTree == 'A') ||
             (index == 'U' || workTree == 'U')
    }
  }
}

fun GitFileStatus.isTracked(): Boolean {
  return when (this) {
    GitFileStatus.Blank -> false
    is GitFileStatus.NotChanged -> true
    is GitFileStatus.StatusRecord -> !setOf('?', '!').contains(index)
  }
}

val GitFileStatus.repositoryPath: String?
  get() = when (this) {
    GitFileStatus.Blank -> null
    is GitFileStatus.NotChanged -> path
    is GitFileStatus.StatusRecord -> if (!isTracked() || index == 'A' || workTree == 'A') null else origPath ?: path
  }

val GitFileStatus.color: Color?
  get() = getFileStatus().color

@Nls
fun GitFileStatus.getPresentation(): String {
  return when (this) {
    GitFileStatus.Blank, is GitFileStatus.NotChanged -> ""
    is GitFileStatus.StatusRecord -> getPresentation()
  }
}

@Nls
private fun GitFileStatus.StatusRecord.getPresentation(): String {
  val fileName = PathUtil.getFileName(path)
  if (index == '!' || workTree == '!' || index == '?' || workTree == '?') return "$fileName: ${getPresentation(index)}"
  if (isConflicted()) {
    val status = if (index == workTree) {
      GitBundle.message("git.status.unmerged.both", getFileStatus(if (index == 'U') 'M' else index)!!.text.toLowerCase())
    }
    else {
      val indexPresentation = if (index == 'U') ""
      else GitBundle.message("git.status.unmerged.index", getPresentation(index).toLowerCase())
      val workTreePresentation = if (workTree == 'U') ""
      else GitBundle.message("git.status.unmerged.work.tree", getPresentation(workTree).toLowerCase())
      when {
        indexPresentation.isBlank() -> workTreePresentation
        workTreePresentation.isBlank() -> indexPresentation
        else -> "$indexPresentation, $workTreePresentation"
      }
    }
    return "$fileName: ${getPresentation('U')} ($status)"
  }
  val indexPresentation = if (index == ' ') "" else GitBundle.message("git.status.index", getPresentation(index))
  val workTreePresentation = if (workTree == ' ') "" else GitBundle.message("git.status.work.tree", getPresentation(workTree))
  if (indexPresentation.isBlank()) return "$fileName: $workTreePresentation"
  if (workTreePresentation.isBlank()) return "$fileName: $indexPresentation"
  return "$fileName:${UIUtil.BR}$indexPresentation${UIUtil.BR}$workTreePresentation"
}

@Nls
private fun getPresentation(status: StatusCode): String {
  return when (status) {
    ' ' -> GitBundle.message("git.status.not.changed")
    'R' -> GitBundle.message("git.status.renamed")
    'C' -> GitBundle.message("git.status.copied")
    'T' -> GitBundle.message("git.status.type.changed")
    'U' -> GitBundle.message("git.status.unmerged")
    '?' -> GitBundle.message("git.status.untracked")
    else -> getFileStatus(status)!!.text
  }
}
