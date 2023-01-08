// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.configurationStore.StateStorageManager
import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.serviceContainer.executeRegisterTaskForOldContent
import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

private val LOG = logger<ClientSessionImpl>()

@ApiStatus.Experimental
@ApiStatus.Internal
abstract class ClientSessionImpl(
  final override val clientId: ClientId,
  final override val type: ClientType,
  private val sharedComponentManager: ClientAwareComponentManager
) : ComponentManagerImpl(null, false), ClientSession {

  override val isLightServiceSupported = false
  override val isMessageBusSupported = false

  init {
    @Suppress("LeakingThis")
    registerServiceInstance(ClientSession::class.java, this, fakeCorePluginDescriptor)
  }

  fun registerServices() {
    registerComponents()
  }

  fun preloadServices(syncScope: CoroutineScope) {
    assert(containerState.get() == ContainerState.PRE_INIT)
    val exceptionHandler = CoroutineExceptionHandler { _, exception ->
      LOG.error(exception)
    }
    this.preloadServices(modules = PluginManagerCore.getPluginSet().getEnabledModules(),
                         activityPrefix = "client ",
                         syncScope = syncScope + exceptionHandler,
                         onlyIfAwait = false
    )
    assert(containerState.compareAndSet(ContainerState.PRE_INIT, ContainerState.COMPONENT_CREATED))
  }

  override suspend fun preloadService(service: ServiceDescriptor): Job? {
    return ClientId.withClientId(clientId) {
      super.preloadService(service)
    }
  }

  override fun isServiceSuitable(descriptor: ServiceDescriptor): Boolean {
    return descriptor.client?.let { type.matches(it) } ?: false
  }

  /**
   * only per-client services are supported (no components, extensions, listeners)
   */
  override fun registerComponents(modules: List<IdeaPluginDescriptorImpl>,
                                  app: Application?,
                                  precomputedExtensionModel: PrecomputedExtensionModel?,
                                  listenerCallbacks: MutableList<in Runnable>?) {
    for (rootModule in modules) {
      registerServices(getContainerDescriptor(rootModule).services, rootModule)
      executeRegisterTaskForOldContent(rootModule) { module ->
        registerServices(getContainerDescriptor(module).services, module)
      }
    }
  }

  override fun isComponentSuitable(componentConfig: ComponentConfig): Boolean {
    LOG.error("components aren't supported")
    return false
  }

  override fun <T : Any> doGetService(serviceClass: Class<T>, createIfNeeded: Boolean): T? {
    return doGetService(serviceClass, createIfNeeded, true)
  }

  fun <T : Any> doGetService(serviceClass: Class<T>, createIfNeeded: Boolean, fallbackToShared: Boolean): T? {
    val clientService = ClientId.withClientId(clientId) { super.doGetService(serviceClass, createIfNeeded) }
    if (clientService != null || !fallbackToShared) return clientService

    if (createIfNeeded && !type.isLocal) {
      val sessionsManager = sharedComponentManager.getService(ClientSessionsManager::class.java)
      val localSession = sessionsManager?.getSession(ClientId.localId) as? ClientSessionImpl

      if (localSession?.doGetService(serviceClass, createIfNeeded = true, fallbackToShared = false) != null) {
        LOG.error("$serviceClass is registered only for client=\"local\", " +
                  "please provide a guest-specific implementation, or change to client=\"all\"")
        return null
      }
    }

    ClientId.withClientId(ClientId.localId) {
      return if (createIfNeeded) {
        sharedComponentManager.getService(serviceClass)
      }
      else {
        sharedComponentManager.getServiceIfCreated(serviceClass)
      }
    }
  }

  override fun getApplication(): Application? {
    return sharedComponentManager.getApplication()
  }

  override val componentStore = ClientSessionComponentStore()

  @Deprecated("sessions don't have their own message bus", level = DeprecationLevel.ERROR)
  override fun getMessageBus(): MessageBus {
    error("Not supported")
  }

  override fun toString(): String {
    return clientId.toString()
  }
}

class ClientSessionComponentStore : IComponentStore {
  override val storageManager: StateStorageManager
    get() = throw UnsupportedOperationException()

  override fun setPath(path: Path) {
  }

  override fun initComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId?) {
    if (component is PersistentStateComponent<*>) {
      LOG.error("Persisting is not supported for per-client services. Loading ${component::class.java.name} with default values")
    }
  }

  override fun unloadComponent(component: Any) {
  }

  override fun initPersistencePlainComponent(component: Any, key: String) {
  }

  override fun reloadStates(componentNames: Set<String>, messageBus: MessageBus) {
  }

  override fun reloadState(componentClass: Class<out PersistentStateComponent<*>>) {
  }

  override fun isReloadPossible(componentNames: Set<String>): Boolean {
    return false
  }

  override suspend fun save(forceSavingAllSettings: Boolean) {
  }

  override fun saveComponent(component: PersistentStateComponent<*>) {
  }

  override fun removeComponent(name: String) {
  }

  override fun clearCaches() {
  }

  override fun release() {
  }
}

@ApiStatus.Internal
open class ClientAppSessionImpl(
  clientId: ClientId,
  clientType: ClientType,
  application: ApplicationImpl
) : ClientSessionImpl(clientId, clientType, application), ClientAppSession {
  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor {
    return pluginDescriptor.appContainerDescriptor
  }

  init {
    @Suppress("LeakingThis")
    registerServiceInstance(ClientAppSession::class.java, this, fakeCorePluginDescriptor)
  }
}

@ApiStatus.Internal
open class ClientProjectSessionImpl(
  clientId: ClientId,
  clientType: ClientType,
  final override val project: ProjectImpl,
) : ClientSessionImpl(clientId, clientType, project), ClientProjectSession {
  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor {
    return pluginDescriptor.projectContainerDescriptor
  }

  init {
    registerServiceInstance(ClientProjectSession::class.java, this, fakeCorePluginDescriptor)
    registerServiceInstance(Project::class.java, project, fakeCorePluginDescriptor)
  }

  override val appSession: ClientAppSession
    get() = ClientSessionsManager.getAppSession(clientId)!!
}
