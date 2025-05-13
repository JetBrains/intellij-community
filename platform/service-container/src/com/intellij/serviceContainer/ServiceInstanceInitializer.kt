// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Disposer
import com.intellij.platform.instanceContainer.instantiation.InstantiationException
import com.intellij.platform.instanceContainer.instantiation.instantiate
import com.intellij.platform.instanceContainer.instantiation.withStoredTemporaryContext
import com.intellij.platform.instanceContainer.internal.InstanceInitializer
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException

internal abstract class ServiceInstanceInitializer(
  val componentManager: ComponentManagerImpl,
  private val pluginId: PluginId,
  private val serviceDescriptor: ServiceDescriptor?,
) : InstanceInitializer {

  override suspend fun createInstance(parentScope: CoroutineScope, instanceClass: Class<*>): Any {
    checkWriteAction(instanceClass)
    val instance = try {
      instantiate(resolver = componentManager.dependencyResolver,
                  parentScope = parentScope,
                  instanceClass = instanceClass,
                  supportedSignatures = componentManager.supportedSignaturesOfLightServiceConstructors)
    }
    catch (e: InstantiationException) {
      LOG.error(e)
      instantiateWithContainer(resolver = componentManager.dependencyResolver,
                               parentScope = parentScope,
                               instanceClass = instanceClass,
                               pluginId = pluginId)
    }
    catch (e: PluginException) {
      throw e
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      throw PluginException(e, pluginId)
    }

    if (instance is Disposable) {
      Disposer.register(componentManager.serviceParentDisposable, instance)
    }
    // If a service is requested during highlighting (under impatient=true),
    // then it's initialization might be broken forever.
    // Impatient reader is a property of thread (at the moment, before IJPL-53 is completed),
    // so it leaks to initializeComponent call, where it might cause ReadMostlyRWLock.throwIfImpatient() to throw,
    // for example, if a service obtains a read action in loadState.
    // Non-cancellable section is required to silence throwIfImpatient().
    // In general, we want initialization to be cancellable, and it must be cancelled only on parent scope cancellation,
    // which happens only on project/application shutdown, or on plugin unload.
    Cancellation.withNonCancelableSection().use {
      // loadState may invokeLater => don't capture the context
      withStoredTemporaryContext(parentScope) {
        componentManager.initializeService(instance, serviceDescriptor, pluginId)
      }
    }
    return instance
  }
}

internal open class ServiceDescriptorInstanceInitializer(
  private val keyClassName: String,
  override val instanceClassName: String,
  componentManager: ComponentManagerImpl,
  private val pluginDescriptor: PluginDescriptor,
  private val serviceDescriptor: ServiceDescriptor,
) : ServiceInstanceInitializer(componentManager, pluginDescriptor.pluginId, serviceDescriptor) {

  override fun loadInstanceClass(keyClass: Class<*>?): Class<*> {
    if (keyClass != null && keyClassName == instanceClassName) {
      // avoid classloading
      return keyClass
    }
    else {
      return doLoadClass(serviceDescriptor.implementation!!, pluginDescriptor)
    }
  }
}

internal class ServiceClassInstanceInitializer(
  componentManager: ComponentManagerImpl,
  private val instanceClass: Class<*>,
  pluginId: PluginId,
  serviceDescriptor: ServiceDescriptor?,
) : ServiceInstanceInitializer(componentManager, pluginId, serviceDescriptor) {

  override val instanceClassName: String get() = instanceClass.name

  override fun loadInstanceClass(keyClass: Class<*>?): Class<*> = instanceClass
}

private fun checkWriteAction(instanceClass: Class<*>) {
  if (!LOG.isDebugEnabled) {
    return
  }
  if (!checkServiceFromWriteAccess) {
    return
  }
  val app = ApplicationManager.getApplication()
            ?: return
  if (app.isWriteAccessAllowed && !app.isUnitTestMode && PersistentStateComponent::class.java.isAssignableFrom(instanceClass)) {
    LOG.warn(Throwable("Getting service from write-action leads to possible deadlock. Service implementation ${instanceClass.name}"))
  }
}

@ApiStatus.Internal
var checkServiceFromWriteAccess: Boolean = true
