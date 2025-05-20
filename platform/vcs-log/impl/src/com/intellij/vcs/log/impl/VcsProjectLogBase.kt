// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.ide.caches.CachesInvalidator
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.application.impl.RawSwingDispatcher
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ShutDownTracker
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsMappingListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.PlatformUtils
import com.intellij.util.awaitCancellationAndInvoke
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.index.PhmVcsLogStorageBackend
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

private val LOG: Logger
  get() = logger<VcsProjectLogBase<*>>()

private val CLOSE_LOG_TIMEOUT = 10.seconds

internal abstract class VcsProjectLogBase<M : VcsLogManager>(
  protected val project: Project,
  protected val coroutineScope: CoroutineScope,
) : VcsProjectLog() {
  protected val errorHandler by lazy { VcsProjectLogErrorHandler(this, coroutineScope) }

  private val logManagerState = MutableStateFlow<M?>(null)

  private val disposeStarted = AtomicBoolean(false)
  val isDisposing: Boolean get() = disposeStarted.get()

  // not-reentrant - invoking [lock] even from the same thread/coroutine that currently holds the lock still suspends the invoker
  private val mutex = Mutex()

  final override val logManager: M? get() = logManagerState.value

  final override val dataManager: VcsLogData? get() = logManager?.dataManager

  private val listenersDisposable = Disposer.newDisposable()

  init {
    val busConnection = project.messageBus.connect(listenersDisposable)
    busConnection.subscribe(ProjectLevelVcsManager.VCS_CONFIGURATION_CHANGED, VcsMappingListener {
      LOG.debug("Recreating Vcs Log after roots changed")
      reinitAsync()
    })
    busConnection.subscribe(DynamicPluginListener.TOPIC, MyDynamicPluginUnloader())
    VcsLogData.getIndexingRegistryValue().addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        LOG.debug("Recreating Vcs Log after indexing registry value changed")
        reinitAsync()
      }
    }, listenersDisposable)
    project.service<VcsLogSharedSettings>().addListener(VcsLogSharedSettings.Listener {
      LOG.debug("Recreating Vcs Log after settings changed")
      reinitAsync()
    }, listenersDisposable)
    PhmVcsLogStorageBackend.durableEnumeratorRegistryProperty.addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        LOG.debug("Recreating Vcs Log after durable enumerator registry value changed")
        coroutineScope.launchWithAnyModality { logManager?.let { invalidateCaches(it) } }
      }
    }, listenersDisposable)

    @Suppress("SSBasedInspection")
    val shutdownTask = object : Runnable {
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
          disposeLogInternal(useRawSwingDispatcher, notify = false)
        }
      }
    }
    catch (e: TimeoutCancellationException) {
      LOG.error("Cannot close VCS log in ${CLOSE_LOG_TIMEOUT.inWholeSeconds} seconds")
    }
  }

  final override fun runWhenLogIsReady(action: (VcsLogManager) -> Unit) = doRunWhenLogIsReady(action)

  protected fun doRunWhenLogIsReady(action: (M) -> Unit) {
    val manager = logManager
    if (manager != null) {
      action(manager)
      return
    }

    // schedule showing the log, wait its initialization, and then open the tab
    coroutineScope.launch {
      withBackgroundProgress(project, VcsLogBundle.message("vcs.log.creating.process")) {
        val logManager = init(true)
        if (logManager != null) {
          withContext(Dispatchers.EDT) {
            action(logManager)
          }
        }
      }
    }
  }

  final override suspend fun init(force: Boolean): M? = initAsync(force).await()

  fun initAsync(forceInit: Boolean): Deferred<M?> =
    coroutineScope.asyncWithAnyModality {
      mutex.withLock {
        getOrCreateLogManager(forceInit)
      }
    }

  private fun disposeAsync(useRawSwingDispatcher: Boolean = false) =
    coroutineScope.launchWithAnyModality {
      mutex.withLock {
        disposeLogInternal(useRawSwingDispatcher, notify = true)
      }
    }

  final override suspend fun reinit(beforeCreateLog: (suspend () -> Unit)?): M? = reinitAsync(beforeCreateLog).await()

  private fun reinitAsync(beforeCreateLog: (suspend () -> Unit)? = null) =
    coroutineScope.asyncWithAnyModality {
      mutex.withLock {
        disposeLogInternal(useRawSwingDispatcher = false, notify = true)

        try {
          beforeCreateLog?.invoke()
        }
        catch (e: Throwable) {
          LOG.error("Unable to execute 'beforeCreateLog'", e)
        }

        getOrCreateLogManager(forceInit = false)
      }
    }

  private suspend fun disposeLogInternal(useRawSwingDispatcher: Boolean, notify: Boolean) {
    val logManager = withContext(if (useRawSwingDispatcher) RawSwingDispatcher else Dispatchers.EDT) {
      dropLogManager(notify)?.also { it.disposeUi() }
    }
    if (logManager != null) Disposer.dispose(logManager)
  }

  private suspend fun getOrCreateLogManager(forceInit: Boolean): M? {
    if (isDisposing || PlatformUtils.isQodana()) return null

    val projectLevelVcsManager = project.serviceAsync<ProjectLevelVcsManager>()
    val logProviders = VcsLogManager.findLogProviders(projectLevelVcsManager.allVcsRoots.toList(), project)
    if (logProviders.isEmpty()) return null

    return project.trackActivity(VcsActivityKey) {
      getOrCreateLogManager(logProviders).apply {
        initialize(forceInit)
      }
    }
  }

  private suspend fun getOrCreateLogManager(logProviders: Map<VirtualFile, VcsLogProvider>): M {
    logManagerState.value?.let {
      return it
    }

    LOG.debug { "Creating ${getProjectLogName(logProviders)}" }
    return createLogManager(logProviders).also { manager ->
      logManagerState.value = manager
      val publisher = project.messageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED)
      withContext(Dispatchers.EDT) {
        publisher.logCreated(manager)
      }
    }
  }

  protected abstract suspend fun createLogManager(logProviders: Map<VirtualFile, VcsLogProvider>): M

  @RequiresEdt
  private fun dropLogManager(notify: Boolean): M? {
    ThreadingAssertions.assertEventDispatchThread()
    val oldValue = logManagerState.getAndUpdate { null } ?: return null

    LOG.debug { "Disposing ${oldValue.name}" }
    if (notify) {
      project.messageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logDisposed(oldValue)
    }
    return oldValue
  }

  private inner class MyDynamicPluginUnloader : DynamicPluginListener {
    private val affectedPlugins = HashSet<PluginId>()

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
      if (hasLogExtensions(pluginDescriptor)) {
        LOG.debug { "Disposing Vcs Log after loading ${pluginDescriptor.pluginId}" }
        reinitAsync()
      }
    }

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
      if (hasLogExtensions(pluginDescriptor)) {
        affectedPlugins.add(pluginDescriptor.pluginId)
        LOG.debug { "Disposing Vcs Log before unloading ${pluginDescriptor.pluginId}" }
        disposeAsync()
      }
    }

    override fun pluginUnloaded(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
      if (affectedPlugins.remove(pluginDescriptor.pluginId)) {
        LOG.debug { "Recreating Vcs Log after unloading ${pluginDescriptor.pluginId}" }
        // createLog calls between beforePluginUnload and pluginUnloaded are technically not prohibited
        // so, just in case, recreating log here
        reinitAsync()
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
}

private suspend fun VcsLogManager.initialize(force: Boolean) {
  if (force) {
    blockingContext {
      scheduleInitialization()
    }
    return
  }

  if (VcsLogManager.keepUpToDate()) {
    val invalidator = CachesInvalidator.EP_NAME.findExtensionOrFail(VcsLogCachesInvalidator::class.java)
    if (invalidator.isValid) {
      blockingContext {
        scheduleInitialization()
      }
      return
    }
  }

  withContext(Dispatchers.EDT) {
    blockingContext {
      scheduleInitialization(false)
    }
  }
}

/**
 * Launches the coroutine with "any" modality in the context.
 * Using "any" modality is required in order to create or dispose log when modal dialog (such as Settings dialog) is shown.
 */
private fun CoroutineScope.launchWithAnyModality(block: suspend CoroutineScope.() -> Unit): Job {
  return launch(ModalityState.any().asContextElement(), block = block)
}

private fun <T> CoroutineScope.asyncWithAnyModality(block: suspend CoroutineScope.() -> T): Deferred<T> {
  return async(ModalityState.any().asContextElement(), block = block)
}
