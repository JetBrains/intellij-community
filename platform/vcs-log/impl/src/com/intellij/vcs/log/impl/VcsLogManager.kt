// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.objectTree.ThrowableInterner
import com.intellij.openapi.vcs.VcsNotifier
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.MultiMap
import com.intellij.util.lazyUnsafe
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogRefresher
import com.intellij.vcs.log.VcsLogUi
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogStatusBarProgress
import com.intellij.vcs.log.data.index.VcsLogModifiableIndex
import com.intellij.vcs.log.impl.PostponableLogRefresher.VcsLogWindow
import com.intellij.vcs.log.ui.*
import com.intellij.vcs.log.util.VcsLogUtil
import com.intellij.vcs.log.visible.VcsLogFiltererImpl
import com.intellij.vcs.log.visible.VisiblePackRefresherImpl
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject.collection
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntSets
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.BiConsumer

open class VcsLogManager @Internal constructor(
  protected val project: Project,
  val uiProperties: VcsLogTabsProperties,
  logProviders: Map<VirtualFile, VcsLogProvider>,
  internal val name: String,
  scheduleRefreshImmediately: Boolean,
  isIndexEnabled: Boolean,
  private val recreateMainLogHandler: BiConsumer<in VcsLogErrorHandler.Source, in Throwable>?,
) : Disposable {

  private constructor(project: Project, uiProperties: VcsLogTabsProperties, logProviders: Map<VirtualFile, VcsLogProvider>)
    : this(project, uiProperties, logProviders, generateName(logProviders), true, false, null)

  @Internal
  constructor(project: Project, uiProperties: VcsLogTabsProperties, roots: Collection<VcsRoot>)
    : this(project, uiProperties, findLogProviders(roots, project))

  val dataManager: VcsLogData = VcsLogData(project, logProviders, MyErrorHandler(), isIndexEnabled, this)
  private val postponableRefresher = PostponableLogRefresher(dataManager)

  private val lazyTabsWatcher = lazyUnsafe {
    LOG.assertTrue(!isDisposed)
    VcsLogTabsWatcher(project, postponableRefresher)
  }

  val colorManager: VcsLogColorManager = VcsLogColorManagerFactory.create(logProviders.keys)
  private val statusBarProgress = VcsLogStatusBarProgress(project, logProviders, dataManager.index.indexingRoots, dataManager.progress)

  @get:RequiresEdt
  var isDisposed: Boolean = false
    private set

  init {
    refreshLogOnVcsEvents(dataManager, logProviders, postponableRefresher)
    if (scheduleRefreshImmediately) {
      scheduleInitialization()
    }
  }

  @get:RequiresEdt
  val isLogVisible: Boolean
    get() = postponableRefresher.isLogVisible

  /**
   * If this Log has a full data pack and there are no postponed refreshes. Does not check if there are refreshes in progress.
   */
  @get:RequiresEdt
  val isLogUpToDate: Boolean
    get() = dataManager.dataPack.isFull && !postponableRefresher.hasPostponedRoots()

  @Internal
  @CalledInAny
  fun scheduleInitialization() {
    dataManager.initialize()
  }

  /**
   * Schedules Log initialization and update even when none on the log tabs is visible and a power save mode is enabled.
   *
   * @see PostponableLogRefresher.canRefreshNow
   */
  @Internal
  @RequiresEdt
  fun scheduleUpdate() {
    scheduleInitialization()
    postponableRefresher.refreshPostponedRoots()
  }

  @Internal
  fun createLogUi(logId: String, location: VcsLogTabLocation): MainVcsLogUi {
    return createLogUi(getMainLogUiFactory(logId, null), location, true)
  }

  @Internal
  fun createLogUi(logId: String, location: VcsLogTabLocation, isClosedOnDispose: Boolean): MainVcsLogUi {
    return createLogUi(getMainLogUiFactory(logId, null), location, isClosedOnDispose)
  }

  @Internal
  fun <U : VcsLogUiEx> createLogUi(factory: VcsLogUiFactory<U>, location: VcsLogTabLocation): U {
    return createLogUi(factory, location, true)
  }

  @Internal
  fun getMainLogUiFactory(logId: String, filters: VcsLogFilterCollection?): VcsLogUiFactory<out MainVcsLogUi> {
    val factoryProvider = CustomVcsLogUiFactoryProvider.LOG_CUSTOM_UI_FACTORY_PROVIDER_EP.findFirstSafe(project) {
      it.isActive(dataManager.logProviders)
    }
    if (factoryProvider == null) {
      return MainVcsLogUiFactory(logId, filters, uiProperties, colorManager)
    }
    return factoryProvider.createLogUiFactory(logId, this, filters)
  }

  private fun <U : VcsLogUiEx> createLogUi(
    factory: VcsLogUiFactory<U>,
    location: VcsLogTabLocation,
    isClosedOnDispose: Boolean,
  ): U {
    ThreadingAssertions.assertEventDispatchThread()
    if (isDisposed) {
      LOG.error("Trying to create new VcsLogUi on a disposed VcsLogManager instance")
      throw ProcessCanceledException()
    }

    val ui = factory.createLogUi(project, dataManager)
    Disposer.register(ui, lazyTabsWatcher.value.addTabToWatch(ui, location, isClosedOnDispose))

    return ui
  }

  @Internal
  fun <U : VcsLogUiEx, W : VcsLogWindow> registerLogWindow(ui: U, window: W) {
    Disposer.register(ui, postponableRefresher.addLogWindow(window))
  }

  fun getLogUis(): List<VcsLogUi> {
    if (!lazyTabsWatcher.isInitialized()) return emptyList()
    return lazyTabsWatcher.value.getTabs()
  }

  @Internal
  fun getLogUis(location: VcsLogTabLocation): List<VcsLogUi> {
    if (!lazyTabsWatcher.isInitialized()) return emptyList()
    return lazyTabsWatcher.value.getTabs(location)
  }

  @Internal
  fun getVisibleLogUis(location: VcsLogTabLocation): List<VcsLogUi> {
    if (!lazyTabsWatcher.isInitialized()) return emptyList()
    return lazyTabsWatcher.value.getVisibleTabs(location)
  }

  @Internal
  fun getLogWindowsInformation(): String {
    return postponableRefresher.logWindowsInformation
  }

  @RequiresEdt
  internal open fun disposeUi() {
    isDisposed = true
    ThreadingAssertions.assertEventDispatchThread()
    if (lazyTabsWatcher.isInitialized()) Disposer.dispose(lazyTabsWatcher.value)
    Disposer.dispose(statusBarProgress)
  }

  /**
   * Dispose VcsLogManager and execute some activity after it.
   *
   * @param callback activity to run after log is disposed. Is executed in background thread. null means execution of additional activity after disposing is not required.
   */
  @RequiresEdt
  fun dispose(callback: Runnable?) {
    disposeUi()
    ApplicationManager.getApplication().executeOnPooledThread {
      Disposer.dispose(this)
      callback?.run()
    }
  }

  @RequiresBackgroundThread
  override fun dispose() {
    // since disposing log triggers flushing indexes on disk we do not want to do it in EDT
    // disposing of VcsLogManager is done by manually executing dispose(@Nullable Runnable callback)
    // the above method first disposes ui in EDT, then disposes everything else in a background
    ApplicationManager.getApplication().assertIsNonDispatchThread()
    LOG.debug("Disposed " + name)
  }

  private inner class MyErrorHandler : VcsLogErrorHandler {
    private val myErrors: IntSet = IntSets.synchronize(IntOpenHashSet())
    private val myIsBroken = AtomicBoolean(false)

    override fun handleError(source: VcsLogErrorHandler.Source, throwable: Throwable) {
      if (myIsBroken.compareAndSet(false, true)) {
        if (recreateMainLogHandler != null) {
          ApplicationManager.getApplication().invokeLater {
            recreateMainLogHandler.accept(source, throwable)
          }
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

  @ApiStatus.Experimental
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
    private val LOG = Logger.getInstance(VcsLogManager::class.java)

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

private fun generateName(logProviders: Map<VirtualFile, VcsLogProvider>) =
  "Vcs Log for " + VcsLogUtil.getProvidersMapText(logProviders)

private fun refreshLogOnVcsEvents(
  disposableParent: Disposable,
  logProviders: Map<VirtualFile, VcsLogProvider>,
  refresher: PostponableLogRefresher,
) {
  val providers2roots = MultiMap.create<VcsLogProvider, VirtualFile>()
  logProviders.forEach { (key, value) -> providers2roots.putValue(value, key) }

  val wrappedRefresher = VcsLogRefresher { root ->
    ApplicationManager.getApplication().invokeLater({ refresher.refresh(root) }, ModalityState.any())
  }
  for ((key, value) in providers2roots.entrySet()) {
    val disposable = key.subscribeToRootRefreshEvents(value, wrappedRefresher)
    Disposer.register(disposableParent, disposable)
  }
}
