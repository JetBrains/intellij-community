// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.impl.CoreProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.telemetry.VcsBackendTelemetrySpan
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.vcs.impl.shared.telemetry.VcsScope
import com.intellij.util.containers.ContainerUtil
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.SingleTaskController.SingleTask
import com.intellij.vcs.log.data.SingleTaskController.SingleTaskImpl
import com.intellij.vcs.log.data.index.*
import com.intellij.vcs.log.data.util.trace
import com.intellij.vcs.log.impl.VcsLogCachesInvalidator
import com.intellij.vcs.log.impl.VcsLogErrorHandler
import com.intellij.vcs.log.impl.VcsLogStorageLocker
import com.intellij.vcs.log.util.PersistentUtil
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.util.VcsUserUtil.VcsUserHashingStrategy
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.function.Consumer

class VcsLogData(
  val project: Project,
  val logProviders: Map<VirtualFile, VcsLogProvider>,
  private val errorHandler: VcsLogErrorHandler,
  isIndexEnabled: Boolean,
  parentDisposable: Disposable,
) : Disposable, VcsLogDataProvider {
  private val disposableFlag = Disposer.newCheckedDisposable()
  private val lock = Any()

  @ApiStatus.Internal
  val progress: VcsLogProgress = VcsLogProgress(this)

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

  val containingBranchesGetter: ContainingBranchesGetter = ContainingBranchesGetter(this, this)

  @Suppress("unused") // need a hard reference for scope
  private val indexDiagnosticRunner: IndexDiagnosticRunner

  /**
   * Current username, as specified in the VCS settings.
   * It can be configured differently for different roots => store in a map.
   */
  @Volatile
  var currentUser: Map<VirtualFile, VcsUser> = emptyMap()
    private set

  @Volatile
  private var state = State.CREATED

  @Volatile
  private var initializationTask: SingleTask? = null

  private val userRegistry: VcsUserRegistryImpl = project.service<VcsUserRegistry>() as VcsUserRegistryImpl
  val allUsers: Set<VcsUser> get() = userRegistry.users
  val userNameResolver: VcsLogUserResolver = MyVcsLogUserResolver()

  init {
    Disposer.register(parentDisposable, this)
    // storage cannot be opened twice, so we have to wait for other clients (previously opened/closed project) to close it first
    storageLocker.acquireLock(storageId)
    val (storage, index) = try {
      createStorageAndIndex(progress, isIndexEnabled)
    }
    catch (e: Throwable) {
      storageLocker.releaseLock(storageId)
      throw e
    }
    this.storage = storage
    this.index = index

    topCommitsCache = TopCommitsCache(storage)
    miniDetailsGetter = MiniDetailsGetter(project, storage, logProviders, topCommitsCache, index, this)
    commitDetailsGetter = CommitDetailsGetter(storage, logProviders, this)
    indexDiagnosticRunner = IndexDiagnosticRunner(index, storage, logProviders.keys, { dataPack }, commitDetailsGetter, errorHandler, this)

    val commitDataConsumer = VcsLogCommitDataConsumerImpl(userRegistry, index, topCommitsCache)
    refresher = VcsLogRefresherImpl(project, storage, logProviders, progress, commitDataConsumer,
                                    Consumer { fireDataPackChangeEvent(it) },
                                    getRecentCommitsCount())
    Disposer.register(this, refresher)
    Disposer.register(this, Disposable {
      synchronized(lock) {
        initializationTask?.cancel()
      }
    })
    Disposer.register(this, disposableFlag)
  }

  fun initialize() {
    refresher.initialize()
    readCurrentUser()
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
    }, { disposableFlag.isDisposed() })
  }

  override fun dispose() {
    try {
      val initialization: SingleTask?

      synchronized(lock) {
        initialization = initializationTask
        initializationTask = null
        state = State.DISPOSED
      }

      if (initialization != null) {
        initialization.cancel()
        try {
          initialization.waitFor(1, TimeUnit.MINUTES)
        }
        catch (e: InterruptedException) {
          LOG.warn(e)
        }
        catch (e: ExecutionException) {
          LOG.warn(e)
        }
        catch (e: TimeoutException) {
          LOG.warn(e)
        }
      }
      topCommitsCache.clear()

      if (storage is VcsLogStorageImpl && !storage.isDisposed) {
        LOG.error("Storage for \$name was not disposed")
        Disposer.dispose(storage)
      }
    }
    finally {
      storageLocker.releaseLock(storageId)
    }
  }

  private fun readCurrentUser() {
    synchronized(lock) {
      if (state != State.CREATED) {
        return
      }
      state = State.INITIALIZED
      val title = VcsLogBundle.message("vcs.log.initial.reading.current.user.process")
      val backgroundable: Task.Backgroundable = object : Task.Backgroundable(project, title, false) {
        override fun run(indicator: ProgressIndicator) {
          indicator.setIndeterminate(true)
          topCommitsCache.clear()
          currentUser = doReadCurrentUser()
        }

        override fun onCancel() {
          synchronized(lock) {
            // Here be dragons:
            // VcsLogProgressManager can cancel us when it's getting disposed,
            // and we can also get canceled by invalid git executable.
            // Since we do not know what's up, we just restore the state,
            // and it is entirely possible to start another initialization after that.
            // Eventually, everything gets canceled for good in VcsLogData.dispose.
            // But still.
            if (state == State.INITIALIZED) {
              state = State.CREATED
              initializationTask = null
            }
          }
        }

        override fun onThrowable(error: Throwable) {
          synchronized(lock) {
            LOG.error(error)
            if (state == State.INITIALIZED) {
              state = State.CREATED
              initializationTask = null
            }
          }
        }

        override fun onSuccess() {
          synchronized(lock) {
            if (state == State.INITIALIZED) {
              initializationTask = null
            }
          }
        }
      }
      val manager = ProgressManager.getInstance() as CoreProgressManager
      val indicator = progress.createProgressIndicator(DATA_PACK_REFRESH)
      val future = manager.runProcessWithProgressAsynchronously(backgroundable, indicator, null)
      initializationTask = SingleTaskImpl(future, indicator)
    }
  }

  private fun doReadCurrentUser(): Map<VirtualFile, VcsUser> =
    TelemetryManager.getInstance().getTracer(VcsScope).trace(VcsBackendTelemetrySpan.LogData.ReadingCurrentUser) {
      buildMap {
        for ((root, provider) in logProviders.entries) {
          try {
            val user = provider.getCurrentUser(root)
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

  private fun createStorageAndIndex(progress: VcsLogProgress, isIndexEnabled: Boolean): Pair<VcsLogStorage, VcsLogModifiableIndex> {
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
        val sqliteBackend = SqliteVcsLogStorageBackend(project, storageId, logProviders, errorHandler, this)
        storage = sqliteBackend
        indexBackend = sqliteBackend
      }
      else {
        val indexingRoots = if (isIndexSwitchedOn) LinkedHashSet<VirtualFile>(indexers.keys) else emptySet()
        val storageAndIndexBackend = VcsLogStorageImpl.createStorageAndIndexBackend(project, storageId, logProviders, indexingRoots, errorHandler, this)
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

    val index = VcsLogPersistentIndex(project, logProviders, indexers, storage, indexBackend, progress, errorHandler, this)
    return storage to index
  }

  private enum class State {
    CREATED, INITIALIZED, DISPOSED
  }

  private inner class MyVcsLogUserResolver : VcsLogUserResolverBase(), Disposable {
    private val listener = DataPackChangeListener {
      clearCache()
    }

    init {
      addDataPackChangeListener(listener)
      Disposer.register(this@VcsLogData, this)
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
