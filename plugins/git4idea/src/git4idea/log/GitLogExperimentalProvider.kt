// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.dvcs.repo.getRepositoryUnlessFresh
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.vcs.impl.shared.telemetry.VcsTracer
import com.intellij.platform.vcs.impl.shared.telemetry.traceSuspending
import com.intellij.platform.vcs.impl.shared.telemetry.withVcsAttributes
import com.intellij.util.ArrayUtilRt
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsRefsContainer
import com.intellij.vcs.log.data.VcsLogSorter
import com.intellij.vcs.log.impl.LogDataImpl
import com.intellij.vcsUtil.VcsFileUtil
import fleet.util.logging.logger
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.history.GitLogOutputSplitter
import git4idea.history.GitLogParser
import git4idea.history.GitLogRecord
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.telemetry.GitBackendTelemetrySpan.Log
import git4idea.telemetry.GitBackendTelemetrySpan.LogProvider.LoadingCommitsOnTaggedBranch
import git4idea.telemetry.GitBackendTelemetrySpan.LogProvider.SortingCommits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val LOG = logger<GitLogExperimentalProvider>()

@Service(Service.Level.PROJECT)
internal class GitLogExperimentalProvider(private val project: Project) {
  private val vcsLogObjectsFactory = project.service<VcsLogObjectsFactory>()

  suspend fun readRecentCommits(
    root: VirtualFile,
    requirements: VcsLogProvider.Requirements,
    refsLoadingPolicy: VcsLogProvider.RefsLoadingPolicy,
  ): VcsLogProvider.DetailedLogData {
    val repository = withContext(Dispatchers.IO) {
      coroutineToIndicator {
        GitRepositoryManager.getInstance(project).getRepositoryUnlessFresh(root)
      }
    } ?: return LogDataImpl.empty()

    return withContext(Dispatchers.Default) {
      val branches = vcsLogObjectsFactory.createBranchesRefsSequence(repository)
      val tagsLoader = TagsLoader.create(repository, refsLoadingPolicy)

      // need to query more to sort them manually; this doesn't affect performance: it is equal for -1000 and -2000
      val commitsFromLogCommand = getCommits(root, requirements.commitCount * 2)
      val allCommits = commitsFromLogCommand + tagsLoader.loadCommitsReachableOnlyFromTags(commitsFromLogCommand)

      val sortedCommits = VcsTracer.traceSuspending(SortingCommits) {
        it.withVcsAttributes(root)
        VcsLogSorter.sortByDateTopoOrder(allCommits).take(requirements.commitCount)
      }

      val allRefs = branches + tagsLoader.tags.asSequence()
      SequenceBasedLogData(allRefs, sortedCommits)
    }
  }

  private suspend fun getCommits(root: VirtualFile, commitCount: Int): List<VcsCommitMetadata> {
    checkCanceled()
    val parser = GitLogParser.createDefaultParser(project, *GitLogUtil.COMMIT_METADATA_OPTIONS)
    val handler = GitLogUtil.createGitHandler(project, root).apply {
      setStdoutSuppressed(true)
      // Don't request refs, don't decorate and don't sort by date
      addParameters(
        GitUtil.HEAD,
        "--branches",
        "--remotes",
        "--max-count=$commitCount",
        parser.pretty,
        "--encoding=UTF-8",
      )
      endOptions()
    }

    val commits = mutableListOf<VcsCommitMetadata>()
    val handlerListener = GitLogOutputSplitter(handler, parser, { record: GitLogRecord ->
      commits.add(GitLogUtil.createMetadata(root, record, vcsLogObjectsFactory))
    })
    VcsTracer.traceSuspending(Log.LoadingCommitMetadata) {
      it.withVcsAttributes(root)
      withContext(Dispatchers.IO) {
        coroutineToIndicator {
          Git.getInstance().runCommandWithoutCollectingOutput(handler).throwOnError()
          handlerListener.reportErrors()
        }
      }
    }
    return commits
  }

  companion object {
    val isEnabled: Boolean
      get() = Registry.`is`("git.log.provider.experimental.refs.collection", false)
  }
}

private class SequenceBasedLogData(
  refsSequence: Sequence<VcsRef>,
  override val commits: List<VcsCommitMetadata>,
) : VcsLogProvider.DetailedLogData {
  override val refsIterable: Iterable<VcsRef> = refsSequence.asIterable()
}

/**
 * There might be some commits reachable only from tags. To list them, `--tags` should be
 * added to `git log` command. However, it causes a noticeable slowdown in repositories with many tags.
 * For this reason, such commits are handled separately.
 */
private class TagsLoader(
  private val repository: GitRepository,
  val tags: Collection<VcsRef>,
  /**
   * Tags that which were added or which hash was changed after the previous refresh
   */
  val modifiedTags: Collection<VcsRef>,
) {
  suspend fun loadCommitsReachableOnlyFromTags(commitsFromLog: List<VcsCommitMetadata>): Collection<VcsCommitMetadata> {
    val unreachableTags = getUnreachableTags(commitsFromLog)
    if (unreachableTags.isEmpty()) {
      LOG.debug { "No tags to load" }
      return emptySet()
    }
    LOG.info { "Processing ${unreachableTags.size} tags" }
    val commits = HashSet<VcsCommitMetadata>()
    VcsTracer.traceSuspending(LoadingCommitsOnTaggedBranch) {
      it.withVcsAttributes(repository.root)
      val maxCountParam = listOf("--max-count=$COMMITS_TO_LOAD")

      withContext(Dispatchers.IO) {
        VcsFileUtil.foreachChunk(unreachableTags, 1) { tagsChunk ->
          val parameters = ArrayUtilRt.toStringArray(maxCountParam + tagsChunk)
          val logData = GitLogUtil.collectMetadata(repository.project, repository.root, *parameters)
          commits.addAll(logData.commits)
        }
      }
    }
    return commits
  }

  /**
   * Returns a list of modified tags that are not reachable from the loaded commits.
   */
  private fun getUnreachableTags(commits: List<VcsCommitMetadata>): List<String> {
    if (modifiedTags.isEmpty()) return emptyList()

    val commitsHashes = commits.mapTo(mutableSetOf()) { it.id }
    return modifiedTags.mapNotNull {
      if (commitsHashes.contains(it.commitHash)) null else it.name
    }
  }

  companion object {
    private const val COMMITS_TO_LOAD = 20

    fun create(repository: GitRepository, refsLoadingPolicy: VcsLogProvider.RefsLoadingPolicy): TagsLoader {
      val (allTags, modifiedTags) = when (refsLoadingPolicy) {
        is VcsLogProvider.RefsLoadingPolicy.LoadAllRefs -> {
          val factory = repository.project.service<VcsLogObjectsFactory>()
          val allTags =
            repository.tagsHolder.state.value.tagsToCommitHashes.map { (tag, hash) -> factory.createTag(repository, tag.name, hash) }
          val modifiedTags = getModifiedTags(allTags, refsLoadingPolicy.previouslyLoadedRefs)
          Pair(allTags, modifiedTags)
        }
        is VcsLogProvider.RefsLoadingPolicy.FromLoadedCommits -> Pair(emptyList(), emptyList())
      }

      return TagsLoader(repository, allTags, modifiedTags)
    }

    private fun getModifiedTags(tags: Collection<VcsRef>, previousRefs: VcsRefsContainer): Collection<VcsRef> {
      val previousTags = previousRefs.tags().associateBy { it.name }
      val newTags = tags.filter { tag -> previousTags[tag.name]?.commitHash != tag.commitHash }
      LOG.debug { "${newTags.size} newly added tags" }
      return newTags
    }
  }
}
