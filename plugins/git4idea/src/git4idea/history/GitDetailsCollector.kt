// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.Consumer
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.StopWatch
import git4idea.GitCommit
import git4idea.commands.Git
import git4idea.commands.GitLineHandler

internal abstract class GitDetailsCollector<R : GitLogRecord, C : VcsCommitMetadata>(protected val project: Project,
                                                                                     protected val root: VirtualFile,
                                                                                     private val recordBuilder: GitLogRecordBuilder<R>) {
  @Throws(VcsException::class)
  fun readFullDetails(commitConsumer: Consumer<in C>,
                      requirements: GitCommitRequirements,
                      lowPriorityProcess: Boolean,
                      vararg parameters: String) {
    val configParameters = requirements.configParameters()
    val handler = GitLogUtil.createGitHandler(project, root, configParameters, lowPriorityProcess)
    readFullDetailsFromHandler(commitConsumer, handler, requirements, *parameters)
  }

  @Throws(VcsException::class)
  fun readFullDetailsForHashes(hashes: List<String>,
                               requirements: GitCommitRequirements,
                               lowPriorityProcess: Boolean,
                               commitConsumer: Consumer<in C>) {
    if (hashes.isEmpty()) return
    val handler = GitLogUtil.createGitHandler(project, root, requirements.configParameters(), lowPriorityProcess)
    GitLogUtil.sendHashesToStdin(hashes, handler)

    readFullDetailsFromHandler(commitConsumer, handler, requirements, GitLogUtil.getNoWalkParameter(project), GitLogUtil.STDIN)
  }

  @Throws(VcsException::class)
  private fun readFullDetailsFromHandler(commitConsumer: Consumer<in C>,
                                         handler: GitLineHandler,
                                         requirements: GitCommitRequirements,
                                         vararg parameters: String) {
    val factory = GitLogUtil.getObjectsFactoryWithDisposeCheck(project) ?: return

    val commandParameters = ArrayUtil.mergeArrays(ArrayUtil.toStringArray(requirements.commandParameters(project)), *parameters)
    if (requirements.diffInMergeCommits == GitCommitRequirements.DiffInMergeCommits.DIFF_TO_PARENTS) {
      val consumer = { records: List<R> ->
        val firstRecord = records.first()
        val parents = firstRecord.parentsHashes

        if (parents.isEmpty() || parents.size == records.size) {
          commitConsumer.consume(createCommit(records, factory, requirements.diffRenameLimit))
        }
        else {
          LOG.warn("Not enough records for commit ${firstRecord.hash} " +
                   "expected ${parents.size} records, but got ${records.size}")
        }
      }

      val recordCollector = createRecordsCollector(consumer)
      readRecordsFromHandler(handler, recordCollector, *commandParameters)
      recordCollector.finish()
    }
    else {
      val consumer = Consumer<R> { record ->
        commitConsumer.consume(createCommit(mutableListOf(record), factory,
                                            requirements.diffRenameLimit))
      }

      readRecordsFromHandler(handler, consumer, *commandParameters)
    }
  }

  @Throws(VcsException::class)
  private fun readRecordsFromHandler(handler: GitLineHandler, converter: Consumer<R>, vararg parameters: String) {
    val parser = GitLogParser(project, recordBuilder, GitLogParser.NameStatus.STATUS, *GitLogUtil.COMMIT_METADATA_OPTIONS)
    handler.setStdoutSuppressed(true)
    handler.addParameters(*parameters)
    handler.addParameters(parser.pretty, "--encoding=UTF-8")
    handler.addParameters("--name-status")
    handler.endOptions()

    val sw = StopWatch.start("loading details in [" + root.name + "]")

    val handlerListener = GitLogOutputSplitter(handler, parser, converter)
    Git.getInstance().runCommandWithoutCollectingOutput(handler).throwOnError()
    handlerListener.reportErrors()

    sw.report()
  }

  protected abstract fun createRecordsCollector(consumer: (List<R>) -> Unit): GitLogRecordCollector<R>

  protected abstract fun createCommit(records: List<R>, factory: VcsLogObjectsFactory,
                                      renameLimit: GitCommitRequirements.DiffRenameLimit): C

  companion object {
    private val LOG = Logger.getInstance(GitDetailsCollector::class.java)
  }
}

internal class GitFullDetailsCollector(project: Project, root: VirtualFile,
                                       recordBuilder: GitLogRecordBuilder<GitLogFullRecord>) :
  GitDetailsCollector<GitLogFullRecord, GitCommit>(project, root, recordBuilder) {

  internal constructor(project: Project, root: VirtualFile) : this(project, root, DefaultGitLogFullRecordBuilder())

  override fun createCommit(records: List<GitLogFullRecord>, factory: VcsLogObjectsFactory,
                            renameLimit: GitCommitRequirements.DiffRenameLimit): GitCommit {
    val record = records.last()
    val parents = record.parentsHashes.map { factory.createHash(it) }

    return GitCommit(project, HashImpl.build(record.hash), parents, record.commitTime, root, record.subject,
                     factory.createUser(record.authorName, record.authorEmail), record.fullMessage,
                     factory.createUser(record.committerName, record.committerEmail), record.authorTimeStamp,
                     records.map { it.statusInfos }
    )
  }

  override fun createRecordsCollector(consumer: (List<GitLogFullRecord>) -> Unit): GitLogRecordCollector<GitLogFullRecord> {
    return object : GitLogRecordCollector<GitLogFullRecord>(project, root, consumer) {
      override fun createEmptyCopy(r: GitLogFullRecord): GitLogFullRecord = GitLogFullRecord(r.options, listOf(), r.isSupportsRawBody)
    }
  }
}