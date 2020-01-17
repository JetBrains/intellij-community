// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.VcsLogUtil
import git4idea.GitLocalBranch
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler

@Throws(VcsException::class)
fun getCurrentBranchFromGitOrThrow(directory: VirtualFile, executablePath: String): GitLocalBranch? {
  val handler = GitLineHandler(null, VfsUtilCore.virtualToIoFile(directory),
                               executablePath, GitCommand.REV_PARSE, emptyList())
  handler.addParameters("--abbrev-ref", "HEAD")
  handler.setSilent(true)
  val name = Git.getInstance().runCommand(handler).getOutputOrThrow()
  return if (name != "HEAD") GitLocalBranch(name) else null
}

@Throws(VcsException::class)
fun getCurrentRevisionFromGitOrThrow(directory: VirtualFile, executablePath: String): Hash {
  val handler = GitLineHandler(null, VfsUtilCore.virtualToIoFile(directory),
                               executablePath, GitCommand.REV_PARSE, emptyList())
  handler.addParameters("HEAD")
  handler.setSilent(true)
  val output = Git.getInstance().runCommand(handler).getOutputOrThrow()
  if (VcsLogUtil.HASH_REGEX.matcher(output).matches()) {
    return HashImpl.build(output)
  }
  throw VcsException("Could not find current revision for " + directory.path)
}
