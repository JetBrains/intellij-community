// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.history

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Couple
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.impl.VcsFileStatusInfo
import com.intellij.vcs.log.impl.isRenamed
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitContentRevision
import git4idea.GitFileRevision
import git4idea.GitRevisionNumber
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.config.GitVersionSpecialty
import git4idea.history.GitLogParser.GitLogOption
import git4idea.log.GitLogProvider
import git4idea.repo.GitRepositoryManager
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.function.Consumer

/**
 * An implementation of file history algorithm with renames detection.
 * <p>
 * 'git log --follow' does detect renames, but it has a bug - merge commits aren't handled properly: they just disappear from the history.
 * See http://kerneltrap.org/mailarchive/git/2009/1/30/4861054 and the whole thread about that: --follow is buggy, but maybe it won't be fixed.
 * To get the whole history through renames we do the following:
 * 1. 'git log <file>' - and we get the history since the first rename, if there was one.
 * 2. 'git show -M --follow --name-status <first_commit_id> -- <file>'
 * where <first_commit_id> is the hash of the first commit in the history we got in #1.
 * With this command we get the rename-detection-friendly information about the first commit of the given file history.
 * (by specifying the <file> we filter out other changes in that commit; but in that case rename detection requires '--follow' to work,
 * that's safe for one commit though)
 * If the first commit was ADDING the file, then there were no renames with this file, we have the full history.
 * But if the first commit was RENAMING the file, we are going to query for the history before rename.
 * Now we have the previous name of the file:
 * <p>
 * ~/sandbox/git # git show --oneline --name-status -M 4185b97
 * 4185b97 renamed a to b
 * R100    a       b
 * <p>
 * 3. 'git log <rename_commit_id> -- <previous_file_name>' - get the history of a before the given commit.
 * We need to specify <rename_commit_id> here, because <previous_file_name> could have some new history, which has nothing common with our <file>.
 * Then we repeat 2 and 3 until the first commit is ADDING the file, not RENAMING it.
 * <p>
 * TODO: handle multiple repositories configuration: a file can be moved from one repo to another
 */
class GitFileHistory internal constructor(private val project: Project,
                                          private val root: VirtualFile,
                                          path: FilePath,
                                          private val startingRevisions: List<String>,
                                          private val fullHistory: Boolean = false) {
  private val path = VcsUtil.getLastCommitPath(project, path)

  @Throws(VcsException::class)
  internal fun load(consumer: (GitFileRevision) -> Unit, vararg parameters: String) = load(consumer, {}, *parameters)


  @Throws(VcsException::class)
  internal fun load(consumer: (GitFileRevision) -> Unit, renameConsumer: (FileRename) -> Unit, vararg parameters: String) {
    val logParser = createLogParser(project)

    val visitedCommits = mutableSetOf<String>()
    val starts = ContainerUtil.newLinkedList(FileHistoryStart(startingRevisions, path))
    while (starts.isNotEmpty()) {
      val (startRevisions, startPath) = starts.removeFirst()
      val lastCommits = runGitLog(logParser, startPath, visitedCommits, consumer, startRevisions + parameters.toList())
      if (lastCommits.isEmpty()) continue

      for (lastCommit in lastCommits) {
        val parents = getParentsAndPathsIfRename(lastCommit, startPath)
        parents.filter { it.path != startPath }.forEach {
          it.revisions.forEach { prevRevision -> renameConsumer(FileRename(prevRevision, lastCommit, it.path, startPath)) }
        }
        starts.addAll(parents)
      }
    }
  }

  @TestOnly
  internal fun getFilePath() = path

  @Throws(VcsException::class)
  private fun runGitLog(logParser: GitLogParser<GitLogFullRecord>,
                        startPath: FilePath,
                        visitedCommits: MutableSet<String>,
                        consumer: (GitFileRevision) -> Unit,
                        parameters: List<String>): List<String> {
    val handler = createLogHandler(logParser, startPath, parameters)
    var skipFurtherOutput = false
    val lastCommits = mutableListOf<String>()
    val splitter = GitLogOutputSplitter(handler, logParser) { record ->
      if (skipFurtherOutput || visitedCommits.contains(record.hash)) return@GitLogOutputSplitter

      visitedCommits.add(record.hash)
      if (record.statusInfos.firstOrNull()?.type == Change.Type.NEW && !path.isDirectory) {
        lastCommits.add(record.hash)
        if (!fullHistory) skipFurtherOutput = true
      }
      val revision = createGitFileRevision(project, root, record, startPath)
      consumer(revision)
    }
    Git.getInstance().runCommandWithoutCollectingOutput(handler).throwOnError()
    splitter.reportErrors()
    return lastCommits
  }

  /**
   * Gets info of the given commit and checks if a file was renamed there.
   * Returns a list of pairs consisting of the older file path, which file was renamed from and a parent commit hash as a string.
   */
  @Throws(VcsException::class)
  private fun getParentsAndPathsIfRename(commit: @NonNls String, filePath: FilePath): List<FileHistoryStart> {
    val requirements = GitCommitRequirements(diffRenames = GitCommitRequirements.DiffRenames.Limit.Default,
                                             diffInMergeCommits = GitCommitRequirements.DiffInMergeCommits.DIFF_TO_PARENTS)
    val h = GitLineHandler(project, root, GitCommand.SHOW, requirements.configParameters())
    val parser = GitLogParser.createDefaultParser(project, GitLogParser.NameStatus.STATUS, GitLogOption.PARENTS)
    h.setStdoutSuppressed(true)
    h.addParameters(requirements.commandParameters(project, h.executable))
    h.addParameters("--follow", "--name-status", parser.pretty, "--encoding=UTF-8", commit)
    h.endOptions()
    h.addRelativePaths(filePath)

    val output = Git.getInstance().runCommand(h).getOutputOrThrow()
    val records = parser.parse(output)

    if (records.isEmpty()) return emptyList()

    val parentsHashes = records.first().parentsHashes

    // each record correspond to a parent of the target commit (since DIFF_TO_PARENTS was passed to the "git show" command)
    val renames = parentsHashes.zip(records).mapNotNullValues { record ->
      record.statusInfos.firstOrNull(VcsFileStatusInfo::isRenamed)
    }.toMutableList()
    if (records.size < parentsHashes.size && fullHistory) {
      for (parent in parentsHashes.drop(records.size)) {
        val rename = GitLogHistoryHandler.getRename(project, root, parent, commit, filePath) ?: continue
        renames.add(Pair(parent, rename))
      }
    }

    return renames.map { (parent, status) -> FileHistoryStart(parent, GitContentRevision.createPath(root, status.firstPath)) }
  }

  private fun createLogHandler(parser: GitLogParser<GitLogFullRecord>, path: FilePath, parameters: List<String>): GitLineHandler {
    val h = GitLineHandler(project, root, GitCommand.LOG)
    h.setStdoutSuppressed(true)
    h.addParameters("--name-status", parser.pretty, "--encoding=UTF-8")
    if (GitVersionSpecialty.FULL_HISTORY_SIMPLIFY_MERGES_WORKS_CORRECTLY.existsIn(project) && Registry.`is`("git.file.history.full")) {
      h.addParameters("--full-history", "--simplify-merges")
    }
    h.addParameters(parameters)
    h.endOptions()
    h.addRelativePaths(path)
    return h
  }

  companion object {
    /**
     * Starting conditions for computing file history: target path and a list of commit hashes,
     * branch names or parameters such as "--branches", "--remotes" or "--tags".
     */
    private data class FileHistoryStart(val revisions: List<String>, val path: FilePath) {
      constructor(revision: String, path: FilePath) : this(listOf(revision), path)
    }

    private fun GitLogFullRecord.filePath(root: VirtualFile): FilePath? {
      val statusInfo = statusInfos.firstOrNull() ?: return null
      return VcsUtil.getFilePath(root.path + "/" + (statusInfo.secondPath ?: statusInfo.firstPath), false)
    }

    internal fun createGitFileRevision(project: Project, root: VirtualFile, record: GitLogFullRecord, filePath: FilePath): GitFileRevision {
      val revision = GitRevisionNumber(record.hash, record.date)
      val authorPair = Couple.of(record.authorName, record.authorEmail)
      val committerPair = Couple.of(record.committerName, record.committerEmail)
      val parents = listOf(*record.parentsHashes)
      val revisionPath = record.filePath(root) ?: filePath
      val deleted = record.statusInfos.firstOrNull()?.type == Change.Type.DELETED
      return GitFileRevision(project, root, revisionPath, revision, Couple.of(authorPair, committerPair),
                             record.fullMessage,
                             null, Date(record.authorTimeStamp), parents, deleted)
    }

    internal fun createLogParser(project: Project): GitLogParser<GitLogFullRecord> {
      return GitLogParser.createDefaultParser(project, GitLogParser.NameStatus.STATUS, GitLogOption.HASH, GitLogOption.COMMIT_TIME,
                                              GitLogOption.AUTHOR_NAME, GitLogOption.AUTHOR_EMAIL, GitLogOption.COMMITTER_NAME,
                                              GitLogOption.COMMITTER_EMAIL, GitLogOption.PARENTS,
                                              GitLogOption.SUBJECT, GitLogOption.BODY, GitLogOption.RAW_BODY,
                                              GitLogOption.AUTHOR_TIME)
    }

    fun loadHistory(project: Project,
                    path: FilePath,
                    startingFrom: VcsRevisionNumber?,
                    consumer: (GitFileRevision) -> Unit,
                    renameConsumer: (FileRename) -> Unit = {},
                    vararg parameters: String) {
      val detectedRoot = GitUtil.getRootForFile(project, path)
      val repositoryRoot = GitLogProvider.getCorrectedVcsRoot(GitRepositoryManager.getInstance(project), detectedRoot, path)
      val revision = startingFrom?.asString() ?: GitUtil.HEAD
      GitFileHistory(project, repositoryRoot, path, listOf(revision)).load(consumer, renameConsumer, *parameters)
    }

    /**
     * Get history for the file starting from specific revision and feed it to the consumer.
     *
     * @param project           Context project.
     * @param path              FilePath which history is queried.
     * @param startingFrom      Revision from which to start file history, when null history is started from HEAD revision.
     * @param consumer          This consumer is notified ([Consumer.accept]) when new history records are retrieved.
     * @param renameConsumer          This consumer is notified ([Consumer.accept]) when rename records are retrieved.
     * @param exceptionConsumer This consumer is notified in case of error while executing git command.
     * @param parameters        Optional parameters which will be added to the git log command just before the path.
     */
    @JvmStatic
    fun loadHistory(project: Project,
                    path: FilePath,
                    startingFrom: VcsRevisionNumber?,
                    consumer: Consumer<in GitFileRevision>,
                    exceptionConsumer: Consumer<in VcsException>,
                    renameConsumer: Consumer<FileRename> = Consumer { },
                    vararg parameters: String) {
      try {
        loadHistory(project, path, startingFrom, consumer::accept, renameConsumer::accept, *parameters)
      }
      catch (e: VcsException) {
        exceptionConsumer.accept(e)
      }
    }

    /**
     * Get history for the file starting from specific revision.
     *
     * @param project      the context project
     * @param path         the file path
     * @param startingFrom revision from which to start file history
     * @param parameters   optional parameters which will be added to the git log command just before the path
     * @return list of the revisions
     * @throws VcsException if there is problem with running git
     */
    @JvmStatic
    @Throws(VcsException::class)
    fun collectHistoryForRevision(project: Project,
                                  path: FilePath,
                                  startingFrom: VcsRevisionNumber,
                                  vararg parameters: String): List<VcsFileRevision> {
      val revisions = mutableListOf<VcsFileRevision>()
      loadHistory(project, path, startingFrom, revisions::add, {}, *parameters)
      return revisions
    }

    /**
     * Get history for the file.
     *
     * @param project    the context project
     * @param path       the file path
     * @param parameters optional parameters which will be added to the git log command just before the path
     * @return list of the revisions
     * @throws VcsException if there is problem with running git
     */
    @JvmStatic
    @Throws(VcsException::class)
    fun collectHistory(project: Project, path: FilePath, vararg parameters: String): List<VcsFileRevision> {
      return collectHistoryForRevision(project, path, GitRevisionNumber.HEAD, *parameters)
    }
  }
}

private fun <K, V, R> List<Pair<K, V>>.mapNotNullValues(mapping: (V) -> R?): List<Pair<K, R>> {
  return mapNotNull { (key, value) -> mapping(value)?.let { mappedValue -> key to mappedValue } }
}

data class FileRename(val beforeRevision: String?, val afterRevision: String, val beforePath: FilePath, val afterPath: FilePath)