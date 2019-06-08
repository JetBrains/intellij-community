// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogFileHistoryHandler
import com.intellij.vcs.log.VcsLogFileHistoryHandler.Rename
import com.intellij.vcsUtil.VcsUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler

class GitLogHistoryHandler(private val project: Project) : VcsLogFileHistoryHandler {
  private val LOG = Logger.getInstance(GitLogHistoryHandler::class.java)

  @Throws(VcsException::class)
  override fun getRename(root: VirtualFile, filePath: FilePath, beforeHash: Hash, afterHash: Hash): Rename? {
    val h = GitLineHandler(project, root, GitCommand.DIFF)
    h.setWithMediator(false)
    h.setStdoutSuppressed(true)
    h.addParameters("-M", "--diff-filter=R", "--name-status", "--encoding=UTF-8", "--follow",
                    beforeHash.asString() + ".." + afterHash.asString())
    h.endOptions()
    h.addRelativePaths(filePath)

    val recordBuilder = DefaultGitLogFullRecordBuilder()
    val parser = GitLogParser.PathsParser(GitLogParser.NameStatus.STATUS, recordBuilder)
    h.addLineListener { line, outputType -> if (outputType == ProcessOutputTypes.STDOUT) parser.parseLine(line) }
    Git.getInstance().runCommandWithoutCollectingOutput(h).throwOnError()

    val statuses = recordBuilder.statuses
    if (statuses.size > 1) {
      LOG.error("Unexpected multiple renames found. Command [${h.printableCommandLine()}].\n" +
                "Output [$statuses].")
    }

    val info = statuses.singleOrNull()
    if (info?.secondPath != null) {
      val firstPath = VcsUtil.getFilePath(root.path + "/" + info.firstPath, false)
      val secondPath = VcsUtil.getFilePath(root.path + "/" + info.secondPath, false)
      return Rename(firstPath, secondPath, beforeHash, afterHash)
    }

    return null
  }
}