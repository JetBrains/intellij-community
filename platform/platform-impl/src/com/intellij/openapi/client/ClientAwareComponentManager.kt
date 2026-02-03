// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.diagnostic.logger
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.throwAlreadyDisposedError
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

private val logger = logger<ClientAwareComponentManager>()

@ApiStatus.Internal
abstract class ClientAwareComponentManager: ComponentManagerImpl {
  protected constructor(parent: ComponentManagerImpl) : super(parent)

  protected constructor(parentScope: CoroutineScope): super(parentScope)

  override fun <T : Any> getServiceForClient(serviceClass: Class<T>): T? {
    return postGetService(serviceClass, createIfNeeded = true)
  }

  override fun <T : Any> getServices(serviceClass: Class<T>, clientKind: ClientKind): List<T> {
    val sessionsManager = super.getService(ClientSessionsManager::class.java)!!
    return sessionsManager.getSessions(clientKind).mapNotNull {
      (it as? ClientSessionImpl)?.doGetService(serviceClass = serviceClass, createIfNeeded = true, fallbackToShared = false)
    }
  }

  override fun <T : Any> postGetService(serviceClass: Class<T>, createIfNeeded: Boolean): T? {
    val sessionManager: ClientSessionsManager<*>?
    if (containerState.get() == ContainerState.DISPOSE_COMPLETED) {
      if (createIfNeeded) {
        throwAlreadyDisposedError(serviceDescription = serviceClass.name, componentManager = this)
      }
      sessionManager = super.doGetService(serviceClass = ClientSessionsManager::class.java, createIfNeeded = false)
    }
    else {
      sessionManager = super.doGetService(serviceClass = ClientSessionsManager::class.java, createIfNeeded = true)
    }

    val clientId = ClientId.currentOrNull
    val session = sessionManager?.getSession(clientId ?: ClientId.localId) as? ClientSessionImpl
    val service = session?.doGetService(serviceClass = serviceClass, createIfNeeded = createIfNeeded, fallbackToShared = false)
    if (clientId == null && service != null && ClientId.absenceBehaviorValue == ClientId.AbsenceBehavior.LOG_ERROR) {
      logger.error("Requested existing per-client service '${service.javaClass.name}' under missing ClientId. " +
                   "Host implementation will be returned, but calling code has to be fixed: either set/promote ClientId " +
                   "or mark the service as non per-client")
    }
    if (service == null && session?.type == ClientType.FRONTEND) {
      // if current ClientId is a frontend one and Frontend session doesn't have the service,
      // we need to try looking in the local session as a fallback
      // use case: coroutine has a frontend ClientId -> some code requests for a service registered as 'local' in the xml
      val localSession = sessionManager.getSession(ClientId.localId) as? ClientSessionImpl ?: return null
      return localSession.doGetService(serviceClass = serviceClass, createIfNeeded = createIfNeeded, fallbackToShared = false)
    }
    return service
  }

  final override fun registerComponents(
    modules: List<IdeaPluginDescriptorImpl>,
    app: Application?,
    listenerCallbacks: MutableList<in Runnable>?
  ) {
    super.registerComponents(modules = modules,
                             app = app,
                             listenerCallbacks = listenerCallbacks)

    val sessionsManager = super.getService(ClientSessionsManager::class.java)!!
    for (session in sessionsManager.getSessions(ClientKind.ALL)) {
      (session as? ClientSessionImpl)?.registerComponents(modules = modules,
                                                          app = app,
                                                          listenerCallbacks = listenerCallbacks)
    }
  }

  final override fun unloadServices(module: IdeaPluginDescriptor, services: List<ServiceDescriptor>) {
    super.unloadServices(module, services)

    val sessionsManager = super.getService(ClientSessionsManager::class.java)!!
    for (session in sessionsManager.getSessions(ClientKind.ALL)) {
      (session as? ClientSessionImpl)?.unloadServices(module, services)
    }
  }

  final override fun postPreloadServices(modules: List<IdeaPluginDescriptorImpl>,
                                   activityPrefix: String,
                                   syncScope: CoroutineScope,
                                   onlyIfAwait: Boolean) {
    val sessionsManager = super.getService(ClientSessionsManager::class.java)!!
    for (session in sessionsManager.getSessions(ClientKind.ALL)) {
      session as? ClientSessionImpl ?: continue
      session.preloadServices(modules, activityPrefix, syncScope, onlyIfAwait, getCoroutineScope())
    }
  }

  final override fun isPreInitialized(service: Any): Boolean {
    return super.isPreInitialized(service) || service is ClientSessionsManager<*>
  }
}