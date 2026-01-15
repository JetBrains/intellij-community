// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.util.messages.MessageBus
import com.intellij.vcs.log.graph.PermanentGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

/**
 * Provides the information needed to build the VCS log, such as the list of most recent commits with their parents.
 */
interface VcsLogProvider {
  /**
   * Reads the most recent commits from the log together with all repository references.
   *
   * Commits should be at least topologically ordered, better considering commit time as well: they will be shown in the log in this order.
   *
   * This method is called both on the startup and on refresh.
   *
   * @param root The root of the repository where the commits and references should be read from.
   * @param requirements Specifies the limitations on the commit data to return, such as the required number of commits.
   * @param refsLoadingPolicy policy defining whether repository references should be loaded or skipped (e.g., to optimize an initial load).
   *
   * @return List of commits along with their metadata and repository references if requested.
   */
  @Throws(VcsException::class)
  suspend fun readRecentCommits(
    root: VirtualFile,
    requirements: Requirements,
    refsLoadingPolicy: RefsLoadingPolicy,
  ): DetailedLogData {
    return withContext(Dispatchers.IO) { coroutineToIndicator { readFirstBlock(root, requirements) } }
  }

  /**
   * Reads the most recent commits from the log together with all repository references.
   *
   * Commits should be at least topologically ordered, better considering commit time as well: they will be shown in the log in this order.
   *
   * This method is called both on the startup and on refresh.
   *
   * @param requirements some limitations on commit data that should be returned, e.g. the number of commits.
   * @return given amount of ordered commits and **all** references in the repository.
   * @deprecated Use [readRecentCommits] instead
   */
  @Deprecated("Use readRecentCommits instead", ReplaceWith("readRecentCommits(root, requirements)"))
  @Throws(VcsException::class)
  fun readFirstBlock(root: VirtualFile, requirements: Requirements): DetailedLogData {
    throw UnsupportedOperationException("Method readFirstBlock is not implemented in class ${this.javaClass.name}. Please implement readRecentCommits instead.")
  }

  /**
   * Reads the whole history.
   *
   * Reports the commits to the consumer to avoid creation & even temporary storage of a too large commits collection.
   *
   * @return all references and all authors in the repository.
   */
  @Throws(VcsException::class)
  fun readAllHashes(root: VirtualFile, commitConsumer: Consumer<in TimedVcsCommit>): LogData

  /**
   * Reads those details of the given commits, which are necessary to be shown in the log table and commit details.
   */
  @Throws(VcsException::class)
  fun readMetadata(root: VirtualFile, hashes: List<String>, consumer: Consumer<in VcsCommitMetadata>)

  /**
   * Reads full details for specified commits in the repository.
   *
   * Reports the commits to the consumer to avoid creation & even temporary storage of a too large commits collection.
   */
  @Throws(VcsException::class)
  fun readFullDetails(root: VirtualFile, hashes: List<String>, commitConsumer: Consumer<in VcsFullCommitDetails>)

  /**
   * Returns the VCS which is supported by this provider.
   *
   * If there will be several VcsLogProviders which support the same VCS, only one will be chosen. It is undefined, which one.
   */
  val supportedVcs: VcsKey

  /**
   * Returns the [VcsLogRefManager] which will be used to identify positions of references in the log table, on the branches panel,
   * and on the details panel.
   */
  val referenceManager: VcsLogRefManager

  /**
   * Starts listening to events from the certain VCS, which should lead to the log refresh.
   *
   * Returns disposable that unsubscribes from events.
   * Using a [MessageBus] topic can help to accomplish that.
   *
   * @param roots     VCS roots which should be listened to.
   * @param refresher The refresher which should be notified about the need of refresh.
   * @return Disposable that unsubscribes from events on dispose.
   */
  fun subscribeToRootRefreshEvents(roots: Collection<VirtualFile>, refresher: VcsLogRefresher): Disposable

  /**
   * @deprecated implement [VcsLogProvider.getCommitsMatchingFilter] with graphOptions parameter instead
   */
  @Deprecated("implement getCommitsMatchingFilter with graphOptions parameter instead")
  @Throws(VcsException::class)
  fun getCommitsMatchingFilter(
    root: VirtualFile,
    filterCollection: VcsLogFilterCollection,
    maxCount: Int,
  ): List<TimedVcsCommit> {
    return getCommitsMatchingFilter(root, filterCollection, PermanentGraph.Options.Default, maxCount)
  }

  /**
   * Return commits, which correspond to the given filters.
   *
   * @param root             repository root
   * @param filterCollection filters to use
   * @param graphOptions     additional options, such as "--first-parent", see [PermanentGraph.Options]
   * @param maxCount         maximum number of commits to request from the VCS, or -1 for unlimited.
   */
  @Throws(VcsException::class)
  fun getCommitsMatchingFilter(
    root: VirtualFile,
    filterCollection: VcsLogFilterCollection,
    graphOptions: PermanentGraph.Options,
    maxCount: Int,
  ): List<TimedVcsCommit> {
    throw UnsupportedOperationException("Method getCommitsMatchingFilter is not implemented the class ${this.javaClass.name}")
  }

  /**
   * Returns the name of current user as specified for the given root,
   * or null if user didn't configure his name in the VCS settings.
   */
  @Throws(VcsException::class)
  fun getCurrentUser(root: VirtualFile): VcsUser?

  /**
   * Returns the list of names of branches/references which contain the given commit.
   */
  @Throws(VcsException::class)
  fun getContainingBranches(root: VirtualFile, commitHash: Hash): Collection<String>

  /**
   * In order to tune log for it's VCS, provider may set value to one of the properties specified in [VcsLogProperties].
   *
   * @param property Property instance to return value for.
   * @param T        Type of property value.
   * @return Property value or null if unset.
   */
  fun <T> getPropertyValue(property: VcsLogProperties.VcsLogProperty<T>): T?

  /**
   * Returns currently checked out branch in given root, or null if not on any branch or provided root is not under version control.
   *
   * @param root root for which branch is requested.
   * @return branch that is currently checked out in the specified root.
   */
  fun getCurrentBranch(root: VirtualFile): String?

  /**
   * Returns [VcsLogDiffHandler] for this provider in order to support comparing commits and with local version from log-based file history.
   *
   * @return diff handler or null if unsupported.
   */
  val diffHandler: VcsLogDiffHandler?
    get() = null

  /**
   * Returns [VcsLogFileHistoryHandler] for this provider in order to support Log-based file history.
   *
   * @return file history handler or null if unsupported.
   */
  fun getFileHistoryHandler(project: Project): VcsLogFileHistoryHandler? {
    return VcsLogFileHistoryHandler.getByVcs(project, supportedVcs)
  }

  /**
   * Checks that the given reference points to a valid commit in the given root, and returns the Hash of this commit.
   * Otherwise, if the reference is invalid, returns null.
   */
  fun resolveReference(ref: String, root: VirtualFile): Hash? = null

  /**
   * Returns the VCS root which should be used by the file history instead of the root found by standard mechanism (through mappings).
   */
  fun getVcsRoot(project: Project, detectedRoot: VirtualFile, filePath: FilePath): VirtualFile? = detectedRoot

  interface Requirements {
    /**
     * Returns the number of commits that should be queried from the VCS.
     *
     * (of course it may return fewer commits if the repository is small)
     */
    val commitCount: Int
  }

  /**
   * Container for references and users.
   */
  interface LogData {
    val refs: Set<VcsRef>

    val users: Set<VcsUser>
  }

  /**
   * Container for the ordered list of commits together with their details, and references.
   */
  interface DetailedLogData {
    /**
     * In the case of refresh can contain only 'recent' commits, which will be joined the previously loaded data.
     */
    val commits: List<VcsCommitMetadata>

    /**
     * Should contain all the refs which are related to commits to display in log.
     * It means that in case of refresh all refs should be loaded,
     * while during the initial load refs related to [commits] can be loaded.
     *
     * @see [RefsLoadingPolicy]
     */
    val refs: Set<VcsRef>
  }

  @ApiStatus.Experimental
  sealed interface RefsLoadingPolicy {
    @ApiStatus.Experimental
    object FromLoadedCommits : RefsLoadingPolicy

    @ApiStatus.Experimental
    interface LoadAllRefs : RefsLoadingPolicy {
      val previouslyLoadedRefs: VcsRefsContainer
    }
  }

  companion object {
    @JvmField
    val LOG_PROVIDER_EP: ExtensionPointName<VcsLogProvider> = ExtensionPointName.create("com.intellij.logProvider")
  }
}
