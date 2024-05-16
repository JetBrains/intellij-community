// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.*
import com.intellij.vcs.log.VcsLogFileHistoryHandler.Rename
import com.intellij.vcs.log.impl.VcsFileStatusInfo
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import com.intellij.vcs.log.visible.filters.keysToSet
import com.intellij.vcs.log.visible.isAll
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.commands.GitLineHandlerListener
import git4idea.i18n.GitBundle
import git4idea.log.GitLogProvider
import git4idea.repo.GitRepositoryManager

open class GitLogHistoryHandler(private val project: Project) : VcsLogFileHistoryHandler {

  override val supportedVcs: VcsKey = GitVcs.getKey()

  private val supportedFilters = setOf(VcsLogFilterCollection.BRANCH_FILTER, VcsLogFilterCollection.REVISION_FILTER)

  override fun getSupportedFilters(root: VirtualFile, filePath: FilePath, hash: Hash?): Set<VcsLogFilterCollection.FilterKey<*>> {
    if (hash != null) return emptySet()
    return supportedFilters
  }

  protected fun getDefaultFilters(root: VirtualFile, hash: Hash?): VcsLogFilterCollection {
    return VcsLogFilterObject.collection(hash?.let { VcsLogFilterObject.fromCommit(CommitId(it, root)) }
                                         ?: VcsLogFilterObject.fromBranch(VcsLogUtil.HEAD))
  }

  @Throws(UnsupportedHistoryFiltersException::class)
  private fun throwIfFiltersNotSupported(root: VirtualFile, hash: Hash?, filters: VcsLogFilterCollection) {
    if (hash == null && filters.keysToSet.all { supportedFilters.contains(it) }) return
    if (filters == getDefaultFilters(root, hash)) return

    val presentation = filters.filters.joinToString { it.displayText }
    val message = if (hash == null) GitBundle.message("history.filters.unsupported.error.message", presentation)
    else GitBundle.message("history.for.revision.filters.unsupported.error.message", presentation)

    throw UnsupportedHistoryFiltersException(message)
  }

  @Throws(VcsException::class)
  override fun getHistoryFast(root: VirtualFile, filePath: FilePath, hash: Hash?, filters: VcsLogFilterCollection, commitCount: Int, consumer: (VcsFileRevision) -> Unit) {
    throwIfFiltersNotSupported(root, hash, filters)

    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root) ?: return
    val parser = GitFileHistory.createLogParser(project)
    val handler = GitLineHandler(project, root, GitCommand.LOG)
    handler.setStdoutSuppressed(true)
    handler.addParameters("--name-status", parser.pretty, "--encoding=UTF-8")
    if (!isAll(commitCount)) {
      handler.addParameters("--max-count=$commitCount")
    }
    handler.addParameters(GitLogProvider.getBranchLikeFilterParameters(repository, filters, null))
    handler.endOptions()
    handler.addRelativePaths(filePath)

    val splitter = GitLogOutputSplitter(handler, parser) { record ->
      consumer(GitFileHistory.createGitFileRevision(project, root, record, filePath))
    }
    Git.getInstance().runCommandWithoutCollectingOutput(handler).throwOnError()
    splitter.reportErrors()
  }

  @Throws(VcsException::class)
  override fun collectHistory(root: VirtualFile, filePath: FilePath, hash: Hash?, filters: VcsLogFilterCollection, consumer: (VcsFileRevision) -> Unit) {
    throwIfFiltersNotSupported(root, hash, filters)

    val repository = GitRepositoryManager.getInstance(project).getRepositoryForRoot(root) ?: return
    val args = GitHistoryProvider.getHistoryLimitArgs(project)
    // filtered branches are passed to git as starting points,
    // since it's not possible to pass a starting point separately from branch filters
    val startingRevisions = GitLogProvider.getBranchLikeFilterParameters(repository, filters, null)
    GitFileHistory(project, root, filePath, startingRevisions, true).load(consumer, *args)
  }

  @Throws(VcsException::class)
  override fun getRename(root: VirtualFile, filePath: FilePath, beforeHash: Hash, afterHash: Hash): Rename? {
    val info = getRename(project, root, beforeHash.asString(), afterHash.asString(), filePath) ?: return null
    val firstPath = VcsUtil.getFilePath(root.path + "/" + info.firstPath, false)
    val secondPath = VcsUtil.getFilePath(root.path + "/" + info.secondPath, false)
    return Rename(firstPath, secondPath, beforeHash, afterHash)
  }

  private class RenamesCollector(private val commandLine: String) : DefaultGitLogFullRecordBuilder() {
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

    @Throws(VcsException::class)
    internal fun getRename(project: Project,
                           root: VirtualFile,
                           beforeHash: @NlsSafe String,
                           afterHash: @NlsSafe String,
                           filePath: FilePath): VcsFileStatusInfo? {
      val h = GitLineHandler(project, root, GitCommand.DIFF)
      h.setWithMediator(false)
      h.setStdoutSuppressed(true)
      h.addParameters("-M", "--diff-filter=R", "--name-status", "--encoding=UTF-8", "--follow", "$beforeHash..$afterHash")
      h.endOptions()
      h.addRelativePaths(filePath)

      val renamesCollector = RenamesCollector(h.printableCommandLine())
      h.addLineListener(renamesCollector.getLineListener())
      Git.getInstance().runCommandWithoutCollectingOutput(h).throwOnError()

      return renamesCollector.getSingleRename()
    }
  }
}