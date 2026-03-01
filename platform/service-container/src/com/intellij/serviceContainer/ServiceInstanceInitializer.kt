// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.configurationStore.ProjectIdManager
import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.diagnostic.PluginException
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.platform.instanceContainer.instantiation.InstantiationException
import com.intellij.platform.instanceContainer.instantiation.instantiate
import com.intellij.platform.instanceContainer.internal.InstanceInitializer
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CancellationException

internal abstract class ServiceInstanceInitializer(
  private val componentManager: ComponentManagerImpl,
  private val pluginId: PluginId,
  private val serviceDescriptor: ServiceDescriptor,
) : InstanceInitializer {
  // In the ideal world, we should create the proxy for any "open=true" service. Unfortunately, at the moment "open" services are not
  // truly open: they are cast to "impl" classes in different places. Unchecked casts throw exceptions. Checked casts silently
  // disable some functionality. To work around the problem, we introduce the "allowlist" of services that are allowed to be proxied.
  private val proxiedServicesList: Set<String> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
    javaClass.classLoader.getResourceAsStream("proxied-services.list")?.bufferedReader()?.useLines { it.toSet() } ?: emptySet()
  }

  override val overridable: Boolean
    get() = serviceDescriptor.open

  override suspend fun createInstance(parentScope: CoroutineScope, instanceClass: Class<*>): Any {
    checkWriteAction(instanceClass)
    val instance = try {
      val newInstance = instantiate(
        resolver = componentManager.dependencyResolver,
        parentScope = parentScope,
        instanceClass = instanceClass,
        supportedSignatures = componentManager.supportedSignaturesOfLightServiceConstructors,
      )

      wrapIfDynamicOverrideSupported(newInstance)
    }
    catch (e: InstantiationException) {
      LOG.error(e)
      instantiateWithContainer(
        resolver = componentManager.dependencyResolver,
        parentScope = parentScope,
        instanceClass = instanceClass,
        pluginId = pluginId,
      )
    }
    catch (e: PluginException) {
      throw e
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      throw PluginException(e, pluginId)
    }

    if (instance is Disposable) {
      Disposer.register(componentManager.serviceParentDisposable, instance)
    }

    // do not call Cancellation.withNonCancelableSection or perform any other setup if the service doesn't need to be initialized
    @Suppress("DEPRECATION")
    if ((!componentManager.isPreInitialized(instance)) &&
        (instance is PersistentStateComponent<*> || instance is SettingsSavingComponent || instance is com.intellij.openapi.util.JDOMExternalizable)) {
      initializeService(instance, serviceDescriptor, pluginId, parentScope, componentManager)
    }
    return instance
  }

  private fun wrapIfDynamicOverrideSupported(instance: Any): Any {
    val keyClassName = serviceDescriptor.serviceInterface ?: serviceDescriptor.implementation!!
    return if (canBeDynamicallyOverridden(keyClassName)) {
      // TODO: is there a better way to get the service interface type here?
      val keyClass = instance.javaClass.classLoader.loadClass(keyClassName)
      ServiceProxyGenerator.createInstance(keyClass, instance)
    }
    else {
      instance
    }
  }

  private fun canBeDynamicallyOverridden(serviceInterfaceName: String): Boolean {
    return componentManager.useProxiesForOpenServices && overridable &&
           proxiedServicesList.contains(serviceInterfaceName)
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
  serviceDescriptor: ServiceDescriptor,
) : ServiceInstanceInitializer(componentManager, pluginId, serviceDescriptor) {
  override val instanceClassName: String
    get() = instanceClass.name

  override fun loadInstanceClass(keyClass: Class<*>?): Class<*> = instanceClass
}

private fun checkWriteAction(instanceClass: Class<*>) {
  if (!LOG.isDebugEnabled) {
    return
  }
  if (!checkServiceFromWriteAccess) {
    return
  }
  val app = ApplicationManager.getApplication() ?: return
  if (app.isWriteAccessAllowed && !app.isUnitTestMode && PersistentStateComponent::class.java.isAssignableFrom(instanceClass)) {
    LOG.warn(Throwable("Getting service from write-action leads to possible deadlock. Service implementation ${instanceClass.name}"))
  }
}

@ApiStatus.Internal
@JvmField
var checkServiceFromWriteAccess: Boolean = true

private suspend fun initializeService(
  component: Any,
  serviceDescriptor: ServiceDescriptor,
  pluginId: PluginId,
  parentScope: CoroutineScope,
  componentManager: ComponentManagerImpl,
) {
  val componentStore = componentManager.componentStore
  check(component is ProjectIdManager || componentStore.isStoreInitialized || componentManager.getApplication()!!.isUnitTestMode) {
    "You cannot get $component before component store is initialized"
  }

  componentStore.initComponent(component, serviceDescriptor, pluginId, parentScope)
}