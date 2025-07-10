// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.google.common.collect.EnumMultiset
import com.intellij.ide.caches.CachesInvalidator
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
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
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.data.index.PhmVcsLogStorageBackend
import com.intellij.vcs.log.data.index.VcsLogBigRepositoriesList
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.util.StorageId
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.seconds

private val LOG: Logger
  get() = logger<VcsProjectLogBase<*>>()

private val CLOSE_LOG_TIMEOUT = 10.seconds

@Internal
abstract class VcsProjectLogBase<M : VcsLogManager>(
  protected val project: Project,
  protected val coroutineScope: CoroutineScope,
) : VcsProjectLog() {
  private val _logManagerState = MutableStateFlow<M?>(null)
  override val logManagerState: StateFlow<M?> = _logManagerState.asStateFlow()
  private val errorCountBySource = EnumMultiset.create(VcsLogErrorHandler.Source::class.java)

  private val shutDownStarted = AtomicBoolean(false)
  val isDisposing: Boolean get() = shutDownStarted.get()

  // not-reentrant - invoking [lock] even from the same thread/coroutine that currently holds the lock still suspends the invoker
  private val mutex = Mutex()

  final override val logManager: M? get() = _logManagerState.value

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
        reinitAsync(invalidateCaches = true)
      }
    }, listenersDisposable)

    @Suppress("SSBasedInspection")
    val shutdownTask = object : Runnable {
      override fun run() {
        if (shutDownStarted.get()) {
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
    if (!shutDownStarted.compareAndSet(false, true)) return

    Disposer.dispose(listenersDisposable)

    try {
      withTimeout(CLOSE_LOG_TIMEOUT) {
        mutex.withLock {
          _logManagerState.getAndUpdate { null }?.let {
            LOG.debug { "Disposing ${it.name}" }
            it.dispose(useRawSwingDispatcher = useRawSwingDispatcher)
          }
        }
      }
    }
    catch (e: TimeoutCancellationException) {
      LOG.error("Cannot close VCS log in ${CLOSE_LOG_TIMEOUT.inWholeSeconds} seconds")
    }
  }

  final override fun runWhenLogIsReady(action: (VcsLogManager) -> Unit): Unit = doRunWhenLogIsReady(action)

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

  private fun disposeAsync() =
    coroutineScope.asyncWithAnyModality {
      mutex.withLock {
        disposeLogManager()
      }
    }

  final override suspend fun reinit(invalidateCaches: Boolean) {
    reinitAsync(invalidateCaches).await()
  }

  private fun reinitAsync(invalidateCaches: Boolean = false) =
    coroutineScope.asyncWithAnyModality {
      mutex.withLock {
        reinitUnsafe(invalidateCaches)
      }
    }

  private suspend fun reinitUnsafe(invalidateCaches: Boolean = false) {
    disposeLogManager(invalidateCaches)
    getOrCreateLogManager(forceInit = false)
  }

  private suspend fun disposeLogManager(invalidateCaches: Boolean = false) {
    val manager = _logManagerState.getAndUpdate { null } ?: return

    LOG.debug { "Disposing ${manager.name}" }
    withContext(Dispatchers.EDT) {
      notifyLogDisposed(manager)
    }
    manager.dispose(clearStorage = invalidateCaches)
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
    _logManagerState.value?.let {
      return it
    }

    LOG.debug { "Creating ${getProjectLogName(logProviders)}" }
    val logManager = try {
      createLogManager(logProviders)
    }
    catch (ce: CancellationException) {
      throw ce
    }
    catch (e: Exception) {
      LOG.error("Failed to initialize log manager", e)
      throw e
    }
    _logManagerState.value = logManager
    withContext(Dispatchers.EDT) {
      notifyLogCreated(logManager)
    }
    return logManager
  }

  protected abstract suspend fun createLogManager(logProviders: Map<VirtualFile, VcsLogProvider>): M

  private suspend fun reinitOnError(source: VcsLogErrorHandler.Source, error: Throwable) {
    if (isDisposing) return
    mutex.withLock {
      if (isDisposing) return
      val logManager = logManagerState.value ?: return

      errorCountBySource.add(source)
      val count = errorCountBySource.count(source)

      if (source == VcsLogErrorHandler.Source.Index && count > DISABLE_INDEX_COUNT) {
        withContext(Dispatchers.EDT) {
          val rootsForIndexing = logManager.dataManager.index.indexingRoots
          LOG.error("Disabling indexing for ${rootsForIndexing.map { it.name }} due to corruption " +
                    "(count=$count).", error)
          rootsForIndexing.forEach { VcsLogBigRepositoriesList.getInstance().addRepository(it) }
        }
        reinitUnsafe(true)
        return
      }

      val invalidateCaches = count % INVALIDATE_CACHES_COUNT == 0
      if (invalidateCaches) {
        LOG.error("Invalidating Vcs Log caches after $source corruption (count=$count).", error)
      }
      else {
        LOG.debug("Recreating Vcs Log after $source corruption (count=$count).", error)
      }

      reinitUnsafe(invalidateCaches)
    }
  }

  protected fun reinitOnErrorAsync(source: VcsLogErrorHandler.Source, error: Throwable) {
    if (isDisposing) return
    coroutineScope.asyncWithAnyModality {
      reinitOnError(source, error)
    }
  }

  private fun notifyLogCreated(oldManager: M) {
    ThreadingAssertions.assertEventDispatchThread()
    project.messageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logCreated(oldManager)
  }

  private fun notifyLogDisposed(oldManager: M) {
    ThreadingAssertions.assertEventDispatchThread()
    project.messageBus.syncPublisher(VCS_PROJECT_LOG_CHANGED).logDisposed(oldManager)
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

private const val INVALIDATE_CACHES_COUNT = 5
private const val DISABLE_INDEX_COUNT = 2 * INVALIDATE_CACHES_COUNT

internal fun VcsLogManager.storageIds(): List<StorageId> {
  return linkedSetOf((dataManager.index as? VcsLogPersistentIndex)?.indexStorageId,
                     (dataManager.storage as? VcsLogStorageImpl)?.refsStorageId,
                     (dataManager.storage as? VcsLogStorageImpl)?.hashesStorageId).filterNotNull()
}

/**
 * @param force run the initialization ignoring the invalid caches and the possible init delay in the manager
 */
private suspend fun VcsLogManager.initialize(force: Boolean) {
  if (force) {
    initialize()
    return
  }

  if (VcsLogManager.keepUpToDate()) {
    val invalidator = CachesInvalidator.EP_NAME.findExtensionOrFail(VcsLogCachesInvalidator::class.java)
    if (invalidator.isValid) {
      initialize()
      return
    }
  }

  withContext(Dispatchers.EDT) {
    initializeIfNeeded()
  }
}

/**
 * Launches the coroutine with "any" modality in the context.
 * Using "any" modality is required in order to create or dispose log when modal dialog (such as Settings dialog) is shown.
 */
private fun <T> CoroutineScope.asyncWithAnyModality(block: suspend CoroutineScope.() -> T): Deferred<T> {
  return async(ModalityState.any().asContextElement(), block = block)
}
