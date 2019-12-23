// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitExecutableManager
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

@Throws(VcsException::class)
fun getFileStatus(root: VirtualFile, filePath: FilePath, executable: String): FileStatus {
  val h = GitLineHandler(null, VfsUtilCore.virtualToIoFile(root), executable, GitCommand.STATUS, emptyList())
  h.setSilent(true)
  h.addParameters("--porcelain", "-z", "--no-renames")
  if (GitVersionSpecialty.STATUS_SUPPORTS_IGNORED_MODES.existsIn(GitExecutableManager.getInstance().identifyVersion(executable))) {
    h.addParameters("--ignored=matching")
  } else {
    h.addParameters("--ignored")
  }
  h.endOptions()
  h.addRelativePaths(filePath)

  val output: String = Git.getInstance().runCommand(h).getOutputOrThrow()
  if (output.isNotBlank()) {
    val gitStatusOutput = parseGitStatusOutput(output)
    return gitStatusOutput.firstOrNull()?.getStatus() ?: FileStatus.NOT_CHANGED
  }
  return FileStatus.NOT_CHANGED
}

@Throws(VcsException::class)
fun parseGitStatusOutput(output: String): List<StatusOutputLine> {
  val result = mutableListOf<StatusOutputLine>()

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

    result.add(StatusOutputLine(xStatus, yStatus, pathPart))
  }

  return result;
}

@Throws(VcsException::class)
private fun getStatus(status: Char): FileStatus? {
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

data class StatusOutputLine(val xStatus: Char, val yStatus: Char, val path: String, val origPath: String? = null) {
  fun getStatus(): FileStatus {
    if ((xStatus == 'D' && yStatus == 'D') ||
        (xStatus == 'A' && yStatus == 'A') ||
        (xStatus == 'U' || yStatus == 'U')) return FileStatus.MERGED_WITH_CONFLICTS
    return getStatus(xStatus) ?: getStatus(yStatus) ?: FileStatus.NOT_CHANGED
  }
}