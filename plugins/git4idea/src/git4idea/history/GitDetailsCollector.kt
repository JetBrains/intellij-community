// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.history

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ArrayUtil
import com.intellij.util.Consumer
import com.intellij.util.ObjectUtils.notNull
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.ContainerUtil.getFirstItem
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.StopWatch
import git4idea.GitCommit
import git4idea.GitVcs
import git4idea.commands.Git
import git4idea.commands.GitLineHandler

object GitDetailsCollector {
  private val LOG = Logger.getInstance(GitDetailsCollector::class.java)

  @Throws(VcsException::class)
  fun readFullDetails(project: Project,
                      root: VirtualFile,
                      commitConsumer: Consumer<in GitCommit>,
                      requirements: GitCommitRequirements,
                      lowPriorityProcess: Boolean,
                      vararg parameters: String) {
    val configParameters = requirements.configParameters()
    val handler = GitLogUtil.createGitHandler(project, root, configParameters, lowPriorityProcess)
    readFullDetailsFromHandler(project, root, commitConsumer, handler, requirements, *parameters)
  }

  @Throws(VcsException::class)
  fun readFullDetailsForHashes(project: Project,
                               root: VirtualFile,
                               vcs: GitVcs,
                               hashes: List<String>,
                               requirements: GitCommitRequirements,
                               lowPriorityProcess: Boolean,
                               commitConsumer: Consumer<in GitCommit>) {
    if (hashes.isEmpty()) return
    val handler = GitLogUtil.createGitHandler(project, root, requirements.configParameters(), lowPriorityProcess)
    GitLogUtil.sendHashesToStdin(vcs, hashes, handler)

    readFullDetailsFromHandler(project, root, commitConsumer, handler, requirements, GitLogUtil.getNoWalkParameter(vcs), GitLogUtil.STDIN)
  }

  @Throws(VcsException::class)
  private fun readFullDetailsFromHandler(project: Project,
                                         root: VirtualFile,
                                         commitConsumer: Consumer<in GitCommit>,
                                         handler: GitLineHandler,
                                         requirements: GitCommitRequirements,
                                         vararg parameters: String) {
    val factory = GitLogUtil.getObjectsFactoryWithDisposeCheck(project) ?: return

    val commandParameters = ArrayUtil.mergeArrays(ArrayUtil.toStringArray(requirements.commandParameters()), *parameters)
    if (requirements.diffInMergeCommits == GitCommitRequirements.DiffInMergeCommits.DIFF_TO_PARENTS) {
      val consumer = { records: List<GitLogRecord> ->
        val firstRecord = records.first()
        val parents = firstRecord.parentsHashes

        LOG.assertTrue(parents.isEmpty() || parents.size == records.size,
                       "Not enough records for commit ${firstRecord.hash} " +
                       "expected ${parents.size} records, but got ${records.size}")

        commitConsumer.consume(createCommit(project, root, records, factory, requirements.diffRenameLimit))
      }
      val recordCollector = if (requirements.preserveOrder)
        GitLogRecordCollector(project, root, consumer)
      else
        GitLogUnorderedRecordCollector(project, root, consumer)

      readRecordsFromHandler(project, root, handler, recordCollector, *commandParameters)
      recordCollector.finish()
    }
    else {
      val consumer = Consumer<GitLogRecord> { record ->
        commitConsumer.consume(createCommit(project, root,
                                            ContainerUtil.newArrayList(record), factory,
                                            requirements.diffRenameLimit))
      }

      readRecordsFromHandler(project, root, handler, consumer, *commandParameters)
    }
  }

  @Throws(VcsException::class)
  private fun readRecordsFromHandler(project: Project,
                                     root: VirtualFile,
                                     handler: GitLineHandler,
                                     converter: Consumer<GitLogRecord>,
                                     vararg parameters: String) {
    val parser = GitLogParser(project, GitLogParser.NameStatus.STATUS, *GitLogUtil.COMMIT_METADATA_OPTIONS)
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

  private fun createCommit(project: Project, root: VirtualFile, records: List<GitLogRecord>,
                           factory: VcsLogObjectsFactory, renameLimit: GitCommitRequirements.DiffRenameLimit): GitCommit {
    val record = records.last()
    val parents = record.parentsHashes.map { factory.createHash(it) }

    return GitCommit(project, HashImpl.build(record.hash), parents, record.commitTime, root, record.subject,
                     factory.createUser(record.authorName, record.authorEmail), record.fullMessage,
                     factory.createUser(record.committerName, record.committerEmail), record.authorTimeStamp,
                     records.map { it.statusInfos },
                     renameLimit)
  }
}
