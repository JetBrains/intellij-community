// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.SimpleMessageBusConnection
import com.intellij.util.messages.Topic
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.util.VcsLogUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

private val LOG: Logger
  get() = logger<VcsProjectLog>()

@Service(Service.Level.PROJECT)
class VcsProjectLog(private val project: Project, private val coroutineScope: CoroutineScope) {
  private val uiProperties = project.service<VcsLogProjectTabsProperties>()
  internal val tabManager: VcsLogTabsManager

  private val lazyVcsLogManager = LazyVcsLogManager(project = project,
                                                    uiProperties = uiProperties,
                                                    errorHandler = VcsProjectLogErrorHandler(this))

  private val disposeStarted = AtomicBoolean(false)

  private val mutex = Mutex()

  val logManager: VcsLogManager?
    get() = lazyVcsLogManager.cached
  val dataManager: VcsLogData?
    get() = lazyVcsLogManager.cached?.dataManager
  val isDisposing: Boolean
    get() = disposeStarted.get()

  /** The instance of the [MainVcsLogUi] or null if the log was not initialized yet. */
  val mainLogUi: VcsLogUiImpl?
    get() = VcsLogContentProvider.getInstance(project)?.ui as VcsLogUiImpl?

  internal val busConnection: SimpleMessageBusConnection = project.messageBus.connect(coroutineScope)

  init {
    tabManager = VcsLogTabsManager(project = project, uiProperties = uiProperties, busConnection = busConnection)

    @Suppress("SSBasedInspection") val shutdownTask = Runnable {
      runBlocking {
        shutDown()
      }
    }

    ShutDownTracker.getInstance().registerShutdownTask(shutdownTask)
    busConnection.subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosing(project: Project) {
        if (project === this@VcsProjectLog.project) {
          ShutDownTracker.getInstance().unregisterShutdownTask(shutdownTask)
          runBlockingModal(owner = ModalTaskOwner.project(project),
                           title = VcsLogBundle.message("vcs.log.closing.process"),
                           cancellation = TaskCancellation.nonCancellable()) {
            shutDown()
          }
        }
      }
    })
  }

  private suspend fun shutDown() {
    if (!disposeStarted.compareAndSet(false, true)) {
      return
    }

    withTimeout(10.seconds) {
      disposeLog(recreate = false)
    }
  }

  private fun subscribeToMappingsAndPluginChanges() {
    busConnection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsMappingListener {
      coroutineScope.launch {
        disposeLog(recreate = true)
      }
    })
    busConnection.subscribe(DynamicPluginListener.TOPIC, MyDynamicPluginUnloader())
  }

  private fun disposeLogBlocking(recreate: Boolean, beforeCreateLog: Runnable = EmptyRunnable.getInstance()) {
    if (ApplicationManager.getApplication().isDispatchThread) {
      runBlockingModal(owner = ModalTaskOwner.project(project),
                       title = VcsLogBundle.message("vcs.log.closing.process"),
                       cancellation = TaskCancellation.nonCancellable()) {
        disposeLog(recreate = recreate, beforeCreateLog = beforeCreateLog)
      }
    }
    else {
      runBlockingMaybeCancellable {
        disposeLog(recreate = recreate)
      }
    }
  }

  @RequiresEdt
  fun openLogTab(filters: VcsLogFilterCollection): MainVcsLogUi? {
    return openLogTab(filters = filters, location = VcsLogTabLocation.TOOL_WINDOW)
  }

  @RequiresEdt
  fun openLogTab(filters: VcsLogFilterCollection, location: VcsLogTabLocation): MainVcsLogUi? {
    val logManager = logManager ?: return null
    return tabManager.openAnotherLogTab(manager = logManager, filters = filters, location = location)
  }

  @CalledInAny
  private suspend fun disposeLog(recreate: Boolean, beforeCreateLog: Runnable = EmptyRunnable.getInstance()) {
    mutex.withLock {
      val logManager = withContext(Dispatchers.EDT + modality()) {
        blockingContext {
          val manager = lazyVcsLogManager.dropValue()
          manager?.disposeUi()
          manager
        }
      }
      if (logManager != null) {
        blockingContext {
          Disposer.dispose(logManager)
        }
      }
      if (recreate) {
        if (!isDisposing) {
          try {
            blockingContext {
              beforeCreateLog.run()
            }
          }
          catch (e: Throwable) {
            LOG.error("Unable to execute 'beforeCreateLog'", e)
          }
        }

        try {
          createLog(false)
        }
        catch (e: Throwable) {
          LOG.error("Unable to execute 'createLog'", e)
        }
      }
    }
  }

  /**
   * Disposes log and performs the given `task` before recreating the log
   */
  @ApiStatus.Internal
  @CalledInAny
  fun runOnDisposedLog(task: Runnable): Job {
    return coroutineScope.launch {
      disposeLog(recreate = true, beforeCreateLog = task)
    }
  }

  fun createLogInBackground(forceInit: Boolean) {
    coroutineScope.async {
      createLog(forceInit) != null
    }
  }

  private suspend fun createLog(forceInit: Boolean): VcsLogManager? {
    if (isDisposing) {
      return null
    }

    mutex.withLock {
      if (isDisposing) {
        return null
      }

      val logProviders = blockingContext { getLogProviders(project) }
      if (logProviders.isEmpty()) {
        return null
      }

      val logManager = lazyVcsLogManager.getValue(logProviders)
      initialize(logManager = logManager, force = forceInit)
      return logManager
    }
  }

  internal class InitLogStartupActivity : ProjectActivity {
    init {
      val app = ApplicationManager.getApplication()
      if (app.isUnitTestMode || app.isHeadlessEnvironment) {
        throw ExtensionNotApplicableException.create()
      }
    }

    override suspend fun execute(project: Project) {
      val projectLog = getInstance(project)
      projectLog.subscribeToMappingsAndPluginChanges()
      projectLog.createLog(forceInit = false)
    }
  }

  private inner class MyDynamicPluginUnloader : DynamicPluginListener {
    private val affectedPlugins: MutableSet<PluginId> = HashSet()
    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
      if (hasLogExtensions(pluginDescriptor)) {
        disposeLogBlocking(recreate = true)
      }
    }

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
      if (hasLogExtensions(pluginDescriptor)) {
        affectedPlugins.add(pluginDescriptor.pluginId)
        LOG.debug("Disposing Vcs Log before unloading ${pluginDescriptor.pluginId}")
        disposeLogBlocking(recreate = false)
      }
    }

    override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
      if (affectedPlugins.remove(pluginDescriptor.pluginId)) {
        LOG.debug("Recreating Vcs Log after unloading " + pluginDescriptor.pluginId)
        // createLog calls between beforePluginUnload and pluginUnloaded are technically not prohibited
        //  so, just in case, recreating log here
        disposeLogBlocking(recreate = true)
      }
    }

    private fun hasLogExtensions(descriptor: IdeaPluginDescriptor): Boolean {
      for (logProvider in VcsLogProvider.LOG_PROVIDER_EP.getExtensions(project)) {
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
    fun getInstance(project: Project): VcsProjectLog = project.service<VcsProjectLog>()

    /**
     * Executes the given action if the VcsProjectLog has been initialized. If not, then schedules the log initialization,
     * waits for it in a background task, and executes the action after the log is ready.
     */
    @RequiresEdt
    fun runWhenLogIsReady(project: Project, action: (VcsLogManager) -> Unit) {
      val log = getInstance(project)
      val manager = log.logManager
      if (manager != null) {
        action(manager)
        return
      }

      // schedule showing the log, wait its initialization, and then open the tab
      log.coroutineScope.launch {
        withBackgroundProgress(project, VcsLogBundle.message("vcs.log.creating.process")) {
          log.createLog(forceInit = true)

          withContext(Dispatchers.EDT) {
            log.logManager?.let {
              blockingContext {
                action(it)
              }
            }
          }
        }
      }
    }

    suspend fun waitWhenLogIsReady(project: Project): Boolean {
      val log = getInstance(project)
      return if (log.logManager == null) log.createLog(true) != null else true
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

// Using "any" modality specifically is required in order to be able to wait for log initialization or disposal under modal progress.
// Otherwise, methods such as "VcsProjectLog#runWhenLogIsReady" or "VcsProjectLog.shutDown" won't be able to work
// when "disposeLog" is queued as "invokeAndWait"
// (used there in order to ensure sequential execution) will the app freeze when modal progress is displayed.
private suspend fun modality(): CoroutineContext {
  return if (coroutineContext.contextModality() == null) ModalityState.any().asContextElement() else EmptyCoroutineContext
}

private suspend fun initialize(logManager: VcsLogManager, force: Boolean) {
  if (force) {
    logManager.scheduleInitialization()
    return
  }

  if (PostponableLogRefresher.keepUpToDate()) {
    val invalidator = CachesInvalidator.EP_NAME.findExtensionOrFail(VcsLogCachesInvalidator::class.java)
    if (invalidator.isValid) {
      HeavyAwareListener.executeOutOfHeavyProcessLater(5000) { logManager.scheduleInitialization() }
      return
    }
  }

  withContext(modality()) {
    if (logManager.isLogVisible) {
      logManager.scheduleInitialization()
    }
  }
}

private class LazyVcsLogManager(private val project: Project,
                                private val uiProperties: VcsLogProjectTabsProperties,
                                private val errorHandler: VcsProjectLogErrorHandler) {
  @Volatile
  var cached: VcsLogManager? = null
    private set

  suspend fun getValue(logProviders: Map<VirtualFile, VcsLogProvider>): VcsLogManager {
    cached?.let {
      return it
    }

    LOG.debug { "Creating Vcs Log for ${VcsLogUtil.getProvidersMapText(logProviders)}" }
    val result = VcsLogManager(project, uiProperties, logProviders, false) { s, t ->
      errorHandler.recreateOnError(s, t)
    }
    cached = result
    val publisher = project.messageBus.syncPublisher(VcsProjectLog.VCS_PROJECT_LOG_CHANGED)
    withContext(Dispatchers.EDT + modality()) {
      publisher.logCreated(result)
    }
    return result
  }

  @RequiresEdt
  fun dropValue(): VcsLogManager? {
    ApplicationManager.getApplication().assertIsDispatchThread()
    val oldValue = cached ?: return null
    cached = null
    LOG.debug { "Disposing Vcs Log for ${VcsLogUtil.getProvidersMapText(oldValue.dataManager.logProviders)}" }
    project.messageBus.syncPublisher(VcsProjectLog.VCS_PROJECT_LOG_CHANGED).logDisposed(oldValue)
    return oldValue
  }
}