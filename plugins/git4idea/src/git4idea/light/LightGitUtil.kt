// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.light

import com.intellij.diff.DiffContentFactoryImpl
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitBinaryHandler
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitExecutableManager
import git4idea.util.GitFileUtils.addTextConvParameters
import java.nio.charset.Charset

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

@Throws(VcsException::class)
fun getFileContent(directory: VirtualFile,
                   repositoryPath: String,
                   executable: String,
                   revisionOrBranch: String): ByteArray {
  val h = GitBinaryHandler(VfsUtilCore.virtualToIoFile(directory), executable, GitCommand.CAT_FILE)
  addTextConvParameters(GitExecutableManager.getInstance().getVersion(executable), h, true)
  h.addParameters("$revisionOrBranch:$repositoryPath")
  return h.run()
}

@Throws(VcsException::class)
fun getFileContentAsString(file: VirtualFile, repositoryPath: String, executable: String, revisionOrBranch: String = GitUtil.HEAD): String {
  val vcsContent = getFileContent(file.parent, repositoryPath, executable, revisionOrBranch)
  val charset: Charset = DiffContentFactoryImpl.guessCharset(null, vcsContent, VcsUtil.getFilePath(file))
  return CharsetToolkit.decodeString(vcsContent, charset)
}