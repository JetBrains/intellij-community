// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.ignore.IgnoredToExcludedSynchronizer
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.InitialVfsRefreshService
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.VcsManagedFilesHolder
import com.intellij.openapi.vcs.util.paths.RecursiveFilePathSet
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitContentRevision
import git4idea.GitRefreshUsageCollector.logUntrackedRefresh
import git4idea.ignore.GitRepositoryIgnoredFilesHolder
import git4idea.index.LightFileStatus.StatusRecord
import git4idea.index.getFileStatus
import git4idea.index.isIgnored
import git4idea.index.isUntracked
import git4idea.status.GitRefreshListener
import git4idea.util.DebouncedTaskRunner
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.CancellationException
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

class GitUntrackedFilesHolder internal constructor(
  parentCs: CoroutineScope,
  private val repository: GitRepository,
) {
  private val cs = parentCs.childScope(::javaClass.name)

  private val project: Project = repository.getProject()
  private val repoRoot: VirtualFile = repository.getRoot()

  private val dirtyFiles = HashSet<FilePath>()
  @Volatile
  private var isEverythingDirty = true

  @Volatile
  var isInUpdateMode: Boolean = false
    private set

  private val untrackedFiles: CopyOnWriteFilePathSet = CopyOnWriteFilePathSet(repoRoot.isCaseSensitive)
  val untrackedFilePaths: Collection<FilePath> get() = untrackedFiles.toSet()

  private val _ignoredFilesHolder = MyGitRepositoryIgnoredFilesHolder()
  val ignoredFilesHolder: GitRepositoryIgnoredFilesHolder get() = _ignoredFilesHolder

  private val updateRunner: DebouncedTaskRunner
  private val LOCK = Any()

  @get:ApiStatus.Internal
  val isInitialized: Boolean
    get() = untrackedFiles.initialized

  init {
    updateRunner = DebouncedTaskRunner(cs, 500.milliseconds, ::update)
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      try {
        project.serviceAsync<InitialVfsRefreshService>().awaitInitialVfsRefreshFinished()
        updateRunner.start()
        scheduleUpdate()
        awaitCancellation()
      }
      finally {
        clear()
      }
    }
  }

  private fun clear() {
    synchronized(LOCK) {
      untrackedFiles.clear()
      _ignoredFilesHolder.clear()
      dirtyFiles.clear()
    }
  }

  /**
   * Adds the file to the list of untracked.
   */
  fun addUntracked(file: FilePath) {
    addUntracked(listOf(file))
  }

  /**
   * Adds several files to the list of untracked.
   */
  fun addUntracked(files: Collection<FilePath>) {
    synchronized(LOCK) {
      if (untrackedFiles.initialized) {
        untrackedFiles.add(files)
      }
      if (!isEverythingDirty) dirtyFiles.addAll(files)
    }
    ChangeListManagerImpl.getInstanceImpl(project).notifyUnchangedFileStatusChanged()
    scheduleUpdate()
  }

  /**
   * Removes several files from untracked.
   */
  fun removeUntracked(files: Collection<FilePath>) {
    synchronized(LOCK) {
      if (untrackedFiles.initialized) {
        untrackedFiles.remove(files)
      }
      if (!isEverythingDirty) dirtyFiles.addAll(files)
    }
    ChangeListManagerImpl.getInstanceImpl(project).notifyUnchangedFileStatusChanged()
    scheduleUpdate()
  }

  /**
   * Marks files as possibly untracked to be checked on the next [.update] call.
   *
   * @param files files that are possibly untracked.
   */
  fun markPossiblyUntracked(files: Collection<FilePath>) {
    synchronized(LOCK) {
      if (isEverythingDirty) return
      val ignoredFiles = _ignoredFilesHolder.ignoredFiles
      for (filePath in files) {
        if (ignoredFiles.containsExplicitly(filePath) || !ignoredFiles.hasAncestor(filePath)) {
          dirtyFiles.add(filePath)
        }
      }
    }
    scheduleUpdate()
  }

  fun invalidate() {
    synchronized(LOCK) {
      isEverythingDirty = true
      dirtyFiles.clear()
    }
    scheduleUpdate()
  }

  /**
   * Returns the list of unversioned files.
   * This method may be slow, if the full-refresh of untracked files is needed.
   *
   * @return untracked files.
   * @throws VcsException if there is an unexpected error during Git execution.
   */
  @Deprecated("use {@link #retrieveUntrackedFilePaths} instead")
  @Throws(VcsException::class)
  fun retrieveUntrackedFiles(): Collection<VirtualFile?> = retrieveUntrackedFilePaths().mapNotNull { it.getVirtualFile() }

  @Throws(VcsException::class)
  fun retrieveUntrackedFilePaths(): Collection<FilePath> {
    runBlockingMaybeCancellable {
      updateRunner.awaitNotBusy()
    }
    return untrackedFilePaths
  }

  fun containsUntrackedFile(filePath: FilePath): Boolean {
    return untrackedFiles.hasAncestor(filePath)
  }

  private val isDirty: Boolean
    get() = synchronized(LOCK) {
      isEverythingDirty || !dirtyFiles.isEmpty()
    }

  private fun scheduleUpdate() {
    synchronized(LOCK) {
      if (!isDirty) return
      isInUpdateMode = true
    }
    BackgroundTaskUtil.syncPublisher(project, VcsManagedFilesHolder.TOPIC).updatingModeChanged()
    updateRunner.request()
  }

  private suspend fun update() {
    // do not cancel the processing on failure
    try {
      doUpdate()
    }
    catch (@Suppress("IncorrectCancellationExceptionHandling") _: CancellationException) {
      checkCanceled()
    }
    catch (e: Exception) {
      LOG.error("Update failed", e)
    }
  }

  /**
   * Queries Git to check the status of [dirtyFiles] and moves them to [untrackedFiles].
   */
  private suspend fun doUpdate() {
    val dirtyScope = acquireDirt()
    if (dirtyScope == null) {
      BackgroundTaskUtil.syncPublisher(project, VcsManagedFilesHolder.TOPIC).updatingModeChanged()
      return
    }

    BackgroundTaskUtil.syncPublisher(project, GitRefreshListener.TOPIC).progressStarted()
    try {
      val activity = logUntrackedRefresh(project, dirtyScope == DirtyScope.Everything)
      val (untracked, ignored) = refreshFiles(dirtyScope)
      activity.finished()

      val filteredUntracked = removePathsUnderOtherRoots(untracked, "unversioned")
      val filteredIgnored = removePathsUnderOtherRoots(ignored, "ignored")

      val (oldIgnored, newIgnored) = applyRefreshResult(filteredUntracked, filteredIgnored, dirtyScope)

      BackgroundTaskUtil.syncPublisher(project, GitRefreshListener.TOPIC).repositoryUpdated(repository)
      BackgroundTaskUtil.syncPublisher(project, VcsManagedFilesHolder.TOPIC).updatingModeChanged()
      ChangeListManagerImpl.getInstanceImpl(project).notifyUnchangedFileStatusChanged()

      project.service<IgnoredToExcludedSynchronizer>().onIgnoredFilesUpdate(newIgnored, oldIgnored)
    }
    finally {
      BackgroundTaskUtil.syncPublisher(project, GitRefreshListener.TOPIC).progressStopped()
    }
  }

  private fun acquireDirt(): DirtyScope? {
    return synchronized(LOCK) {
      try {
        when {
          isEverythingDirty || dirtyFiles.contains(VcsUtil.getFilePath(repoRoot)) -> {
            DirtyScope.Everything
          }
          dirtyFiles.isNotEmpty() -> {
            DirtyScope.Files(dirtyFiles.toList())
          }
          else -> {
            isInUpdateMode = false
            null
          }
        }
      }
      finally {
        dirtyFiles.clear()
        isEverythingDirty = false
      }
    }
  }

  private fun applyRefreshResult(
    untracked: Set<FilePath>,
    ignored: Set<FilePath>,
    dirtyScope: DirtyScope,
  ): UpdatedValue<Set<FilePath>> {
    synchronized(LOCK) {
      val oldIgnored = _ignoredFilesHolder.ignoredFilePaths

      val caseSensitive = repoRoot.isCaseSensitive
      val newIgnored = RecursiveFilePathSet(caseSensitive)
      val newUntracked = RecursiveFilePathSet(caseSensitive)

      when (dirtyScope) {
        DirtyScope.Everything -> {
          newUntracked.addAll(untracked)
          newIgnored.addAll(ignored)
        }
        is DirtyScope.Files -> {
          val dirtyFiles = RecursiveFilePathSet(caseSensitive).apply {
            addAll(dirtyScope.files)
          }
          val untrackedSet = untrackedFiles.toSet()
          untrackedSet.removeIf { dirtyFiles.hasAncestor(it) }
          untrackedSet.addAll(untracked)
          newUntracked.addAll(untrackedSet)

          for (filePath in oldIgnored) {
            if (!dirtyFiles.hasAncestor(filePath)) {
              newIgnored.add(filePath)
            }
          }
          for (filePath in ignored) {
            if (!newIgnored.hasAncestor(filePath)) { // prevent storing both parent and child directories
              newIgnored.add(filePath)
            }
          }
        }
      }

      _ignoredFilesHolder.ignoredFiles.set(newIgnored)
      untrackedFiles.set(newUntracked)
      isInUpdateMode = isDirty
      return UpdatedValue(oldIgnored, _ignoredFilesHolder.ignoredFilePaths)
    }
  }

  /**
   * @see git4idea.status.GitStagingAreaHolder.removeUnwantedRecords
   */
  private fun removePathsUnderOtherRoots(untrackedFiles: Collection<FilePath>, type: @NonNls String?): Set<FilePath> {
    val vcsManager = ProjectLevelVcsManager.getInstance(project)

    var removedFiles = 0
    val maxFilesToReport = 10

    val filtered = untrackedFiles.filterTo(mutableSetOf()) { filePath ->
      val root = vcsManager.getVcsRootFor(filePath)
      val sameRoot = root == repoRoot

      if (!sameRoot) {
        removedFiles++
        if (removedFiles < maxFilesToReport) {
          LOG.debug(String.format("Ignoring %s file under another root: %s; root: %s; mapped root: %s",
                                  type, filePath.presentableUrl, repoRoot.presentableUrl, root?.presentableUrl ?: "null"))
        }
      }

      sameRoot
    }

    if (removedFiles >= maxFilesToReport) {
      LOG.debug(String.format("Ignoring %s files under another root: %s files total", type, removedFiles))
    }
    return filtered
  }


  private suspend fun refreshFiles(dirtyScope: DirtyScope): RefreshResult {
    try {
      val withIgnored = AdvancedSettings.getBoolean("vcs.process.ignored")
      val fileStatuses = withContext(Dispatchers.IO) {
        val dirtyFiles = when (dirtyScope) {
          DirtyScope.Everything -> emptyList()
          is DirtyScope.Files -> dirtyScope.files
        }
        coroutineToIndicator {
          getFileStatus(project, repoRoot, dirtyFiles, false, true, withIgnored)
        }
      }

      val untracked = HashSet<FilePath>()
      val ignored = HashSet<FilePath>()
      for (status in fileStatuses) {
        if (isUntracked(status.index)) {
          untracked.add(getFilePath(repoRoot, status))
        }
        if (isIgnored(status.index)) {
          ignored.add(getFilePath(repoRoot, status))
        }
      }
      return RefreshResult(untracked, ignored)
    }
    catch (e: VcsException) {
      LOG.warn(e)
      return RefreshResult()
    }
  }

  private inner class MyGitRepositoryIgnoredFilesHolder : GitRepositoryIgnoredFilesHolder() {
    val ignoredFiles = CopyOnWriteFilePathSet(repoRoot.isCaseSensitive)

    override val initialized: Boolean
      get() = ignoredFiles.initialized

    override val ignoredFilePaths: Set<FilePath>
      get() = ignoredFiles.toSet()

    override fun isInUpdateMode(): Boolean {
      return this@GitUntrackedFilesHolder.isInUpdateMode
    }

    override fun containsFile(file: FilePath): Boolean {
      return ignoredFiles.hasAncestor(file)
    }

    override fun removeIgnoredFiles(filePaths: Collection<FilePath>) {
      synchronized(LOCK) {
        if (ignoredFiles.initialized) {
          ignoredFiles.remove(filePaths)
        }
        if (!isEverythingDirty) {
          // break parent ignored directory into separate ignored files
          if (filePaths.any { ignoredFiles.hasAncestor(it) }) {
            isEverythingDirty = true
          }
          else {
            dirtyFiles.addAll(filePaths)
          }
        }
      }
      ChangeListManagerImpl.getInstanceImpl(project).notifyUnchangedFileStatusChanged()
    }

    fun clear() {
      ignoredFiles.clear()
    }
  }

  @ApiStatus.Internal
  @TestOnly
  suspend fun awaitNotBusy() {
    withTimeout(WAITING_TIMEOUT_MS.milliseconds) {
      updateRunner.awaitNotBusy()
    }
  }

  companion object {
    private val LOG = Logger.getInstance(GitUntrackedFilesHolder::class.java)

    private const val WAITING_TIMEOUT_MS = 10000

    private fun getFilePath(root: VirtualFile, status: StatusRecord): FilePath {
      val path = status.path
      return GitContentRevision.createPath(root, path, path.endsWith("/"))
    }
  }
}

private sealed interface DirtyScope {
  class Files(val files: List<FilePath>) : DirtyScope
  object Everything : DirtyScope
}

private data class RefreshResult(
  val untracked: Set<FilePath> = emptySet(),
  val ignored: Set<FilePath> = emptySet(),
)

private data class UpdatedValue<T>(val old: T, val new: T)
