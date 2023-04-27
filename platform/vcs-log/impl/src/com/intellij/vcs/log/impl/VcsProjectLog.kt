// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseListener
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.messages.Topic
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogFilterCollection
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.ui.MainVcsLogUi
import com.intellij.vcs.log.ui.VcsLogUiImpl
import com.intellij.vcs.log.util.VcsLogUtil
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

@Service(Service.Level.PROJECT)
class VcsProjectLog(private val project: Project) : Disposable {
  private val uiProperties: VcsLogTabsProperties
  val tabsManager: VcsLogTabsManager
  private val errorHandler: VcsProjectLogErrorHandler

  private val lazyVcsLogManager = LazyVcsLogManager()

  private val disposable = Disposer.newDisposable()
  private val executor: ExecutorService
  private val disposeStarted = AtomicBoolean(false)

  val logManager: VcsLogManager? get() = lazyVcsLogManager.cached
  val dataManager: VcsLogData? get() = lazyVcsLogManager.cached?.dataManager
  val isDisposing: Boolean get() = disposeStarted.get()

  /** The instance of the [MainVcsLogUi] or null if the log was not initialized yet. */
  val mainLogUi: VcsLogUiImpl? get() = VcsLogContentProvider.getInstance(project)?.ui as VcsLogUiImpl?

  init {
    uiProperties = project.getService(VcsLogProjectTabsProperties::class.java)
    tabsManager = VcsLogTabsManager(project, uiProperties, this)
    errorHandler = VcsProjectLogErrorHandler(this)
    executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Vcs Log Initialization/Dispose", 1)
    project.messageBus.connect(disposable).subscribe(ProjectCloseListener.TOPIC, object : ProjectCloseListener {
      override fun projectClosing(project: Project) {
        if (this@VcsProjectLog.project === project) {
          shutDown()
        }
      }
    })
    ShutDownTracker.getInstance().registerShutdownTask({ shutDown() }, disposable)
  }

  private fun shutDown() {
    if (!disposeStarted.compareAndSet(false, true)) {
      return
    }

    Disposer.dispose(disposable)
    disposeLog(false)
    executor.shutdown()
    val awaitDisposal = Runnable {
      try {
        executor.awaitTermination(5, TimeUnit.SECONDS)
      }
      catch (ignored: InterruptedException) {
      }
    }
    if (ApplicationManager.getApplication().isDispatchThread) {
      @Suppress("DialogTitleCapitalization")
      ProgressManager.getInstance().runProcessWithProgressSynchronously(awaitDisposal,
                                                                        VcsLogBundle.message("vcs.log.closing.process"),
                                                                        false, project)
    }
    else {
      awaitDisposal.run()
    }
  }

  private fun subscribeToMappingsAndPluginChanges() {
    val connection = project.messageBus.connect(disposable)
    connection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsMappingListener { disposeLog(true) })
    connection.subscribe(DynamicPluginListener.TOPIC, MyDynamicPluginUnloader())
  }

  @RequiresEdt
  fun openLogTab(filters: VcsLogFilterCollection): MainVcsLogUi? {
    return openLogTab(filters = filters, location = VcsLogTabLocation.TOOL_WINDOW)
  }

  @RequiresEdt
  fun openLogTab(filters: VcsLogFilterCollection, location: VcsLogTabLocation): MainVcsLogUi? {
    val logManager = logManager ?: return null
    return tabsManager.openAnotherLogTab(manager = logManager, filters = filters, location = location)
  }

  @CalledInAny
  private fun disposeLog(recreate: Boolean) {
    disposeLog(recreate, EmptyRunnable.getInstance())
  }

  @CalledInAny
  private fun disposeLog(recreate: Boolean, beforeCreateLog: Runnable): Future<*> {
    return executor.submit {
      val logManager = invokeAndWait {
        val manager = lazyVcsLogManager.dropValue()
        manager?.disposeUi()
        manager
      }
      if (logManager != null) {
        Disposer.dispose(logManager)
      }
      if (recreate) {
        if (!isDisposing) {
          try {
            beforeCreateLog.run()
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
  fun runOnDisposedLog(task: Runnable): Future<*>? {
    return try {
      disposeLog(recreate = true, beforeCreateLog = task)
    }
    catch (e: Exception) {
      LOG.error("Unable to execute on disposed log: $task", e)
      null
    }
  }

  fun createLogInBackground(forceInit: Boolean): CompletableFuture<Boolean> {
    return CompletableFuture.supplyAsync({ createLog(forceInit) }, executor).thenApply { obj -> obj != null }
  }

  @RequiresBackgroundThread
  private fun createLog(forceInit: Boolean): VcsLogManager? {
    if (isDisposing) {
      return null
    }

    val logProviders = getLogProviders(project)
    if (!logProviders.isEmpty()) {
      val logManager = lazyVcsLogManager.getValue(logProviders)
      initialize(logManager = logManager, force = forceInit)
      return logManager
    }
    return null
  }

  override fun dispose() {}

  private inner class LazyVcsLogManager {
    @Volatile
    var cached: VcsLogManager? = null
      private set

    @RequiresBackgroundThread
    fun getValue(logProviders: Map<VirtualFile, VcsLogProvider>): VcsLogManager {
      var result = cached
      if (result == null) {
        LOG.debug("Creating Vcs Log for ${VcsLogUtil.getProvidersMapText(logProviders)}")
        result = VcsLogManager(project, uiProperties, logProviders, false) { s, t ->
          errorHandler.recreateOnError(s, t)
        }
        cached = result
        ApplicationManager.getApplication().invokeAndWait({ project.messageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logCreated(result) },
                                                          modality)
      }
      return result
    }

    @RequiresEdt
    fun dropValue(): VcsLogManager? {
      ApplicationManager.getApplication().assertIsDispatchThread()
      val oldValue = cached
      if (oldValue != null) {
        cached = null
        LOG.debug("Disposing Vcs Log for ${VcsLogUtil.getProvidersMapText(oldValue.dataManager.logProviders)}")
        project.messageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logDisposed(oldValue)
        return oldValue
      }
      return null
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
      withContext(projectLog.executor.asCoroutineDispatcher()) {
        projectLog.createLog(forceInit = false)
      }
    }
  }

  private inner class MyDynamicPluginUnloader : DynamicPluginListener {
    private val affectedPlugins: MutableSet<PluginId> = HashSet()
    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
      if (hasLogExtensions(pluginDescriptor)) {
        disposeLog(true)
      }
    }

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
      if (hasLogExtensions(pluginDescriptor)) {
        affectedPlugins.add(pluginDescriptor.pluginId)
        LOG.debug("Disposing Vcs Log before unloading " + pluginDescriptor.pluginId)
        disposeLog(false)
      }
    }

    override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
      if (affectedPlugins.remove(pluginDescriptor.pluginId)) {
        LOG.debug("Recreating Vcs Log after unloading " + pluginDescriptor.pluginId)
        // createLog calls between beforePluginUnload and pluginUnloaded are technically not prohibited
        //  so, just in case, recreating log here
        disposeLog(true)
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
    private val LOG = logger<VcsProjectLog>()

    @Topic.ProjectLevel
    @JvmField
    val VCS_PROJECT_LOG_CHANGED = Topic(ProjectLogListener::class.java, Topic.BroadcastDirection.NONE, true)

    @RequiresBackgroundThread
    private fun initialize(logManager: VcsLogManager, force: Boolean) {
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

      invokeLater(modality) {
        if (logManager.isLogVisible) {
          logManager.scheduleInitialization()
        }
      }
    }

    @JvmStatic
    fun getLogProviders(project: Project): Map<VirtualFile, VcsLogProvider> {
      return VcsLogManager.findLogProviders(ProjectLevelVcsManager.getInstance(project).allVcsRoots.toList(), project)
    }

    @JvmStatic
    fun getInstance(project: Project): VcsProjectLog = project.service<VcsProjectLog>()

    private fun <T> invokeAndWait(computable: Supplier<T>): T {
      val result = Ref<T>()
      ApplicationManager.getApplication().invokeAndWait({ result.set(computable.get()) }, modality)
      return result.get()
    }

    // Using "any" modality specifically is required in order to be able to wait for log initialization or disposal under modal progress.
    // Otherwise, methods such as "VcsProjectLog#runWhenLogIsReady" or "VcsProjectLog.shutDown" won't be able to work
    // when "disposeLog" is queued as "invokeAndWait"
    // (used there in order to ensure sequential execution) will the app freeze when modal progress is displayed.
    private val modality: ModalityState get() = ModalityState.any()

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
      val futureResult = log.createLogInBackground(true)
      object : Task.Backgroundable(project, VcsLogBundle.message("vcs.log.creating.process")) {
        override fun run(indicator: ProgressIndicator) {
          try {
            futureResult.get(5, TimeUnit.SECONDS)
          }
          catch (ignored: InterruptedException) {
          }
          catch (e: ExecutionException) {
            LOG.error(e)
          }
          catch (e: TimeoutException) {
            LOG.warn(e)
          }
        }

        override fun onSuccess() {
          log.logManager?.let {
            action(it)
          }
        }
      }.queue()
    }

    fun waitWhenLogIsReady(project: Project): Future<Boolean> {
      val log = getInstance(project)
      val manager = log.logManager
      return if (manager == null) log.createLogInBackground(true) else CompletableFuture.completedFuture(true)
    }

    @ApiStatus.Internal
    @RequiresBackgroundThread
    fun ensureLogCreated(project: Project): Boolean {
      ApplicationManager.getApplication().assertIsNonDispatchThread()
      try {
        return waitWhenLogIsReady(project).get() == true
      }
      catch (ignored: InterruptedException) {
      }
      catch (e: ExecutionException) {
        LOG.error(e)
      }
      return false
    }
  }
}