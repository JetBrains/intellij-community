// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.objectTree.ThrowableInterner
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.ComponentUtil
import com.intellij.util.BitUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.MultiMap
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
import kotlinx.coroutines.*
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
  parentCs: CoroutineScope,
  val uiProperties: VcsLogTabsProperties,
  logProviders: Map<VirtualFile, VcsLogProvider>,
  @Internal val name: String,
  isIndexEnabled: Boolean,
  private val errorHandler: ((VcsLogErrorHandler.Source, Throwable) -> Unit)?,
) {
  @Internal
  protected val cs: CoroutineScope = parentCs.childScope("Vcs Log manager $name")
  val dataManager: VcsLogData = VcsLogData(project, cs, logProviders, MyErrorHandler(), isIndexEnabled)
  private val postponableRefresher = PostponableLogRefresher(dataManager)

  private val managedUis = mutableMapOf<String, VcsLogUiEx>()
  private val uiRegistrationTraces = mutableMapOf<String, Throwable>()

  val colorManager: VcsLogColorManager = VcsLogColorManagerFactory.create(logProviders.keys)
  private val statusBarProgress = VcsLogStatusBarProgress(project, logProviders, dataManager.index.indexingRoots, dataManager.progress)

  val isDisposed: Boolean get() = !cs.isActive

  init {
    cs.launch(start = CoroutineStart.UNDISPATCHED) {
      val refresherDisposable = Disposer.newDisposable("Vcs Log Data Refresh for $name")
      refreshLogOnVcsEvents(refresherDisposable, logProviders, postponableRefresher)

      try {
        awaitCancellation()
      }
      finally {
        LOG.debug { "Disposing $name" }
        withContext(NonCancellable) {
          Disposer.dispose(refresherDisposable)

          withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
            runCatching {
              disposeUi()
            }.getOrLogException(LOG)
          }
          LOG.debug { "Disposed ${name}" }
        }
      }
    }
  }

  private val isLogVisible: Boolean
    get() = managedUis.any { it.value.isVisible() }

  private var frozen = false

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

  @Internal
  @RequiresEdt
  private fun freezeLog() {
    frozen = true
  }

  @Internal
  @RequiresEdt
  private fun unfreezeLog() {
    if (frozen) {
      frozen = false
      scheduleUpdate()
    }
  }

  @Internal
  suspend fun <R> runWithFreezing(operation: () -> R): R {
    withContext(Dispatchers.EDT) {
      freezeLog()
    }
    try {
      return operation()
    }
    finally {
      withContext(Dispatchers.EDT) {
        unfreezeLog()
      }
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
   * Manually release all resources associated with the manager
   */
  @Internal
  suspend fun dispose() {
    cs.coroutineContext.job.cancelAndJoin()
  }

  internal val hasPersistentStorage: Boolean
    get() = dataManager.hasPersistentStorage

  internal suspend fun clearPersistentStorage() {
    require(isDisposed) { "Cannot clear persistent storage of a not disposed VcsLogManager"}
    dataManager.clearPersistentStorage()
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
                                                        refresher.refresh(root, frozen || !(keepUpToDate() || isLogVisible))
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
