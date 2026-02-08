// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.log

import com.intellij.dvcs.repo.getRepositoryUnlessFresh
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.trackActivityBlocking
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.util.ArrayUtilRt
import com.intellij.util.CollectConsumer
import com.intellij.util.Consumer
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.TimedVcsCommit
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogDiffHandler
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogFilterCollection.BRANCH_FILTER
import com.intellij.vcs.log.VcsLogFilterCollection.DATE_FILTER
import com.intellij.vcs.log.VcsLogFilterCollection.PARENT_FILTER
import com.intellij.vcs.log.VcsLogFilterCollection.RANGE_FILTER
import com.intellij.vcs.log.VcsLogFilterCollection.REVISION_FILTER
import com.intellij.vcs.log.VcsLogFilterCollection.STRUCTURE_FILTER
import com.intellij.vcs.log.VcsLogFilterCollection.TEXT_FILTER
import com.intellij.vcs.log.VcsLogFilterCollection.USER_FILTER
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsLogProperties
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogProviderRequirementsEx
import com.intellij.vcs.log.VcsLogRangeFilter
import com.intellij.vcs.log.VcsLogRefManager
import com.intellij.vcs.log.VcsLogRefresher
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsRefsContainer
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.data.VcsLogSorter
import com.intellij.vcs.log.graph.PermanentGraph
import com.intellij.vcs.log.graph.impl.facade.PermanentGraphImpl
import com.intellij.vcs.log.graph.impl.print.GraphColorGetterByNodeFactory
import com.intellij.vcs.log.impl.LogDataImpl
import com.intellij.vcs.log.impl.VcsActivityKey
import com.intellij.vcs.log.impl.VcsIndexableLogProvider
import com.intellij.vcs.log.impl.VcsLogIndexer
import com.intellij.vcs.log.util.UserNameRegex
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.vcs.log.visible.filters.hasLowerBound
import com.intellij.vcs.log.visible.filters.hasUpperBound
import com.intellij.vcs.log.visible.filters.matchesAll
import com.intellij.vcs.log.visible.filters.without
import com.intellij.vcs.log.visible.isAll
import com.intellij.vcsUtil.VcsFileUtil
import git4idea.GitUserRegistry
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.branch.GitBranchUtil
import git4idea.commands.Git
import git4idea.config.GitVersionSpecialty
import git4idea.history.GitCommitRequirements
import git4idea.history.GitCommitRequirements.DiffInMergeCommits
import git4idea.history.GitCommitRequirements.DiffRenames
import git4idea.history.GitLogCommandParameters
import git4idea.history.GitLogUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryChangeListener
import git4idea.repo.GitRepositoryManager
import git4idea.repo.GitRepositoryTagsHolder
import git4idea.repo.GitTagsHolderListener
import git4idea.repo.asSubmodule
import git4idea.telemetry.GitBackendTelemetrySpan.LogProvider.LoadingCommitsOnTaggedBranch
import git4idea.telemetry.GitBackendTelemetrySpan.LogProvider.ReadingBranches
import git4idea.telemetry.GitBackendTelemetrySpan.LogProvider.ReadingTags
import git4idea.telemetry.GitBackendTelemetrySpan.LogProvider.SortingCommits
import git4idea.telemetry.GitBackendTelemetrySpan.LogProvider.ValidatingData
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import org.jetbrains.annotations.CalledInAny
import kotlin.math.min

class GitLogProvider(private val project: Project) : VcsLogProvider, VcsIndexableLogProvider {
  private val repositoryManager: GitRepositoryManager = GitRepositoryManager.getInstance(project)
  private val refSorter: VcsLogRefManager = GitRefManager(project, repositoryManager)
  private val tracer = TelemetryManager.getInstance().getTracer(VcsScope)

  override suspend fun readRecentCommits(
    root: VirtualFile,
    requirements: VcsLogProvider.Requirements,
    refsLoadingPolicy: VcsLogProvider.RefsLoadingPolicy,
  ): VcsLogProvider.DetailedLogData {
    return if (GitLogExperimentalProvider.isEnabled) {
      project.serviceAsync<GitLogExperimentalProvider>().readRecentCommits(root, requirements, refsLoadingPolicy)
    }
    else {
      super.readRecentCommits(root, requirements, refsLoadingPolicy)
    }
  }

  @Throws(VcsException::class)
  override fun readFirstBlock(root: VirtualFile, requirements: VcsLogProvider.Requirements): VcsLogProvider.DetailedLogData {
    val repository = repositoryManager.getRepositoryUnlessFresh(root) ?: return LogDataImpl.empty()

    // need to query more to sort them manually; this doesn't affect performance: it is equal for -1000 and -2000
    val commitCount = requirements.commitCount * 2

    val params = arrayOf(GitUtil.HEAD, "--branches", "--remotes", "--max-count=$commitCount")
    // NB: not specifying --tags, because it introduces great slowdown if there are many tags,
    // but makes sense only if there are heads without branch or HEAD labels (rare case). Such cases are partially handled below.

    var refresh = false
    var isRefreshRefs = false

    if (requirements is VcsLogProviderRequirementsEx) {
      refresh = requirements.isRefresh
      isRefreshRefs = requirements.isRefreshRefs
    }

    val data = GitLogUtil.collectMetadata(project, root, *params)

    val safeRefs = data.refs
    val allRefs: MutableSet<VcsRef> = ObjectOpenCustomHashSet(safeRefs, DONT_CONSIDER_SHA)
    var branches: Set<VcsRef> = emptySet()

    if (isRefreshRefs) {
      val vcsObjectsFactory = project.service<VcsLogObjectsFactory>()
      branches = TelemetryManager.getInstance().getTracer(VcsScope).spanBuilder(ReadingBranches.name)
        .setAttribute("rootName", repository.root.name).use {
          repository.update()
          vcsObjectsFactory.createBranchesRefs(repository)
        }
      addNewElements(allRefs, branches)
    }

    val allDetails: MutableCollection<VcsCommitMetadata>
    var currentTagNames: Set<String>? = null
    var commitsFromTags: VcsLogProvider.DetailedLogData? = null
    if (!refresh || !isRefreshRefs) {
      allDetails = data.commits.toMutableList()
    }
    else {
      // on refresh: get new tags, which point to commits not from the first block; then get history, walking down just from these tags
      // on init: just ignore such tagged-only branches. The price for speed-up.
      val rex = requirements as VcsLogProviderRequirementsEx

      currentTagNames = readCurrentTagNames(root)
      addOldStillExistingTags(allRefs, currentTagNames, rex.previousRefs)

      allDetails = HashSet(data.commits)

      val previousTags = rex.previousRefs.getTagNames()
      val safeTags = safeRefs.getTagNames()
      val newUnmatchedTags = remove(currentTagNames, previousTags, safeTags)

      if (newUnmatchedTags.isNotEmpty()) {
        commitsFromTags = loadSomeCommitsOnTaggedBranches(root, commitCount, newUnmatchedTags)
        addNewElements(allDetails, commitsFromTags.commits)
        addNewElements(allRefs, commitsFromTags.refs)
      }
    }

    val sortedCommits = tracer.spanBuilder(SortingCommits.name).setAttribute("rootName", root.name).use {
      val commits = VcsLogSorter.sortByDateTopoOrder(allDetails)
      ContainerUtil.getFirstItems(commits, requirements.commitCount)
    }

    if (LOG.isDebugEnabled) {
      validateDataAndReportError(root, allRefs, sortedCommits, data, branches, currentTagNames, commitsFromTags)
    }

    return LogDataImpl(allRefs, sortedCommits)
  }

  private fun validateDataAndReportError(
    root: VirtualFile,
    allRefs: Set<VcsRef>,
    sortedCommits: List<VcsCommitMetadata>,
    firstBlockSyncData: VcsLogProvider.DetailedLogData,
    manuallyReadBranches: Set<VcsRef>,
    currentTagNames: Set<String>?,
    commitsFromTags: VcsLogProvider.DetailedLogData?,
  ) {
    tracer.spanBuilder(ValidatingData.name).setAttribute("rootName", root.name).use {
      val refs = ContainerUtil.map2Set(allRefs) { it.commitHash }

      PermanentGraphImpl.newInstance(sortedCommits, GraphColorGetterByNodeFactory { _, _ -> 0 }, { head1, head2 ->
        if (!refs.contains(head1) || !refs.contains(head2)) {
          val attachment = Attachment(
            "error-details.txt",
            printErrorDetails(root, allRefs, sortedCommits, firstBlockSyncData, manuallyReadBranches, currentTagNames, commitsFromTags)
          )
          LOG.error("GitLogProvider returned inconsistent data", attachment)
        }
        0
      }, refs)
    }
  }

  @Throws(VcsException::class)
  override fun readAllHashes(root: VirtualFile, commitConsumer: Consumer<in TimedVcsCommit>): VcsLogProvider.LogData {
    if (repositoryManager.getRepositoryUnlessFresh<GitRepository>(root) == null) {
      return LogDataImpl.empty()
    }

    val parameters = ArrayList(GitLogUtil.LOG_ALL)
    parameters.add("--date-order")

    val userRegistry = HashSet<VcsUser>()
    val refs = HashSet<VcsRef>()
    GitLogUtil.readTimedCommits(
      project, root, parameters,
      CollectConsumer(userRegistry),
      CollectConsumer(refs),
      commitConsumer::consume
    )
    return LogDataImpl(refs, userRegistry)
  }

  @Throws(VcsException::class)
  override fun readFullDetails(
    root: VirtualFile,
    hashes: List<String>,
    commitConsumer: Consumer<in VcsFullCommitDetails>,
  ) {
    val repository = repositoryManager.getRepositoryUnlessFresh(root) ?: return

    val requirements = GitCommitRequirements(
      shouldIncludeRootChanges(repository),
      DiffRenames.Limit.Default,
      DiffInMergeCommits.DIFF_TO_PARENTS
    )
    GitLogUtil.readFullDetailsForHashes(project, root, hashes, requirements, commitConsumer)
  }

  @Throws(VcsException::class)
  override fun readMetadata(root: VirtualFile, hashes: List<String>, consumer: Consumer<in VcsCommitMetadata>) {
    GitLogUtil.collectMetadata(project, root, hashes, consumer::consume)
  }

  override val supportedVcs: VcsKey
    get() = GitVcs.getKey()

  override val referenceManager: VcsLogRefManager
    get() = refSorter

  override fun subscribeToRootRefreshEvents(roots: Collection<VirtualFile>, refresher: VcsLogRefresher): Disposable {
    val connection = project.messageBus.connect()
    connection.subscribe(GitRepositoryTagsHolder.TAGS_UPDATED, GitTagsHolderListener { repository ->
      refreshOnRepoUpdate(repository, roots, refresher)
    })
    connection.subscribe(GitRepository.GIT_REPO_CHANGE, GitRepositoryChangeListener { repository ->
      refreshOnRepoUpdate(repository, roots, refresher)
    })
    return connection
  }

  private fun refreshOnRepoUpdate(repository: GitRepository, registeredRoots: Collection<VirtualFile>, refresher: VcsLogRefresher) {
    project.trackActivityBlocking(VcsActivityKey) {
      val root = repository.root
      if (registeredRoots.contains(root)) {
        refresher.refresh(root)
      }
    }
  }

  @Throws(VcsException::class)
  override fun getCommitsMatchingFilter(
    root: VirtualFile,
    filterCollection: VcsLogFilterCollection,
    graphOptions: PermanentGraph.Options,
    maxCount: Int,
  ): List<TimedVcsCommit> {
    val rangeFilter = filterCollection.get(RANGE_FILTER)
    if (rangeFilter == null) {
      return getCommitsMatchingFilter(root, filterCollection, null, graphOptions, maxCount)
    }

    /*
      We expect a "branch + range" combined filter to display the union of commits reachable from the branch,
      and commits belonging to the range. But Git intersects results for such parameters.
      => to solve this, we make a query for branch filters, and then separate queries for each of the ranges.
    */
    val commits = LinkedHashSet<TimedVcsCommit>()
    var modifiedFilterCollection = filterCollection
    if (filterCollection.get(BRANCH_FILTER) != null || filterCollection.get(REVISION_FILTER) != null) {
      commits.addAll(getCommitsMatchingFilter(root, filterCollection, null, graphOptions, maxCount))
      modifiedFilterCollection = filterCollection.without(BRANCH_FILTER).without(REVISION_FILTER)
    }
    for (range in rangeFilter.ranges) {
      commits.addAll(getCommitsMatchingFilter(root, modifiedFilterCollection, range, graphOptions, maxCount))
    }
    return ArrayList(commits)
  }

  @Throws(VcsException::class)
  private fun getCommitsMatchingFilter(
    root: VirtualFile,
    filterCollection: VcsLogFilterCollection,
    range: VcsLogRangeFilter.RefRange?,
    options: PermanentGraph.Options,
    maxCount: Int,
  ): List<TimedVcsCommit> {
    val parameters = getGitLogParameters(project, root, filterCollection, range, options, maxCount)
                     ?: return emptyList()

    val commits = ArrayList<TimedVcsCommit>()
    GitLogUtil.readTimedCommits(
      project, root,
      parameters.configParameters,
      parameters.filterParameters,
      null, null,
      CollectConsumer(commits)
    )
    return commits
  }

  override fun getCurrentUser(root: VirtualFile): VcsUser? {
    return GitUserRegistry.getInstance(project).getOrReadUser(root)
  }

  @Throws(VcsException::class)
  override fun getContainingBranches(root: VirtualFile, commitHash: Hash): Collection<String> {
    return GitBranchUtil.getBranches(project, root, true, true, commitHash.asString())
  }

  @CalledInAny
  override fun getCurrentBranch(root: VirtualFile): String? {
    val repository = repositoryManager.getRepositoryForRootQuick(root) ?: return null
    val currentBranchName = repository.currentBranchName
    if (currentBranchName == null && repository.currentRevision != null) {
      return GitUtil.HEAD
    }
    return currentBranchName
  }

  override val diffHandler: VcsLogDiffHandler
    get() = GitLogDiffHandler(project)

  override fun resolveReference(ref: String, root: VirtualFile): Hash? {
    val repository = repositoryManager.getRepositoryForRoot(root) ?: return null
    return Git.getInstance().resolveReference(repository, ref)
  }

  override fun getVcsRoot(project: Project, detectedRoot: VirtualFile, filePath: FilePath): VirtualFile {
    return getCorrectedVcsRoot(repositoryManager, detectedRoot, filePath)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getPropertyValue(property: VcsLogProperties.VcsLogProperty<T>): T? {
    return when (property) {
      VcsLogProperties.LIGHTWEIGHT_BRANCHES -> true as T
      VcsLogProperties.SUPPORTS_INDEXING -> true as T
      VcsLogProperties.SUPPORTS_LOG_DIRECTORY_HISTORY -> true as T
      VcsLogProperties.HAS_COMMITTER -> true as T
      else -> null
    }
  }

  override fun getIndexer(): VcsLogIndexer {
    return GitLogIndexer(project, repositoryManager)
  }

  @Throws(VcsException::class)
  private fun readCurrentTagNames(root: VirtualFile): Set<String> {
    return tracer.spanBuilder(ReadingTags.name).setAttribute("rootName", root.name).use {
      HashSet(GitBranchUtil.getAllTags(project, root))
    }
  }

  @Throws(VcsException::class)
  private fun loadSomeCommitsOnTaggedBranches(
    root: VirtualFile,
    commitCount: Int,
    unmatchedTags: Collection<String>,
  ): VcsLogProvider.DetailedLogData {
    return tracer.spanBuilder(LoadingCommitsOnTaggedBranch.name).setAttribute("rootName", root.name).use {
      val params = ArrayList<String>()
      params.add("--max-count=$commitCount")

      val refs = HashSet<VcsRef>()
      val commits = HashSet<VcsCommitMetadata>()
      VcsFileUtil.foreachChunk(ArrayList(unmatchedTags), 1) { tagsChunk ->
        val parameters = ArrayUtilRt.toStringArray(ContainerUtil.concat(params, tagsChunk))
        val logData = GitLogUtil.collectMetadata(project, root, *parameters)
        refs.addAll(logData.refs)
        commits.addAll(logData.commits)
      }

      LogDataImpl(refs, ArrayList(commits))
    }
  }

  companion object {
    private val LOG = logger<GitLogProvider>()

    @JvmField
    val DONT_CONSIDER_SHA: it.unimi.dsi.fastutil.Hash.Strategy<VcsRef> = object : it.unimi.dsi.fastutil.Hash.Strategy<VcsRef> {
      override fun hashCode(ref: VcsRef?): Int {
        if (ref == null) return 0
        return 31 * ref.name.hashCode() + ref.type.hashCode()
      }

      override fun equals(ref1: VcsRef?, ref2: VcsRef?): Boolean {
        if (ref1 === ref2) return true
        return ref1 != null && ref2 != null && ref1.name == ref2.name && ref1.type == ref2.type
      }
    }

    internal fun shouldIncludeRootChanges(repository: GitRepository): Boolean {
      return !repository.info.isShallow
    }

    internal fun getCorrectedVcsRoot(
      repositoryManager: GitRepositoryManager,
      detectedRoot: VirtualFile,
      path: FilePath,
    ): VirtualFile {
      if (path.isDirectory) return detectedRoot
      val repository = repositoryManager.getRepositoryForRootQuick(path)
      if (repository != null && repository.root == detectedRoot) {
        val submodule = repository.asSubmodule()
        if (submodule != null) {
          return submodule.parent.root
        }
      }
      return detectedRoot
    }

    internal fun getGitLogParameters(
      project: Project,
      root: VirtualFile,
      filterCollection: VcsLogFilterCollection,
      range: VcsLogRangeFilter.RefRange?,
      options: PermanentGraph.Options,
      maxCount: Int,
    ): GitLogCommandParameters? {
      val repository = GitRepositoryManager.getInstance(project).getRepositoryUnlessFresh(root) ?: return null

      val configParameters = ArrayList<String>()

      val branchLikeFilterParameters = getBranchLikeFilterParameters(repository, filterCollection, range)
      if (branchLikeFilterParameters.isEmpty()) {
        return null // no such branches in this repository => filter matches nothing
      }
      val filterParameters = ArrayList(branchLikeFilterParameters)

      val dateFilter = filterCollection.get(DATE_FILTER)
      if (dateFilter != null) {
        // assuming there is only one date filter, until filter expressions are defined
        dateFilter.after?.let { filterParameters.add(prepareParameter("after", it.toString())) }
        dateFilter.before?.let { filterParameters.add(prepareParameter("before", it.toString())) }
      }

      val textFilter = filterCollection.get(TEXT_FILTER)
      val text = textFilter?.text
      val regexp = textFilter == null || textFilter.isRegex
      val caseSensitive = textFilter?.matchesCase() ?: false
      appendTextFilterParameters(text, regexp, caseSensitive, filterParameters)

      val userFilter = filterCollection.get(USER_FILTER)
      if (userFilter != null) {
        val names = userFilter.getUsers(root).map { VcsUserUtil.toExactString(it) }
        if (regexp) {
          val authors = names.map(UserNameRegex.EXTENDED_INSTANCE::`fun`)
          if (GitVersionSpecialty.LOG_AUTHOR_FILTER_SUPPORTS_VERTICAL_BAR.existsIn(project)) {
            filterParameters.add(prepareParameter("author", authors.joinToString("|")))
          }
          else {
            filterParameters.addAll(authors.map { a -> prepareParameter("author", a) })
          }
        }
        else {
          filterParameters.addAll(names.map { prepareParameter("author", StringUtil.escapeBackSlashes(it)) })
        }
        configParameters.add("log.mailmap=false")
      }

      if (!isAll(maxCount)) {
        filterParameters.add(prepareParameter("max-count", maxCount.toString()))
      }

      if (options == PermanentGraph.Options.FirstParent) {
        filterParameters.add("--first-parent")
      }

      val parentFilter = filterCollection.get(PARENT_FILTER)
      if (parentFilter != null && !parentFilter.matchesAll) {
        if (parentFilter.hasLowerBound) {
          filterParameters.add("--min-parents=${parentFilter.minParents}")
        }
        if (parentFilter.hasUpperBound) {
          filterParameters.add("--max-parents=${parentFilter.maxParents}")
        }
      }

      // note: structure filter must be the last parameter, because it uses "--" which separates parameters from paths
      val structureFilter = filterCollection.get(STRUCTURE_FILTER)
      if (structureFilter != null) {
        val files = structureFilter.files
        if (files.isNotEmpty()) {
          filterParameters.add("--full-history")
          filterParameters.add("--simplify-merges")
          filterParameters.add("--")
          for (file in files) {
            filterParameters.add(VcsFileUtil.relativePath(root, file))
          }
        }
      }

      return GitLogCommandParameters(configParameters, filterParameters)
    }

    internal fun getBranchLikeFilterParameters(
      repository: GitRepository,
      filterCollection: VcsLogFilterCollection,
      range: VcsLogRangeFilter.RefRange?,
    ): List<String> {
      val branchFilter = filterCollection.get(BRANCH_FILTER)
      val revisionFilter = filterCollection.get(REVISION_FILTER)
      if (branchFilter == null && revisionFilter == null && range == null) {
        return GitLogUtil.LOG_ALL
      }

      val result = ArrayList<String>()

      if (branchFilter != null) {
        val branches = ContainerUtil.newArrayList(
          ContainerUtil.concat(
            repository.branches.localBranches,
            repository.branches.remoteBranches
          )
        )
        val branchNames = GitBranchUtil.convertBranchesToNames(branches)
        val predefinedNames = listOf(GitUtil.HEAD)

        for (branchName in ContainerUtil.concat(branchNames, predefinedNames)) {
          if (branchFilter.matches(branchName)) {
            result.add(branchName)
          }
        }
      }

      if (revisionFilter != null) {
        for (commit in revisionFilter.heads) {
          if (commit.root == repository.root) {
            result.add(commit.hash.asString())
          }
        }
      }

      if (range != null) {
        result.add("${range.exclusiveRef}..${range.inclusiveRef}")
      }

      return result
    }

    private fun appendTextFilterParameters(
      text: String?,
      regexp: Boolean,
      caseSensitive: Boolean,
      filterParameters: MutableList<in String>,
    ) {
      if (text != null) {
        filterParameters.add(prepareParameter("grep", text))
      }
      filterParameters.add(if (regexp) "--extended-regexp" else "--fixed-strings")
      if (!caseSensitive) {
        filterParameters.add("--regexp-ignore-case") // affects case sensitivity of any filter (except file filter)
      }
    }

    private fun prepareParameter(paramName: String, value: String): String {
      return "--$paramName=$value" // no value quoting needed, because the parameter itself will be quoted by GeneralCommandLine
    }

    private fun printErrorDetails(
      root: VirtualFile,
      allRefs: Set<VcsRef>,
      sortedCommits: List<VcsCommitMetadata>,
      firstBlockSyncData: VcsLogProvider.DetailedLogData,
      manuallyReadBranches: Set<VcsRef>,
      currentTagNames: Set<String>?,
      commitsFromTags: VcsLogProvider.DetailedLogData?,
    ): String {
      val sb = StringBuilder()
      sb.append("[${root.name}]\n")
      sb.append("First block data from Git:\n")
      sb.append(printLogData(firstBlockSyncData))
      sb.append("\n\nManually read refs:\n")
      sb.append(printRefs(manuallyReadBranches))
      sb.append("\n\nCurrent tag names:\n")
      if (currentTagNames != null) {
        sb.append(currentTagNames.joinToString(", "))
        if (commitsFromTags != null) {
          sb.append(printLogData(commitsFromTags))
        }
        else {
          sb.append("\n\nCommits from new tags were not read.\n")
        }
      }
      else {
        sb.append("\n\nCurrent tags were not read\n")
      }

      sb.append("\n\nResult:\n")
      sb.append("\nCommits (last 100): \n")
      sb.append(printCommits(sortedCommits))
      sb.append("\nAll refs:\n")
      sb.append(printRefs(allRefs))
      return sb.toString()
    }

    private fun printLogData(firstBlockSyncData: VcsLogProvider.DetailedLogData): String {
      return "Last 100 commits:\n${printCommits(firstBlockSyncData.commits)}\nRefs:\n${printRefs(firstBlockSyncData.refs)}"
    }

    private fun printCommits(commits: List<VcsCommitMetadata>): String {
      val sb = StringBuilder()
      for (i in 0 until min(commits.size, 100)) {
        val commit = commits[i]
        sb.append("${commit.id.toShortString()} -> ${commit.parents.joinToString(", ") { it.toShortString() }}\n")
      }
      return sb.toString()
    }

    private fun printRefs(refs: Set<VcsRef>): String {
      return refs.joinToString("\n") { "${it.commitHash.toShortString()} : ${it.name}" }
    }

    private fun addOldStillExistingTags(
      allRefs: MutableSet<in VcsRef>,
      currentTags: Set<String>,
      previousRefs: VcsRefsContainer,
    ) {
      for (ref in previousRefs.allRefs()) {
        if (!allRefs.contains(ref) && currentTags.contains(ref.name)) {
          allRefs.add(ref)
        }
      }
    }

    private fun <T> remove(original: Set<T>, vararg toRemove: Set<T>): Set<T> {
      val result = HashSet(original)
      for (set in toRemove) {
        result.removeAll(set)
      }
      return result
    }

    private fun <T> addNewElements(original: MutableCollection<in T>, toAdd: Collection<T>) {
      for (item in toAdd) {
        if (!original.contains(item)) {
          original.add(item)
        }
      }
    }

    private fun Collection<VcsRef>.getTagNames(): Set<String> = mapNotNullTo(mutableSetOf()) { ref: VcsRef ->
      if (ref.type == GitRefManager.TAG) ref.name else null
    }

    private fun VcsRefsContainer.getTagNames(): Set<String> =
      tags().mapTo(mutableSetOf()) { ref: VcsRef -> ref.name }
  }
}
