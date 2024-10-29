// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.PhmVcsLogStorageBackend
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiImpl
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

private val LOG: Logger
  get() = logger<VcsProjectLog>()

private val CLOSE_LOG_TIMEOUT = 10.seconds

@Service(Service.Level.PROJECT)
class VcsProjectLog(private val project: Project, @ApiStatus.Internal val coroutineScope: CoroutineScope) {
  private val uiProperties = project.service<VcsLogProjectTabsProperties>()
  private val errorHandler = VcsProjectLogErrorHandler(this, coroutineScope)

  @Volatile
  private var cachedLogManager: VcsProjectLogManager? = null

  private val disposeStarted = AtomicBoolean(false)

  // not-reentrant - invoking [lock] even from the same thread/coroutine that currently holds the lock still suspends the invoker
  private val mutex = Mutex()

  val logManager: VcsLogManager? get() = cachedLogManager

  @get:ApiStatus.Internal
  val projectLogManager: VcsProjectLogManager? get() = cachedLogManager
  val dataManager: VcsLogData? get() = cachedLogManager?.dataManager
  val tabManager: VcsLogTabsManager? get() = cachedLogManager?.tabsManager

  val isDisposing: Boolean get() = disposeStarted.get()

  /** The instance of the [MainVcsLogUi] or null if the log was not initialized yet. */
  val mainLogUi: VcsLogUiImpl?
    get() = getVcsLogContentProvider(project)?.ui as VcsLogUiImpl?

  private val listenersDisposable = Disposer.newDisposable()

  init {
    val busConnection = project.messageBus.connect(listenersDisposable)
    busConnection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsMappingListener {
      LOG.debug("Recreating Vcs Log after roots changed")
      launchRecreateLog()
    })
    busConnection.subscribe(DynamicPluginListener.TOPIC, MyDynamicPluginUnloader())
    VcsLogData.getIndexingRegistryValue().addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        LOG.debug("Recreating Vcs Log after indexing registry value changed")
        launchRecreateLog()
      }
    }, listenersDisposable)
    project.service<VcsLogSharedSettings>().addListener(VcsLogSharedSettings.Listener {
      LOG.debug("Recreating Vcs Log after settings changed")
      launchRecreateLog()
    }, listenersDisposable)
    PhmVcsLogStorageBackend.durableEnumeratorRegistryProperty.addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        LOG.debug("Recreating Vcs Log after durable enumerator registry value changed")
        launchWithAnyModality { logManager?.let { invalidateCaches(it) } }
      }
    }, listenersDisposable)

    @Suppress("SSBasedInspection", "ObjectLiteralToLambda") val shutdownTask = object : Runnable {
      override fun run() {
        if (disposeStarted.get()) {
          LOG.warn("unregisterShutdownTask should be called")
          return
        }

        runBlocking {
          shutDown(useRawSwingDispatcher = true)
        }
      }
    }

    ShutDownTracker.getInstance().registerShutdownTask(shutdownTask)
    coroutineScope.awaitCancellationAndInvoke(CoroutineName("Close VCS log")) {
      ShutDownTracker.getInstance().unregisterShutdownTask(shutdownTask)
      shutDown(useRawSwingDispatcher = false)
    }
  }

  private suspend fun shutDown(useRawSwingDispatcher: Boolean) {
    if (!disposeStarted.compareAndSet(false, true)) return

    Disposer.dispose(listenersDisposable)

    try {
      withTimeout(CLOSE_LOG_TIMEOUT) {
        mutex.withLock {
          disposeLogInternal(useRawSwingDispatcher = useRawSwingDispatcher)
        }
      }
    }
    catch (e: TimeoutCancellationException) {
      LOG.error("Cannot close VCS log in ${CLOSE_LOG_TIMEOUT.inWholeSeconds} seconds")
    }
  }

  @RequiresEdt
  fun openLogTab(filters: VcsLogFilterCollection): MainVcsLogUi? {
    return openLogTab(filters = filters, location = VcsLogTabLocation.TOOL_WINDOW)
  }

  @RequiresEdt
  fun openLogTab(filters: VcsLogFilterCollection, location: VcsLogTabLocation): MainVcsLogUi? {
    return tabManager?.openAnotherLogTab(filters = filters, location = location)
  }

  fun createLogInBackground(forceInit: Boolean) {
    launchCreateLog(forceInit)
  }

  private fun launchCreateLog(forceInit: Boolean): Job {
    return launchWithAnyModality {
      mutex.withLock {
        createLogInternal(forceInit)
      }
    }
  }

  private fun launchDisposeLog(useRawSwingDispatcher: Boolean = false): Job {
    return launchWithAnyModality {
      mutex.withLock {
        disposeLogInternal(useRawSwingDispatcher)
      }
    }
  }

  private fun launchRecreateLog(beforeCreateLog: (suspend () -> Unit)? = null): Job {
    return launchWithAnyModality {
      mutex.withLock {
        disposeLogInternal(false)

        try {
          beforeCreateLog?.invoke()
        }
        catch (e: Throwable) {
          LOG.error("Unable to execute 'beforeCreateLog'", e)
        }

        createLogInternal(forceInit = false)
      }
    }
  }

  private suspend fun disposeLogInternal(useRawSwingDispatcher: Boolean) {
    val logManager = withContext(if (useRawSwingDispatcher) RawSwingDispatcher else Dispatchers.EDT) {
      dropLogManager()?.also { it.disposeUi() }
    }
    if (logManager != null) Disposer.dispose(logManager)
  }

  private suspend fun createLogInternal(forceInit: Boolean): VcsLogManager? {
    if (isDisposing) return null

    val projectLevelVcsManager = project.serviceAsync<ProjectLevelVcsManager>()
    val logProviders = VcsLogManager.findLogProviders(projectLevelVcsManager.allVcsRoots.toList(), project)
    if (logProviders.isEmpty()) return null

    project.trackActivity(VcsActivityKey) {
      val logManager = getOrCreateLogManager(logProviders)
      logManager.initialize(force = forceInit)
    }
    return logManager
  }

  private suspend fun getOrCreateLogManager(logProviders: Map<VirtualFile, VcsLogProvider>): VcsLogManager {
    cachedLogManager?.let {
      return it
    }

    LOG.debug { "Creating ${getProjectLogName(logProviders)}" }
    val result = VcsProjectLogManager(project, uiProperties, logProviders) { s, t ->
      errorHandler.recreateOnError(s, t)
    }
    cachedLogManager = result
    val publisher = project.messageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED)
    withContext(Dispatchers.EDT) {
      result.createUi()
      publisher.logCreated(result)
    }
    return result
  }

  @RequiresEdt
  private fun dropLogManager(): VcsLogManager? {
    ThreadingAssertions.assertEventDispatchThread()
    val oldValue = cachedLogManager ?: return null
    cachedLogManager = null
    LOG.debug { "Disposing ${oldValue.name}" }
    project.messageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logDisposed(oldValue)
    return oldValue
  }

  /**
   * Launches the coroutine with "any" modality in the context.
   * Using "any" modality is required in order to create or dispose log when modal dialog (such as Settings dialog) is shown.
   */
  private fun launchWithAnyModality(block: suspend CoroutineScope.() -> Unit): Job {
    return coroutineScope.launch(ModalityState.any().asContextElement(), block = block)
  }

  internal class InitLogStartupActivity : ProjectActivity {
    init {
      val app = ApplicationManager.getApplication()
      if (app.isUnitTestMode) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override suspend fun execute(project: Project) {
      getInstance(project).launchCreateLog(forceInit = false)
    }
  }

  private inner class MyDynamicPluginUnloader : DynamicPluginListener {
    private val affectedPlugins = HashSet<PluginId>()

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
      if (hasLogExtensions(pluginDescriptor)) {
        LOG.debug { "Disposing Vcs Log after loading ${pluginDescriptor.pluginId}" }
        launchRecreateLog()
      }
    }

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
      if (hasLogExtensions(pluginDescriptor)) {
        affectedPlugins.add(pluginDescriptor.pluginId)
        LOG.debug { "Disposing Vcs Log before unloading ${pluginDescriptor.pluginId}" }
        launchDisposeLog()
      }
    }

    override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
      if (affectedPlugins.remove(pluginDescriptor.pluginId)) {
        LOG.debug { "Recreating Vcs Log after unloading ${pluginDescriptor.pluginId}" }
        // createLog calls between beforePluginUnload and pluginUnloaded are technically not prohibited
        // so, just in case, recreating log here
        launchRecreateLog()
      }
    }

    private fun hasLogExtensions(descriptor: IdeaPluginDescriptor): Boolean {
      for (logProvider in VcsLogProvider.LOG_PROVIDER_EP.getExtensionList(project)) {
        if (logProvider.javaClass.classLoader === descriptor.pluginClassLoader) {
          return true
        }
      }
      for (factory in CustomVcsLogUiFactoryProvider.LOG_CUSTOM_UI_FACTORY_PROVIDER_EP.getExtensions(project)) {
        if (factory.javaClass.classLoader === descriptor.pluginClassLoader) {
          return true
        }
      }
      return false
    }
  }

  interface ProjectLogListener {
    @RequiresEdt
    fun logCreated(manager: VcsLogManager)

    @RequiresEdt
    fun logDisposed(manager: VcsLogManager)
  }

  companion object {
    @Topic.ProjectLevel
    @JvmField
    val VCS_PROJECT_LOG_CHANGED = Topic(ProjectLogListener::class.java, Topic.BroadcastDirection.NONE, true)

    @JvmStatic
    fun getLogProviders(project: Project): Map<VirtualFile, VcsLogProvider> {
      return VcsLogManager.findLogProviders(ProjectLevelVcsManager.getInstance(project).allVcsRoots.toList(), project)
    }

    @JvmStatic
    fun getSupportedVcs(project: Project): Set<VcsKey> {
      return getLogProviders(project).values.mapTo(mutableSetOf()) { it.supportedVcs }
    }

    @JvmStatic
    fun getInstance(project: Project): VcsProjectLog = project.service<VcsProjectLog>()

    /**
     * Disposes log and performs the given `task` before recreating the log
     */
    @ApiStatus.Internal
    @RequiresBackgroundThread
    suspend fun VcsProjectLog.runOnDisposedLog(task: (suspend () -> Unit)?) {
      launchRecreateLog(beforeCreateLog = task).join()
    }

    /**
     * Executes the given action if the VcsProjectLog has been initialized. If not, then schedules the log initialization,
     * waits for it in a background task, and executes the action after the log is ready.
     */
    @RequiresEdt
    fun runWhenLogIsReady(project: Project, action: (VcsLogManager) -> Unit) {
      val projectLog = getInstance(project)
      val manager = projectLog.logManager
      if (manager != null) {
        action(manager)
        return
      }

      // schedule showing the log, wait its initialization, and then open the tab
      projectLog.coroutineScope.launch {
        withBackgroundProgress(project, VcsLogBundle.message("vcs.log.creating.process")) {
          waitWhenLogIsReady(project)

          withContext(Dispatchers.EDT) {
            projectLog.logManager?.let {
              action(it)
            }
          }
        }
      }
    }

    suspend fun waitWhenLogIsReady(project: Project): Boolean {
      val projectLog = getInstance(project)
      if (projectLog.logManager != null) return true
      projectLog.launchCreateLog(forceInit = true).join()
      return projectLog.logManager != null
    }

    @ApiStatus.Internal
    @RequiresBackgroundThread
    fun ensureLogCreated(project: Project): Boolean {
      ApplicationManager.getApplication().assertIsNonDispatchThread()
      return runBlockingMaybeCancellable {
        waitWhenLogIsReady(project)
      }
    }
  }
}

private suspend fun VcsLogManager.initialize(force: Boolean) {
  if (force) {
    blockingContext {
      scheduleInitialization()
    }
    return
  }

  if (PostponableLogRefresher.keepUpToDate()) {
    val invalidator = CachesInvalidator.EP_NAME.findExtensionOrFail(VcsLogCachesInvalidator::class.java)
    if (invalidator.isValid) {
      blockingContext {
        scheduleInitialization()
      }
      return
    }
  }

  withContext(Dispatchers.EDT) {
    if (isLogVisible) {
      blockingContext {
        scheduleInitialization()
      }
    }
  }
}