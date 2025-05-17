// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.codeWithMe.asContextElement
import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.openapi.project.impl.projectAndScopeMethodType
import com.intellij.openapi.project.impl.projectMethodType
import com.intellij.platform.kernel.util.kernelCoroutineContext
import com.intellij.platform.util.coroutines.childScope
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.serviceContainer.executeRegisterTaskForOldContent
import com.intellij.serviceContainer.findConstructorOrNull
import com.intellij.util.SystemProperties
import com.intellij.util.messages.MessageBus
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

private val LOG: Logger
  get() = logger<ClientSessionImpl>()

@OptIn(DelicateCoroutinesApi::class)
@ApiStatus.Experimental
@ApiStatus.Internal
abstract class ClientSessionImpl(
  final override val clientId: ClientId,
  final override val type: ClientType,
  private val sharedComponentManager: ClientAwareComponentManager
) : ComponentManagerImpl(
  parent = null,
  parentScope = GlobalScope.childScope(
    "Client[$clientId] Session scope",
    context = sharedComponentManager.getCoroutineScope().coroutineContext.kernelCoroutineContext()
  ),
  additionalContext = clientId.asContextElement(),
), ClientSession {
  final override val isLightServiceSupported: Boolean = false
  final override val isMessageBusSupported: Boolean = false

  init {
    @Suppress("LeakingThis")
    registerServiceInstance(ClientSession::class.java, this, fakeCorePluginDescriptor)
  }

  override fun <T : Any> findConstructorAndInstantiateClass(lookup: MethodHandles.Lookup, aClass: Class<T>): T {
    @Suppress("UNCHECKED_CAST")
    return (lookup.findConstructorOrNull(aClass, sessionConstructorMethodType)?.invoke(this) as T?)
           ?: super.findConstructorAndInstantiateClass(lookup, aClass)
  }

  override val supportedSignaturesOfLightServiceConstructors: List<MethodType> = persistentListOf(
    sessionConstructorMethodType,
  ).addAll(super.supportedSignaturesOfLightServiceConstructors)

  fun preloadServices(syncScope: CoroutineScope) {
    assert(containerState.get() == ContainerState.PRE_INIT)
    val exceptionHandler = CoroutineExceptionHandler { _, exception ->
      LOG.error(exception)
    }
    this.preloadServices(modules = PluginManagerCore.getPluginSet().getEnabledModules(),
                         activityPrefix = "client ",
                         syncScope = syncScope + exceptionHandler,
                         onlyIfAwait = false,
                         asyncScope = getCoroutineScope())
    assert(containerState.compareAndSet(ContainerState.PRE_INIT, ContainerState.COMPONENT_CREATED))
  }

  final override suspend fun preloadService(service: ServiceDescriptor, serviceInterface: String) {
    return withContext(clientId.asContextElement()) {
      super.preloadService(service, serviceInterface)
    }
  }

  final override fun isServiceSuitable(descriptor: ServiceDescriptor): Boolean {
    return descriptor.client?.let { type.matches(it) } ?: false
  }

  /**
   * only per-client services are supported (no components, extensions, listeners)
   */
  final override fun registerComponents(modules: List<IdeaPluginDescriptorImpl>,
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

  final override fun isComponentSuitable(componentConfig: ComponentConfig): Boolean {
    LOG.error("components aren't supported")
    return false
  }

  final override fun <T : Any> doGetService(serviceClass: Class<T>, createIfNeeded: Boolean): T? {
    return doGetService(serviceClass, createIfNeeded, true)
  }

  fun <T : Any> doGetService(serviceClass: Class<T>, createIfNeeded: Boolean, fallbackToShared: Boolean): T? {
    if (!fallbackToShared && !createIfNeeded && !hasComponent(serviceClass)) return null

    val clientService = ClientId.withExplicitClientId(clientId) {
      super.doGetService(serviceClass = serviceClass, createIfNeeded = createIfNeeded)
    }
    if (clientService != null || !fallbackToShared) {
      return clientService
    }

    // frontend service as well as a local one should be redirected to a shared in the case when fallbackToShared == true
    if (createIfNeeded && !type.isLocal && !type.isFrontend) {
      val sessionsManager = sharedComponentManager.getService(ClientSessionsManager::class.java)
      val localSession = sessionsManager?.getSession(ClientId.localId) as? ClientSessionImpl

      if (localSession?.doGetService(serviceClass, createIfNeeded = true, fallbackToShared = false) != null) {
        LOG.error("$serviceClass is registered only for client=\"local\", " +
                  "please provide a guest-specific implementation, or change to client=\"all\"")
        return null
      }
    }

    return ClientId.withExplicitClientId(ClientId.localId) {
      return@withExplicitClientId if (createIfNeeded) {
        sharedComponentManager.getService(serviceClass)
      }
      else {
        sharedComponentManager.getServiceIfCreated(serviceClass)
      }
    }
  }

  final override fun getApplication(): Application? {
    return sharedComponentManager.getApplication()
  }

  override val componentStore: IComponentStore
    get() = sharedComponentManager.componentStore

  @Deprecated("sessions don't have their own message bus", level = DeprecationLevel.ERROR)
  final override fun getMessageBus(): MessageBus {
    error("Not supported")
  }

  final override fun toString(): String {
    return "${javaClass.name}(type=$type, clientId=$clientId)"
  }

  override fun debugString(short: Boolean): String {
    val className = if (short) javaClass.simpleName else javaClass.name
    return "$className::$type#$clientId"
  }
}

@ApiStatus.Internal
abstract class ClientAppSessionImpl(
  clientId: ClientId,
  clientType: ClientType,
  application: ApplicationImpl
) : ClientSessionImpl(clientId, clientType, application), ClientAppSession {
  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor {
    return pluginDescriptor.appContainerDescriptor
  }

  override val projectSessions: List<ClientProjectSession>
    get() {
      return ProjectManager.getInstance().openProjects.mapNotNull {
        it.service<ClientSessionsManager<*>>().getSession(clientId) as? ClientProjectSession
      }
    }

  init {
    @Suppress("LeakingThis")
    registerServiceInstance(ClientAppSession::class.java, this, fakeCorePluginDescriptor)
  }

  override val supportedSignaturesOfLightServiceConstructors: List<MethodType> = persistentListOf(
    appSessionConstructorMethodType,
    appSessionAndScopeConstructorMethodType
  ).addAll(super.supportedSignaturesOfLightServiceConstructors)
}

private val sessionConstructorMethodType = MethodType.methodType(Void.TYPE, ClientAppSession::class.java)

private val projectSessionConstructorMethodType = MethodType.methodType(Void.TYPE, ClientProjectSession::class.java)
private val projectSessionAndScopeConstructorMethodType =
  MethodType.methodType(Void.TYPE, ClientProjectSession::class.java, CoroutineScope::class.java)

private val appSessionConstructorMethodType = MethodType.methodType(Void.TYPE, ClientAppSession::class.java)
private val appSessionAndScopeConstructorMethodType =
  MethodType.methodType(Void.TYPE, ClientAppSession::class.java, CoroutineScope::class.java)

@Suppress("LeakingThis")
@ApiStatus.Internal
open class ClientProjectSessionImpl(
  clientId: ClientId,
  clientType: ClientType,
  componentManager: ClientAwareComponentManager,
  final override val project: Project,
) : ClientSessionImpl(clientId, clientType, componentManager), ClientProjectSession {
  constructor(clientId: ClientId, clientType: ClientType, project: ProjectImpl) : this(clientId = clientId,
                                                                                       clientType = clientType,
                                                                                       componentManager = project,
                                                                                       project = project)

  override fun <T : Any> findConstructorAndInstantiateClass(lookup: MethodHandles.Lookup, aClass: Class<T>): T {
    @Suppress("UNCHECKED_CAST")
    return ((lookup.findConstructorOrNull(aClass, projectMethodType)?.invoke(project)
            ?: lookup.findConstructorOrNull(aClass, projectSessionConstructorMethodType)?.invoke(this) ) as T?)
           ?: super.findConstructorAndInstantiateClass(lookup, aClass)
  }

  override val supportedSignaturesOfLightServiceConstructors: List<MethodType> = persistentListOf(
    projectMethodType,
    projectAndScopeMethodType,
    projectSessionConstructorMethodType,
    projectSessionAndScopeConstructorMethodType,
  ).addAll(super.supportedSignaturesOfLightServiceConstructors)

  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor {
    return pluginDescriptor.projectContainerDescriptor
  }

  init {
    registerServiceInstance(ClientProjectSession::class.java, this, fakeCorePluginDescriptor)
    registerServiceInstance(Project::class.java, project, fakeCorePluginDescriptor)
  }

  override val appSession: ClientAppSession
    get() = ClientSessionsManager.getAppSession(clientId)!!

  override val name: String
    get() = appSession.name
}

@ApiStatus.Experimental
@ApiStatus.Internal
open class LocalAppSessionImpl(application: ApplicationImpl) : ClientAppSessionImpl(ClientId.localId, ClientType.LOCAL, application) {
  override val name: String
    // see `com.intellij.remoteDev.util.LocalUserSettings`
    get() = PropertiesComponent.getInstance().getValue("local.user.name", SystemProperties.getUserName())
}

@ApiStatus.Experimental
@ApiStatus.Internal
open class LocalProjectSessionImpl(
  componentManager: ClientAwareComponentManager,
  project: Project
) : ClientProjectSessionImpl(ClientId.localId, ClientType.LOCAL, componentManager, project) {
  constructor(project: ProjectImpl) : this(componentManager = project, project = project)
}
