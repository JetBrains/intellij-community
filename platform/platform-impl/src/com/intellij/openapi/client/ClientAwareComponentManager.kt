// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.client

import com.intellij.codeWithMe.ClientId
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.application.Application
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.PrecomputedExtensionModel
import com.intellij.serviceContainer.throwAlreadyDisposedError
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture

@ApiStatus.Internal
abstract class ClientAwareComponentManager @JvmOverloads constructor(
  internal val parent: ComponentManagerImpl?,
  setExtensionsRootArea: Boolean = parent == null) : ComponentManagerImpl(parent, setExtensionsRootArea) {

  override fun <T : Any> getService(serviceClass: Class<T>): T? {
    return getFromSelfOrCurrentSession(serviceClass, true)
  }

  override fun <T : Any> getServiceIfCreated(serviceClass: Class<T>): T? {
    return getFromSelfOrCurrentSession(serviceClass, false)
  }

  override fun <T : Any> getServices(serviceClass: Class<T>, includeLocal: Boolean): List<T> {
    val sessionsManager = super.getService(ClientSessionsManager::class.java)!!
    return sessionsManager.getSessions(includeLocal)
      .mapNotNull { (it as? ClientSessionImpl)?.doGetService(serviceClass, true,  false) }
  }

  private fun <T : Any> getFromSelfOrCurrentSession(serviceClass: Class<T>, createIfNeeded: Boolean): T? {
    val fromSelf = if (createIfNeeded) {
      super.getService(serviceClass)
    }
    else {
      super.getServiceIfCreated(serviceClass)
    }

    if (fromSelf != null) return fromSelf

    val sessionsManager = if (containerState.get() == ContainerState.DISPOSE_COMPLETED) {
      if (createIfNeeded) {
        throwAlreadyDisposedError(serviceClass.name, this, ProgressIndicatorProvider.getGlobalProgressIndicator())
      }
      super.doGetService(ClientSessionsManager::class.java, false)
    }
    else {
      super.getService(ClientSessionsManager::class.java)
    }

    val session = sessionsManager?.getSession(ClientId.current) as? ClientSessionImpl
    return session?.doGetService(serviceClass, createIfNeeded, false)
  }

  override fun registerComponents(modules: Sequence<IdeaPluginDescriptorImpl>,
                                  app: Application?,
                                  precomputedExtensionModel: PrecomputedExtensionModel?,
                                  listenerCallbacks: List<Runnable>?) {
    super.registerComponents(modules, app, precomputedExtensionModel, listenerCallbacks)

    val sessionsManager = super.getService(ClientSessionsManager::class.java)!!
    for (session in sessionsManager.getSessions(true)) {
      (session as? ClientSessionImpl)?.registerComponents(modules, app, precomputedExtensionModel, listenerCallbacks)
    }
  }

  override fun unloadServices(services: List<ServiceDescriptor>, pluginId: PluginId) {
    super.unloadServices(services, pluginId)

    val sessionsManager = super.getService(ClientSessionsManager::class.java)!!
    for (session in sessionsManager.getSessions(true)) {
      (session as? ClientSessionImpl)?.unloadServices(services, pluginId)
    }
  }

  override fun preloadServices(modules: Sequence<IdeaPluginDescriptorImpl>,
                               activityPrefix: String,
                               onlyIfAwait: Boolean): PreloadServicesResult {
    val result = super.preloadServices(modules, activityPrefix, onlyIfAwait)
    val sessionsManager = super.getService(ClientSessionsManager::class.java)!!

    val syncPreloadFutures = mutableListOf<CompletableFuture<*>>()
    val asyncPreloadFutures = mutableListOf<CompletableFuture<*>>()
    for (session in sessionsManager.getSessions(true)) {
      session as? ClientSessionImpl ?: continue
      val sessionResult = session.preloadServices(modules, activityPrefix, onlyIfAwait)
      syncPreloadFutures.add(sessionResult.sync)
      asyncPreloadFutures.add(sessionResult.async)
    }

    return PreloadServicesResult(
      sync = CompletableFuture.allOf(result.sync, *syncPreloadFutures.toTypedArray()),
      async = CompletableFuture.allOf(result.async, *asyncPreloadFutures.toTypedArray())
    )
  }

  override fun isPreInitialized(component: Any): Boolean {
    return super.isPreInitialized(component) || component is ClientSessionsManager<*>
  }
}