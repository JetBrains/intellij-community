// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.history.VcsFileRevisionEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogFileHistoryHandler
import com.intellij.vcs.log.VcsLogFileHistoryHandler.Rename
import com.intellij.vcs.log.impl.VcsFileStatusInfo
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitRevisionNumber
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.commands.GitLineHandlerListener

class GitLogHistoryHandler(private val project: Project) : VcsLogFileHistoryHandler {

  @Throws(VcsException::class)
  override fun getHistoryFast(root: VirtualFile, filePath: FilePath, hash: Hash?, commitCount: Int): List<VcsFileRevisionEx> {
    val parser = GitFileHistory.createLogParser(project)
    val handler = GitLineHandler(project, root, GitCommand.LOG)
    handler.setStdoutSuppressed(true)
    handler.addParameters("--name-status", parser.pretty, "--encoding=UTF-8",
                          hash?.asString() ?: GitRevisionNumber.HEAD.asString(),
                          "--max-count=$commitCount")
    handler.endOptions()
    handler.addRelativePaths(filePath)

    val result = mutableListOf<VcsFileRevisionEx>()
    val splitter = GitLogOutputSplitter(handler, parser) { record ->
      result.add(GitFileHistory.createGitFileRevision(project, root, record, filePath))
    }
    Git.getInstance().runCommandWithoutCollectingOutput(handler)
    splitter.reportErrors()
    return result
  }

  @Throws(VcsException::class)
  override fun getRename(root: VirtualFile, filePath: FilePath, beforeHash: Hash, afterHash: Hash): Rename? {
    val h = GitLineHandler(project, root, GitCommand.DIFF)
    h.setWithMediator(false)
    h.setStdoutSuppressed(true)
    h.addParameters("-M", "--diff-filter=R", "--name-status", "--encoding=UTF-8", "--follow",
                    beforeHash.asString() + ".." + afterHash.asString())
    h.endOptions()
    h.addRelativePaths(filePath)

    val renamesCollector = RenamesCollector(h.printableCommandLine())
    h.addLineListener(renamesCollector.getLineListener())
    Git.getInstance().runCommandWithoutCollectingOutput(h).throwOnError()

    renamesCollector.getSingleRename()?.let { info ->
      val firstPath = VcsUtil.getFilePath(root.path + "/" + info.firstPath, false)
      val secondPath = VcsUtil.getFilePath(root.path + "/" + info.secondPath, false)
      return Rename(firstPath, secondPath, beforeHash, afterHash)
    }

    return null
  }

  private inner class RenamesCollector(private val commandLine: String) : DefaultGitLogFullRecordBuilder() {
    private var unexpectedStatusReported: Boolean = false

    override fun addPath(type: Change.Type, firstPath: String, secondPath: String?) {
      if (type == Change.Type.MOVED) {
        super.addPath(type, firstPath, secondPath)
      }
      else if (!unexpectedStatusReported) {
        unexpectedStatusReported = true
        LOG.error("Unexpected change $type $firstPath $secondPath in the output of [$commandLine]")
      }
    }

    fun getLineListener(): GitLineHandlerListener {
      val parser = GitLogParser.PathsParser(GitLogParser.NameStatus.STATUS, this)
      return GitLineHandlerListener { line, outputType -> if (outputType == ProcessOutputTypes.STDOUT) parser.parseLine(line) }
    }

    fun getSingleRename(): VcsFileStatusInfo? {
      if (statuses.size > 1) {
        LOG.error("Unexpected multiple renames found. Command [$commandLine].\n" +
                  "Output [$statuses].")
      }

      val info = statuses.singleOrNull()
      if (info?.secondPath != null) return info
      return null
    }
  }

  companion object {
    private val LOG = Logger.getInstance(GitLogHistoryHandler::class.java)
  }
}