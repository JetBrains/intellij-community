// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler

@Throws(VcsException::class)
fun getLocation(directory: VirtualFile, executable: String): String {
  val name = Git.getInstance().runCommand(createRevParseHandler(directory, executable)).getOutputOrThrow()
  if (name != "HEAD") return name

  val hash = Git.getInstance().runCommand(createRevParseHandler(directory, executable, abbrev = false)).getOutputOrThrow()
  if (VcsLogUtil.HASH_REGEX.matcher(hash).matches()) {
    return VcsLogUtil.getShortHash(hash)
  }
  throw VcsException("Could not find current revision for " + directory.path)
}

private fun createRevParseHandler(directory: VirtualFile, executable: String, abbrev: Boolean = true): GitLineHandler {
  val handler = GitLineHandler(null, VfsUtilCore.virtualToIoFile(directory),
                               executable, GitCommand.REV_PARSE, emptyList())
  if (abbrev) handler.addParameters("--abbrev-ref")
  handler.addParameters("HEAD")
  handler.setSilent(true)
  return handler
}