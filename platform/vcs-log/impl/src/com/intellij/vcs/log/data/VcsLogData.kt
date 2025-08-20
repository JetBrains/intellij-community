// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.checkCanceled
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.util.cancelOnDispose
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.index.*
import com.intellij.vcs.log.data.util.trace
import com.intellij.vcs.log.impl.VcsLogCachesInvalidator
import com.intellij.vcs.log.impl.VcsLogErrorHandler
import com.intellij.vcs.log.impl.VcsLogStorageLocker
import com.intellij.vcs.log.util.PersistentUtil
import com.intellij.vcs.log.util.StorageId
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.VcsUserUtil.VcsUserHashingStrategy
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

class VcsLogData @ApiStatus.Internal constructor(
  val project: Project,
  parentCs: CoroutineScope,
  val logProviders: Map<VirtualFile, VcsLogProvider>,
  private val errorHandler: VcsLogErrorHandler,
  isIndexEnabled: Boolean,
) : VcsLogDataProvider {
  private val cs = parentCs.childScope("Vcs Log Data")
  val isDisposed: Boolean get() = !cs.isActive

  @ApiStatus.Internal
  val progress: VcsLogProgress

  private val storageLocker: VcsLogStorageLocker = VcsLogStorageLocker.getInstance()
  private val storageId: String = PersistentUtil.calcLogId(project, logProviders)
  val storage: VcsLogStorage
  val index: VcsLogIndex

  /**
   * Cached details of the latest commits.
   * We store them separately from the cache of [DataGetter], to make sure that they are always available,
   * which is important because these details will be constantly visible to the user,
   * thus it would be annoying to re-load them from VCS if the cache overflows.
   */
  val topCommitsCache: TopCommitsCache

  val miniDetailsGetter: MiniDetailsGetter
  override val fullCommitDetailsCache: VcsLogCommitDataCache<VcsFullCommitDetails> get() = commitDetailsGetter

  val commitDetailsGetter: CommitDetailsGetter
  override val commitMetadataCache: VcsLogCommitDataCache<VcsCommitMetadata> get() = miniDetailsGetter

  private val refresher: VcsLogRefresherImpl
  val dataPack: DataPack get() = refresher.currentDataPack
  private val dataPackChangeListeners = ContainerUtil.createLockFreeCopyOnWriteList<DataPackChangeListener>()

  val containingBranchesGetter: ContainingBranchesGetter

  /**
   * Current username, as specified in the VCS settings.
   * It can be configured differently for different roots => store in a map.
   */
  @Volatile
  var currentUser: Map<VirtualFile, VcsUser> = emptyMap()
    private set
  private val initRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val userRegistry: VcsUserRegistryImpl = project.service<VcsUserRegistry>() as VcsUserRegistryImpl
  val allUsers: Set<VcsUser> get() = userRegistry.users
  val userNameResolver: VcsLogUserResolver

  init {
    val dataDisposable = Disposer.newDisposable("Vcs Log Data Disposable for ${logProviders}")
    val dataDisposableJob = cs.launch(CoroutineName("Data disposer"), start = CoroutineStart.UNDISPATCHED) {
      val dataDisposalStarted = AtomicBoolean(false)
      val shutdownTask = Runnable {
        if (!dataDisposalStarted.compareAndSet(false, true)) {
          LOG.warn("unregisterShutdownTask should be called")
          return@Runnable
        }

        LOG.info("Disposing log data on app shutdown")
        // Forcibly dispose the storage data holders to flush the data on disk.
        Disposer.dispose(dataDisposable)
      }

      try {
        ShutDownTracker.getInstance().registerShutdownTask(shutdownTask)
        awaitCancellation()
      }
      finally {
        ShutDownTracker.getInstance().unregisterShutdownTask(shutdownTask)
        if (dataDisposalStarted.compareAndSet(false, true)) {
          // disposing storage triggers flushing on disk
          withContext(NonCancellable + Dispatchers.IO) {
            LOG.info("Disposing log data")
            Disposer.dispose(dataDisposable)

            yield() // give constructor a chance to complete
            if (storage is VcsLogStorageImpl && !storage.isDisposed) {
              LOG.error("Storage for $logProviders was not disposed")
              Disposer.dispose(storage)
            }
          }
        }
      }
    }.apply {
      cancelOnDispose(dataDisposable)
    }

    progress = VcsLogProgress(dataDisposable)
    // storage cannot be opened twice, so we have to wait for other clients (previously opened/closed project) to close it first
    storageLocker.acquireLock(storageId)
    val (storage, index) = try {
      createStorageAndIndex(progress, isIndexEnabled, dataDisposable)
    }
    catch (e: Throwable) {
      storageLocker.releaseLock(storageId)
      throw e
    }
    this.storage = storage
    this.index = index

    topCommitsCache = TopCommitsCache(storage)
    miniDetailsGetter = MiniDetailsGetter(project, storage, logProviders, topCommitsCache, index, dataDisposable)
    commitDetailsGetter = CommitDetailsGetter(storage, logProviders, dataDisposable)

    val commitDataConsumer = VcsLogCommitDataConsumerImpl(userRegistry, index, topCommitsCache)
    refresher = VcsLogRefresherImpl(cs, storage, logProviders, progress, commitDataConsumer,
                                    Consumer { fireDataPackChangeEvent(it) },
                                    getRecentCommitsCount())

    userNameResolver = MyVcsLogUserResolver()
    Disposer.register(dataDisposable, userNameResolver)

    containingBranchesGetter = ContainingBranchesGetter(this, dataDisposable)

    val initJob = cs.launch(CoroutineName("Init")) {
      initRequests.collect {
        runCatching {
          val usersByRoot = progress.runWithProgress(DATA_PACK_REFRESH) {
            checkCanceled()
            topCommitsCache.clear() // TODO: is it thread safe at all?
            readCurrentUser()
          }
          currentUser = usersByRoot
          currentCoroutineContext().cancel()
        }.getOrLogException {
          LOG.error(it)
        }
      }
    }

    val indexDiagnosticJob = cs.launch {
      IndexDiagnosticRunner(this, index, storage, logProviders.keys, { dataPack }, commitDetailsGetter, errorHandler, this@VcsLogData)
    }

    cs.launch(CoroutineName("Disposer"), CoroutineStart.ATOMIC) {
      try {
        awaitCancellation()
      }
      finally {
        try {
          withContext(NonCancellable) {
            initJob.joinWithTimeout(1.minutes) { LOG.warn("Init job shutdown timed out") }
            topCommitsCache.clear()
            indexDiagnosticJob.joinWithTimeout(10.milliseconds) { LOG.warn("Index diagnostic shutdown timed out") }

            dataDisposableJob.join()
          }
        }
        finally {
          storageLocker.releaseLock(storageId)
        }
      }
    }
  }

  @ApiStatus.Internal
  fun initialize() {
    refresher.initialize()
    initRequests.tryEmit(Unit)
  }

  /**
   * Makes the log perform refresh for the given root.
   * This refresh can be optimized, i.e., it can query VCS just for the part of the log.
   *
   * @param optimized - if request should be optimized see [VcsLogRefresher.refresh]
   */
  @JvmOverloads
  fun refresh(roots: Collection<VirtualFile>, optimized: Boolean = false) {
    initialize()
    refresher.refresh(roots, optimized)
  }

  /**
   * @return true if only one user commits to this repository
   */
  val isSingleUser: Boolean
    get() {
      val users = ObjectOpenCustomHashSet(currentUser.values, VcsUserHashingStrategy())
      return userRegistry.all { user -> users.contains(user) }
    }

  override fun getCommitId(commitIndex: Int): CommitId? {
    val cachedData = miniDetailsGetter.getCachedData(commitIndex)
    if (cachedData != null) {
      return CommitId(cachedData.getId(), cachedData.getRoot())
    }
    return storage.getCommitId(commitIndex)
  }

  override fun getCommitIndex(hash: Hash, root: VirtualFile): Int {
    return storage.getCommitIndex(hash, root)
  }

  fun addDataPackChangeListener(listener: DataPackChangeListener) {
    dataPackChangeListeners.add(listener)
  }

  fun removeDataPackChangeListener(listener: DataPackChangeListener) {
    dataPackChangeListeners.remove(listener)
  }

  private fun fireDataPackChangeEvent(dataPack: DataPack) {
    ApplicationManager.getApplication().invokeLater(Runnable {
      for (listener in dataPackChangeListeners) {
        if (LOG.isDebugEnabled()) {
          LOG.debug("Starting data pack change listener $listener")
        }
        listener.onDataPackChange(dataPack)
        if (LOG.isDebugEnabled()) {
          LOG.debug("Finished data pack change listener $listener")
        }
      }
    }, { isDisposed })
  }

  private suspend fun readCurrentUser(): Map<VirtualFile, VcsUser> =
    TelemetryManager.getInstance().getTracer(VcsScope).trace(VcsBackendTelemetrySpan.LogData.ReadingCurrentUser) {
      buildMap {
        for ((root, provider) in logProviders.entries) {
          checkCanceled()
          try {
            val user = withContext(Dispatchers.IO) {
              coroutineToIndicator {
                provider.getCurrentUser(root)
              }
            }
            if (user == null) {
              LOG.info("Username not configured for root $root")
              continue
            }
            put(root, user)
          }
          catch (e: VcsException) {
            LOG.warn("Couldn't read the username from root $root", e)
          }
        }
      }
    }

  private fun createStorageAndIndex(
    progress: VcsLogProgress,
    isIndexEnabled: Boolean,
    dataDisposable: Disposable,
  ): Pair<VcsLogStorage, VcsLogModifiableIndex> {
    if (!VcsLogCachesInvalidator.getInstance().isValid()) {
      // this is not recoverable
      // restart won't help here
      // and cannot shut down ide because of this
      // so use memory storage (probably leading to out of memory at some point) + no index

      LOG.error("Could not delete caches at " + PersistentUtil.LOG_CACHE)
      errorHandler.displayMessage(VcsLogBundle.message("vcs.log.fatal.error.message", PersistentUtil.LOG_CACHE,
                                                       ApplicationNamesInfo.getInstance().fullProductName))
      return InMemoryStorage() to EmptyIndex()
    }

    val indexers = VcsLogPersistentIndex.getAvailableIndexers(logProviders)
    val isIndexSwitchedOnInRegistry = isIndexSwitchedOnInRegistry()
    val isIndexSwitchedOn = isIndexEnabled && isIndexSwitchedOnInRegistry

    val storage: VcsLogStorage
    val indexBackend: VcsLogStorageBackend?
    try {
      if (`is`("vcs.log.index.sqlite.storage", false)) {
        val sqliteBackend = SqliteVcsLogStorageBackend(project, storageId, logProviders, errorHandler, dataDisposable)
        storage = sqliteBackend
        indexBackend = sqliteBackend
      }
      else {
        val indexingRoots = if (isIndexSwitchedOn) LinkedHashSet<VirtualFile>(indexers.keys) else emptySet()
        val storageAndIndexBackend = VcsLogStorageImpl.createStorageAndIndexBackend(project, storageId, logProviders, indexingRoots, errorHandler, dataDisposable)
        storage = storageAndIndexBackend.first
        indexBackend = storageAndIndexBackend.second
      }
    }
    catch (e: IOException) {
      LOG.error("Falling back to in-memory hashes", e)
      return InMemoryStorage() to EmptyIndex()
    }

    if (indexBackend == null || !isIndexSwitchedOn || indexers.isEmpty()) {
      if (!isIndexSwitchedOnInRegistry) {
        LOG.info("Vcs log index is turned off in the registry")
      }
      if (!isIndexEnabled) {
        LOG.info("Vcs log index is turned off for " + VcsLogUtil.getProvidersMapText(logProviders))
      }
      if (indexers.isEmpty()) {
        LOG.info("No indexers found for project " + project.getName())
      }
      return storage to EmptyIndex()
    }

    val index = VcsLogPersistentIndex(project, logProviders, indexers, storage, indexBackend, progress, errorHandler, dataDisposable)
    return storage to index
  }

  internal val hasPersistentStorage: Boolean
    get() = collectStorageIds().isNotEmpty()

  internal suspend fun clearPersistentStorage() {
    require(isDisposed) { "Cannot clear persistent storage of a non-disposed data manager" }
    cs.coroutineContext.job.cancelAndJoin()
    storageLocker.acquireLock(storageId)
    try {
      val storageIds = collectStorageIds()
      withContext(Dispatchers.IO) {
        VcsLogStorageImpl.cleanupStorageFiles(storageIds)
      }
    }
    finally {
      storageLocker.releaseLock(storageId)
    }
  }

  private fun collectStorageIds(): List<StorageId> =
    linkedSetOf((index as? VcsLogPersistentIndex)?.indexStorageId,
                (storage as? VcsLogStorageImpl)?.refsStorageId,
                (storage as? VcsLogStorageImpl)?.hashesStorageId)
      .filterNotNull()

  private inner class MyVcsLogUserResolver : VcsLogUserResolverBase(), Disposable {
    private val listener = DataPackChangeListener {
      clearCache()
    }

    init {
      addDataPackChangeListener(listener)
    }

    override val currentUsers: Map<VirtualFile, VcsUser>
      get() = this@VcsLogData.currentUser

    override val allUsers: Set<VcsUser>
      get() = this@VcsLogData.allUsers

    override fun dispose() {
      removeDataPackChangeListener(listener)
    }
  }

  companion object {
    private val LOG = logger<VcsLogData>()

    @JvmField
    @ApiStatus.Internal
    val DATA_PACK_REFRESH: VcsLogProgress.ProgressKey = VcsLogProgress.ProgressKey("data pack")

    @JvmStatic
    fun isIndexSwitchedOnInRegistry(): Boolean = getIndexingRegistryValue().asBoolean()

    @JvmStatic
    fun getIndexingRegistryValue(): RegistryValue = Registry.get("vcs.log.index.enable")

    @JvmStatic
    fun getRecentCommitsCount(): Int = Registry.intValue("vcs.log.recent.commits.count")
  }
}

val VcsLogData.roots: Collection<VirtualFile> get() = logProviders.keys

fun VcsLogData.getLogProvider(root: VirtualFile): VcsLogProvider {
  return logProviders[root] ?: error("Unknown root $root")
}

private suspend fun Job.joinWithTimeout(timeout: Duration, onTimeout: (e: TimeoutCancellationException) -> Unit) {
  try {
    withTimeout(timeout) {
      join()
    }
  }
  catch (e: TimeoutCancellationException) {
    onTimeout(e)
  }
}
