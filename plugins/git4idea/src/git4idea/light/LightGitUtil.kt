// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcsUtil.VcsImplUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitBinaryHandler
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitExecutable
import git4idea.config.GitExecutableManager
import git4idea.i18n.GitBundle
import git4idea.util.GitFileUtils.addTextConvParameters

@Throws(VcsException::class)
fun getLocation(directory: VirtualFile, executable: GitExecutable): String {
  val name = Git.getInstance().runCommand(createRevParseHandler(directory, executable)).getOutputOrThrow()
  if (name != "HEAD") return name

  val hash = Git.getInstance().runCommand(createRevParseHandler(directory, executable, abbrev = false)).getOutputOrThrow()
  if (VcsLogUtil.HASH_REGEX.matcher(hash).matches()) {
    return VcsLogUtil.getShortHash(hash)
  }
  throw VcsException(GitBundle.message("git.light.cant.find.current.revision.exception.message", directory.path))
}

private fun createRevParseHandler(directory: VirtualFile, executable: GitExecutable, abbrev: Boolean = true): GitLineHandler {
  val handler = GitLineHandler(null, VfsUtilCore.virtualToIoFile(directory),
                               executable, GitCommand.REV_PARSE, emptyList())
  if (abbrev) handler.addParameters("--abbrev-ref")
  handler.addParameters("HEAD")
  handler.setSilent(true)
  return handler
}

@Throws(VcsException::class)
fun getFileContent(directory: VirtualFile,
                   repositoryPath: String,
                   executable: GitExecutable,
                   revisionOrBranch: String): ByteArray {
  val h = GitBinaryHandler(VfsUtilCore.virtualToIoFile(directory), executable, GitCommand.CAT_FILE)
  addTextConvParameters(GitExecutableManager.getInstance().getVersion(executable), h, true)
  h.addParameters("$revisionOrBranch:$repositoryPath")
  return h.run()
}

@Throws(VcsException::class)
fun getFileContentAsString(file: VirtualFile, repositoryPath: String, executable: GitExecutable, revisionOrBranch: String = GitUtil.HEAD): String {
  val vcsContent = getFileContent(file.parent, repositoryPath, executable, revisionOrBranch)
  return VcsImplUtil.loadTextFromBytes(null, vcsContent, VcsUtil.getFilePath(file))
}