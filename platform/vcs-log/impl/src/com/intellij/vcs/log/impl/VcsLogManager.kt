// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.objectTree.ThrowableInterner
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ComponentUtil
import com.intellij.util.BitUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.MultiMap
import com.intellij.util.ui.RawSwingDispatcher
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.DataPackChangeListener
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogStatusBarProgress
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.statistics.VcsLogUsageTriggerCollector
import com.intellij.vcs.log.ui.*
import com.intellij.vcs.log.visible.VcsLogFiltererImpl
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.collection
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus.*
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

@NonExtendable
open class VcsLogManager @Internal constructor(
  @Internal protected val project: Project,
  val uiProperties: VcsLogTabsProperties,
  logProviders: Map<VirtualFile, VcsLogProvider>,
  @Internal val name: String,
  isIndexEnabled: Boolean,
  private val errorHandler: ((VcsLogErrorHandler.Source, Throwable) -> Unit)?,
) {
  private val dataDisposable = Disposer.newDisposable("Vcs Log Data Disposable for $name")
  val dataManager: VcsLogData = VcsLogData(project, logProviders, MyErrorHandler(), isIndexEnabled, dataDisposable)
  private val postponableRefresher = PostponableLogRefresher(dataManager)

  private val managedUis = mutableMapOf<String, VcsLogUiEx>()
  private val uiRegistrationTraces = mutableMapOf<String, Throwable>()

  val colorManager: VcsLogColorManager = VcsLogColorManagerFactory.create(logProviders.keys)
  private val statusBarProgress = VcsLogStatusBarProgress(project, logProviders, dataManager.index.indexingRoots, dataManager.progress)

  private val disposed = AtomicBoolean(false)
  val isDisposed: Boolean get() = disposed.get()

  init {
    refreshLogOnVcsEvents(dataManager, logProviders, postponableRefresher)
  }

  private val isLogVisible: Boolean
    get() = managedUis.any { it.value.isVisible() }

  /**
   * If this Log has a full data pack and there are no postponed refreshes. Does not check if there are refreshes in progress.
   */
  @get:RequiresEdt
  val isLogUpToDate: Boolean
    get() = dataManager.dataPack.isFull && !postponableRefresher.hasPostponedRoots()

  @Internal
  @CalledInAny
  fun initialize() {
    dataManager.initialize()
  }

  @Internal
  @CalledInAny
  fun initializeIfNeeded() {
    if (isLogVisible) {
      dataManager.initialize()
    }
  }

  /**
   * Schedules Log initialization and update even when none of the log tabs is visible and a power save mode is enabled.
   */
  @Internal
  @RequiresEdt
  fun scheduleUpdate() {
    initialize()
    postponableRefresher.refreshPostponedRoots()
  }

  @Internal
  fun generateNewLogId(): String {
    val existingIds = managedUis.keys
    var newId: String
    do {
      newId = UUID.randomUUID().toString()
    }
    while (existingIds.contains(newId))
    return newId
  }

  @Internal
  fun createLogUi(logId: String, filters: VcsLogFilterCollection?): MainVcsLogUi {
    return createLogUi(getMainLogUiFactory(logId, filters))
  }

  @Internal
  protected fun getMainLogUiFactory(logId: String, filters: VcsLogFilterCollection?): VcsLogUiFactory<out MainVcsLogUi> {
    val factoryProvider = CustomVcsLogUiFactoryProvider.LOG_CUSTOM_UI_FACTORY_PROVIDER_EP.findFirstSafe(project) {
      it.isActive(dataManager.logProviders)
    }
    if (factoryProvider == null) {
      return MainVcsLogUiFactory(logId, filters, uiProperties, colorManager)
    }
    return factoryProvider.createLogUiFactory(logId, this, filters)
  }

  @Internal
  fun <U : VcsLogUiEx> createLogUi(factory: VcsLogUiFactory<U>): U {
    ThreadingAssertions.assertEventDispatchThread()
    if (isDisposed) {
      LOG.error("Trying to create new VcsLogUi on a disposed VcsLogManager instance")
      throw ProcessCanceledException()
    }

    val ui = factory.createLogUi(project, dataManager)
    val id = ui.id
    if (managedUis.contains(id)) {
      Disposer.dispose(ui)
      throw CannotAddVcsLogWindowException("Log ui with id '" + id + "' was already added. " +
                                           "Existing uis:\n" + getLogUiInformation(),
                                           uiRegistrationTraces[id])
    }
    managedUis[id] = ui
    uiRegistrationTraces[id] = Throwable("Creation trace for $ui")

    installRefresher(ui)

    Disposer.register(ui) {
      LOG.debug("Removing disposed log ui $ui")
      managedUis.remove(id)
      uiRegistrationTraces.remove(id)
    }
    return ui
  }

  private fun installRefresher(ui: VcsLogUiEx) {
    postponableRefresher.registerRefresher(ui, ui.id, object : PostponableLogRefresher.Refresher {
      override fun setDataPack(dataPack: DataPack) {
        LOG.debug("Refreshing log window ${ui}")
        ui.refresher.setDataPack(ui.isVisible(), dataPack)
      }

      override fun validate(refresh: Boolean) {
        ui.refresher.setValid(true, refresh)
      }
    })

    val listener = object : HierarchyListener {
      private var wasVisible = ui.isVisible()

      override fun hierarchyChanged(e: HierarchyEvent) {
        if (BitUtil.isSet(e.changeFlags, HierarchyEvent.SHOWING_CHANGED.toLong())) {
          val nowVisible = ui.isVisible()
          if (nowVisible && !wasVisible) {
            VcsLogUsageTriggerCollector.triggerTabNavigated(project)
            LOG.debug("Activated log ui '$ui'")
            postponableRefresher.refresherActivated(ui.id)
          }
          wasVisible = nowVisible
        }
      }
    }
    ui.mainComponent.addHierarchyListener(listener)
    Disposer.register(ui) {
      ui.mainComponent.removeHierarchyListener(listener)
    }
  }

  /**
   * Returns the list of all Log UIs managed by this manager
   */
  open fun getLogUis(): List<VcsLogUi> {
    return managedUis.values.toList()
  }

  @Internal
  fun getLogUiInformation(): String =
    managedUis.values.joinToString("\n") { ui ->
      val isVisible = if (ui.isVisible()) " (visible)" else ""
      val isDisposed = if (Disposer.isDisposed(ui.refresher)) " (disposed)" else ""
      ui.toString() + isVisible + isDisposed
  }

  @Internal
  @RequiresEdt
  protected open fun disposeUi() {
    ThreadingAssertions.assertEventDispatchThread()
    managedUis.values.toList().forEach { Disposer.dispose(it) }
    Disposer.dispose(statusBarProgress)
  }

  /**
   * Dispose VcsLogManager and execute some activity after it.
   * Obsolete in favor of suspending [dispose].
   *
   * @param callback activity to run after log is disposed. Is executed in background thread. null means execution of additional activity after disposing is not required.
   */
  @Obsolete
  @Internal
  @RequiresEdt
  fun dispose(callback: Runnable?) {
    if (!startDisposing()) return
    disposeUi()
    ApplicationManager.getApplication().executeOnPooledThread {
      disposeData()
      callback?.run()
    }
  }

  @Internal
  @RequiresBackgroundThread
  private fun disposeData() {
    // since disposing log triggers flushing indexes on disk we do not want to do it in EDT
    // disposing of VcsLogManager is done by manually executing dispose(@Nullable Runnable callback)
    // the above method first disposes ui in EDT, then disposes everything else in a background
    ThreadingAssertions.assertBackgroundThread()
    Disposer.dispose(dataDisposable)
    LOG.debug("Disposed $name")
  }

  private fun startDisposing(): Boolean {
    val wasNotStartedBefore = disposed.compareAndSet(false, true)
    if (!wasNotStartedBefore) {
      LOG.warn("$name is already disposed. Ignoring dispose request", Throwable("Dispose trace for $name"))
      return false
    }
    return true
  }

  /**
   * Release all resources associated with the manager
   *
   * @param useRawSwingDispatcher on app shutdown the proper EDT dispatcher might not be available
   * @param clearStorage clear the persistent storage (indexes and stuff)
   */
  @Internal
  suspend fun dispose(useRawSwingDispatcher: Boolean = false, clearStorage: Boolean = false) {
    if (!startDisposing()) return
    val uiDispatcher = if (useRawSwingDispatcher) RawSwingDispatcher else Dispatchers.EDT
    withContext(uiDispatcher) {
      disposeUi()
    }
    withContext(Dispatchers.Default) {
      val storageToClear = if (clearStorage) storageIds() else emptyList()
      disposeData()

      for (storageId in storageToClear) {
        try {
          val deleted = withContext(Dispatchers.IO) { storageId.cleanupAllStorageFiles() }
          if (deleted) {
            LOG.info("Deleted ${storageId.storagePath}")
          }
          else {
            LOG.error("Could not delete ${storageId.storagePath}")
          }
        }
        catch (t: Throwable) {
          LOG.error(t)
        }
      }
    }
  }

  private fun refreshLogOnVcsEvents(
    disposableParent: Disposable,
    logProviders: Map<VirtualFile, VcsLogProvider>,
    refresher: PostponableLogRefresher,
  ) {
    val providers2roots = MultiMap.create<VcsLogProvider, VirtualFile>()
    logProviders.forEach { (key, value) -> providers2roots.putValue(value, key) }

    val wrappedRefresher = VcsLogRefresher { root ->
      ApplicationManager.getApplication().invokeLater({
                                                        refresher.refresh(root, !(keepUpToDate() || isLogVisible))
                                                      }, ModalityState.any())
    }
    for ((key, value) in providers2roots.entrySet()) {
      val disposable = key.subscribeToRootRefreshEvents(value, wrappedRefresher)
      Disposer.register(disposableParent, disposable)
    }
  }

  private inner class MyErrorHandler : VcsLogErrorHandler {
    private val myErrors: IntSet = IntSets.synchronize(IntOpenHashSet())
    private val myIsBroken = AtomicBoolean(false)

    override fun handleError(source: VcsLogErrorHandler.Source, throwable: Throwable) {
      if (myIsBroken.compareAndSet(false, true)) {
        if (errorHandler != null) {
          errorHandler.invoke(source, throwable)
        }
        else {
          LOG.error("Vcs Log exception from $source", throwable)
        }

        if (source == VcsLogErrorHandler.Source.Storage) {
          (dataManager.index as VcsLogModifiableIndex).markCorrupted()
        }
      }
      else {
        val errorHashCode = ThrowableInterner.computeTraceHashCode(throwable)
        if (myErrors.add(errorHashCode)) {
          LOG.debug("Vcs Log storage is broken and is being recreated", throwable)
        }
      }
    }

    override fun displayMessage(message: @Nls String) {
      VcsNotifier.getInstance(project).notifyError(VcsLogNotificationIdsHolder.FATAL_ERROR, "", message)
    }
  }

  @Experimental
  fun interface VcsLogUiFactory<T : VcsLogUiEx> {
    fun createLogUi(project: Project, logData: VcsLogData): T
  }

  @Internal
  abstract class BaseVcsLogUiFactory<T : VcsLogUiImpl>(
    private val logId: String, private val filters: VcsLogFilterCollection?, private val uiProperties: VcsLogTabsProperties,
    private val colorManager: VcsLogColorManager,
  ) : VcsLogUiFactory<T> {
    override fun createLogUi(project: Project, logData: VcsLogData): T {
      val properties = uiProperties.createProperties(logId)
      val vcsLogFilterer = VcsLogFiltererImpl(logData)
      val initialOptions = properties[MainVcsLogUiProperties.GRAPH_OPTIONS]
      val initialFilters = filters ?: collection()
      val refresher = VisiblePackRefresherImpl(project, logData, initialFilters, initialOptions,
                                               vcsLogFilterer, logId)
      return createVcsLogUiImpl(logId, logData, properties, colorManager, refresher, filters)
    }

    protected abstract fun createVcsLogUiImpl(
      logId: String,
      logData: VcsLogData,
      properties: MainVcsLogUiProperties,
      colorManager: VcsLogColorManager,
      refresher: VisiblePackRefresherImpl,
      filters: VcsLogFilterCollection?,
    ): T
  }

  private class MainVcsLogUiFactory(
    logId: String, filters: VcsLogFilterCollection?, properties: VcsLogTabsProperties,
    colorManager: VcsLogColorManager,
  ) : BaseVcsLogUiFactory<VcsLogUiImpl>(logId, filters, properties, colorManager) {
    override fun createVcsLogUiImpl(
      logId: String,
      logData: VcsLogData,
      properties: MainVcsLogUiProperties,
      colorManager: VcsLogColorManager,
      refresher: VisiblePackRefresherImpl,
      filters: VcsLogFilterCollection?,
    ): VcsLogUiImpl {
      return VcsLogUiImpl(logId, logData, colorManager, properties, refresher, filters)
    }
  }

  companion object {
    @Internal
    const val MAIN_LOG_ID: @NonNls String = "MAIN"
    private val LOG = Logger.getInstance(VcsLogManager::class.java)

    /**
     * If log should be refreshed even when inactive
     */
    @Internal
    @JvmStatic
    fun keepUpToDate(): Boolean {
      return Registry.`is`("vcs.log.keep.up.to.date") && !PowerSaveMode.isEnabled()
    }

    @JvmStatic
    fun findLogProviders(roots: Collection<VcsRoot>, project: Project): Map<VirtualFile, VcsLogProvider> {
      if (roots.isEmpty()) return emptyMap()

      val logProviders: MutableMap<VirtualFile, VcsLogProvider> = HashMap()
      val allLogProviders = VcsLogProvider.LOG_PROVIDER_EP.getExtensionList(project)
      for (root in roots) {
        val vcs = root.vcs
        val path = root.path
        if (vcs == null) {
          LOG.debug("Skipping invalid VCS root: $root")
          continue
        }

        for (provider in allLogProviders) {
          if (provider.supportedVcs == vcs.keyInstanceMethod) {
            logProviders[path] = provider
            break
          }
        }
      }
      return logProviders
    }
  }
}

@Internal
suspend fun VcsLogManager.awaitContainsCommit(hash: Hash, root: VirtualFile): Boolean {
  if (!containsCommit(hash, root)) {
    if (isLogUpToDate) return false
    waitForRefresh()
    if (!containsCommit(hash, root)) return false
  }
  return true
}

private fun VcsLogManager.containsCommit(hash: Hash, root: VirtualFile): Boolean {
  if (!dataManager.storage.containsCommit(CommitId(hash, root))) return false

  @Suppress("UNCHECKED_CAST")
  val permanentGraphInfo = dataManager.dataPack.permanentGraph as? PermanentGraphInfo<VcsLogCommitStorageIndex> ?: return true

  val commitIndex = dataManager.storage.getCommitIndex(hash, root)
  val nodeId = permanentGraphInfo.permanentCommitsInfo.getNodeId(commitIndex)
  return nodeId != VcsLogUiEx.COMMIT_NOT_FOUND
}

private fun VcsLogUiEx.isVisible(): Boolean = ComponentUtil.isShowing(mainComponent, false)

suspend fun VcsLogManager.waitForRefresh() {
  suspendCancellableCoroutine { continuation ->
    val dataPackListener = object : DataPackChangeListener {
      override fun onDataPackChange(newDataPack: DataPack) {
        if (isLogUpToDate) {
          dataManager.removeDataPackChangeListener(this)
          continuation.resumeWith(Result.success(Unit))
        }
      }
    }
    dataManager.addDataPackChangeListener(dataPackListener)
    if (isLogUpToDate) {
      dataManager.removeDataPackChangeListener(dataPackListener)
      continuation.resumeWith(Result.success(Unit))
      return@suspendCancellableCoroutine
    }

    scheduleUpdate()

    continuation.invokeOnCancellation { dataManager.removeDataPackChangeListener(dataPackListener) }
  }
}
