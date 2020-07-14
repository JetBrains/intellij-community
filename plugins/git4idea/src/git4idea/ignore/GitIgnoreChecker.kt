// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.ignore

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.vcs.*
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtil.virtualToIoFile
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.io.systemIndependentPath
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import java.nio.file.Path

internal class GitIgnoreChecker : VcsIgnoreChecker {
  override fun getSupportedVcs(): VcsKey = GitVcs.getKey()

  override fun isIgnored(vcsRoot: VirtualFile, file: Path): IgnoredCheckResult {
    return isIgnored(vcsRoot, file.toAbsolutePath().normalize().systemIndependentPath, false)
  }

  override fun isFilePatternIgnored(vcsRoot: VirtualFile, filePattern: String) = isIgnored(vcsRoot, filePattern, true)

  private fun isIgnored(vcsRoot: VirtualFile, checkForIgnore: String, isPattern: Boolean): IgnoredCheckResult {
    // check-ignore was introduced in 1.8.2,
    // executing the command for older Gits will fail with exit code 1, which we'll treat as "not ignored" for simplicity
    val handler = GitLineHandler(null, VfsUtilCore.virtualToIoFile(vcsRoot), GitCommand.CHECK_IGNORE)
    handler.addParameters("--verbose")
    handler.endOptions()
    handler.addParameters(checkForIgnore)
    val commandResult = Git.getInstance().runCommand(handler)
    return parseOutput(vcsRoot, checkForIgnore, commandResult.output, isPattern)
  }

  private fun parseOutput(vcsRoot: VirtualFile, checkForIgnorePath: String, output: List<String>, isPattern: Boolean): IgnoredCheckResult {
    if (output.isEmpty()) {
      return NotIgnored
    }

    for (line in output) {
      //Output form: <source> <COLON> <linenum> <COLON> <pattern> <HT> <pathname>
      val lineElements = line.split("\t")
      if (lineElements.size != 2) continue
      val path = lineElements[1]

      val prefixParts = lineElements[0].split(":")
      if (prefixParts.size != 3) {
        continue
      }

      val gitIgnoreRelPath = prefixParts[0]
      val matchedPattern = prefixParts[2]
      if (matchedPattern.startsWith("!")) {
        // skip matching by negative pattern
        continue
      }

      val gitIgnoreFile = VfsUtil.findRelativeFile(vcsRoot, *gitIgnoreRelPath.split("/").toTypedArray()) ?: continue
      if (isPattern && path.equals(checkForIgnorePath, !SystemInfo.isFileSystemCaseSensitive)) {
        return Ignored(virtualToIoFile(gitIgnoreFile), matchedPattern)
      }
      else if (!isPattern) {
        return Ignored(virtualToIoFile(gitIgnoreFile), matchedPattern)
      }
    }

    return NotIgnored
  }
}