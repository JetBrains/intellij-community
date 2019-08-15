// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.diagnostic.*
import com.intellij.ide.plugins.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.messages.LazyListenerCreator
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.messages.MessageBusFactory
import com.intellij.util.messages.impl.MessageBusImpl
import com.intellij.util.pico.DefaultPicoContainer
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.ConcurrentMap

private val LOG = logger<PlatformComponentManagerImpl>()

abstract class PlatformComponentManagerImpl(parent: ComponentManager?) : ComponentManagerImpl(parent), LazyListenerCreator {
  private var handlingInitComponentError = false

  private val lightServices: ConcurrentMap<Class<*>, Any>? = if (parent == null || parent.picoContainer.parent == null) ContainerUtil.newConcurrentMap() else null

  private val componentStore: IComponentStore
    get() = this.stateStore

  @Internal
  open fun registerComponents(plugins: List<IdeaPluginDescriptor>) {
    // Register services before registering extensions because plugins can access services in their
    // extensions which can be invoked right away if the plugin is loaded dynamically
    for (plugin in plugins) {
      val containerDescriptor = getContainerDescriptor(plugin as IdeaPluginDescriptorImpl)
      registerServices(containerDescriptor.services, plugin)
    }

    ParallelActivity.PREPARE_APP_INIT.run(ActivitySubNames.REGISTER_EXTENSIONS) {
      @Suppress("UNCHECKED_CAST")
      PluginManagerCore.registerExtensionPointsAndExtensions(extensionArea, picoContainer,
                                                             plugins as MutableList<IdeaPluginDescriptorImpl>,
                                                             LoadingPhase.isStartupComplete())
    }


    val app = ApplicationManager.getApplication()
    val headless = app == null || app.isHeadlessEnvironment

    var map: ConcurrentMap<String, MutableList<ListenerDescriptor>>? = null
    val isHeadlessMode = app.isHeadlessEnvironment
    val isUnitTestMode = app.isUnitTestMode

    var componentConfigCount = 0
    for (plugin in plugins) {
      val containerDescriptor = getContainerDescriptor(plugin as IdeaPluginDescriptorImpl)

      for (config in containerDescriptor.components) {
        if (!config.prepareClasses(headless)) {
          continue
        }

        if (isComponentSuitable(config)) {
          registerComponents(config, plugin)
          componentConfigCount++
        }
      }

      val listeners = containerDescriptor.listeners
      if (listeners.isNotEmpty()) {
        if (map == null) {
          map = ContainerUtil.newConcurrentMap()
        }

        for (listener in listeners) {
          if ((isUnitTestMode && !listener.activeInTestMode) || (isHeadlessMode && !listener.activeInHeadlessMode)) {
            continue
          }

          map.getOrPut(listener.topicClassName) { SmartList() }.add(listener)
        }
      }
    }

    if (myComponentConfigCount <= 0) {
      myComponentConfigCount = componentConfigCount
    }

    // app - phase must be set before getMessageBus()
    if (picoContainer.parent == null && !LoadingPhase.isStartupComplete() /* loading plugin on the fly */) {
      LoadingPhase.setCurrentPhase(LoadingPhase.COMPONENT_REGISTERED)
    }

    // ensure that messageBus is created, regardless of lazy listeners map state
    val messageBus = messageBus as MessageBusImpl
    if (map != null) {
      messageBus.setLazyListeners(map)
    }
  }

  private fun registerServices(services: List<ServiceDescriptor>, pluginDescriptor: IdeaPluginDescriptor) {
    val picoContainer = myPicoContainer
    for (descriptor in services) {
      // Allow to re-define service implementations in plugins.
      // Empty serviceImplementation means we want to unregister service.
      if (descriptor.overrides && picoContainer.unregisterComponent(descriptor.getInterface()) == null) {
        throw PluginException("Service: ${descriptor.getInterface()} doesn't override anything", pluginDescriptor.pluginId)
      }

      // empty serviceImplementation means we want to unregister service
      if (descriptor.implementation != null) {
        picoContainer.registerComponent(ServiceManagerImpl.createServiceAdapter(descriptor, pluginDescriptor, this))
      }
    }
  }

  final override fun handleInitComponentError(t: Throwable, componentClassName: String?, pluginId: PluginId) {
    if (handlingInitComponentError) {
      return
    }

    handlingInitComponentError = true
    try {
      PluginManager.handleComponentError(t, componentClassName, pluginId)
    }
    finally {
      handlingInitComponentError = false
    }
  }

  final override fun initializeComponent(component: Any, serviceDescriptor: ServiceDescriptor?) {
    if (serviceDescriptor == null || !(component is PathMacroManager || component is IComponentStore || component is MessageBusFactory)) {
      LoadingPhase.CONFIGURATION_STORE_INITIALIZED.assertAtLeast()
      componentStore.initComponent(component, serviceDescriptor)
    }
  }

  protected abstract fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor

  final override fun <T : Any> getService(serviceClass: Class<T>, createIfNeeded: Boolean): T? {
    val lightServices = lightServices
    if (lightServices != null && isLightService(serviceClass)) {
      @Suppress("UNCHECKED_CAST")
      val result = lightServices.get(serviceClass) as T?
      if (result != null || !createIfNeeded) {
        return result
      }
      else {
        synchronized(serviceClass) {
          return getOrCreateLightService(serviceClass, lightServices)
        }
      }
    }

    val componentKey = serviceClass.name
    var result = picoContainer.getService(serviceClass, createIfNeeded)
    if (result != null || !createIfNeeded) {
      return result
    }

    ProgressManager.checkCanceled()

    if (myParent != null) {
      result = myParent.getService(serviceClass, createIfNeeded)
      if (result != null) {
        LOG.error("$componentKey is registered as application service, but requested as project one")
        return result
      }
    }

    result = getComponent(serviceClass) ?: return null
    PluginException.logPluginError(LOG, "$componentKey requested as a service, but it is a component - convert it to a service or " +
                                        "change call to ${if (myParent == null) "ApplicationManager.getApplication().getComponent()" else "project.getComponent()"}",
                                   null, serviceClass)
    return result
  }

  final override fun getServiceImplementationClassNames(prefix: String): MutableList<String> {
    val result = ArrayList<String>()
    ServiceManagerImpl.processAllDescriptors(this) { serviceDescriptor ->
      val implementation = serviceDescriptor.implementation ?: return@processAllDescriptors
      if (implementation.startsWith(prefix)) {
        result.add(implementation)
      }
    }
    return result
  }

  private fun <T : Any> getOrCreateLightService(serviceClass: Class<T>, cache: ConcurrentMap<Class<*>, Any>): T {
    LoadingPhase.COMPONENT_REGISTERED.assertAtLeast()

    @Suppress("UNCHECKED_CAST")
    var result = cache.get(serviceClass) as T?
    if (result != null) {
      return result
    }

    HeavyProcessLatch.INSTANCE.processStarted("Creating service '${serviceClass.name}'").use {
      if (ProgressIndicatorProvider.getGlobalProgressIndicator() == null) {
        result = createLightService(serviceClass)
      }
      else {
        ProgressManager.getInstance().executeNonCancelableSection {
          result = createLightService(serviceClass)
        }
      }
    }

    val prevValue = cache.put(serviceClass, result)
    LOG.assertTrue(prevValue == null)
    return result!!
  }

  /**
   * Use only if approved by core team.
   */
  @Internal
  fun registerComponent(key: Class<*>, implementation: Class<*>, pluginId: PluginId, override: Boolean) {
    val picoContainer = picoContainer
    if (override && picoContainer.unregisterComponent(key) == null) {
      throw PluginException("Component $key doesn't override anything", pluginId)
    }
    picoContainer.registerComponent(ComponentConfigComponentAdapter(key, implementation, pluginId, false))
  }

  /**
   * Use only if approved by core team.
   */
  @Internal
  fun registerService(serviceClass: Class<*>, implementation: Class<*>, pluginDescriptor: IdeaPluginDescriptor, override: Boolean) {
    val picoContainer = picoContainer
    val serviceKey = serviceClass.name
    if (override && picoContainer.unregisterComponent(serviceKey) == null) {
      throw PluginException("Service $serviceKey doesn't override anything", pluginDescriptor.pluginId)
    }

    val descriptor = ServiceDescriptor()
    descriptor.serviceInterface = serviceClass.name
    descriptor.serviceImplementation = implementation.name
    picoContainer.registerComponent(ServiceManagerImpl.createServiceAdapter(descriptor, pluginDescriptor, this))
  }

  private fun <T : Any> createLightService(serviceClass: Class<T>): T {
    val startTime = StartUpMeasurer.getCurrentTime()

    val result = instantiate(serviceClass, null)
    if (result is Disposable) {
      Disposer.register(this, result)
    }

    initializeComponent(result, null)
    ParallelActivity.SERVICE.record(startTime, result.javaClass, DefaultPicoContainer.getActivityLevel(myPicoContainer))
    return result
  }

  private fun <T : Any> instantiate(aClass: Class<T>, pluginId: PluginId?): T {
    try {
      if (myParent == null) {
        val constructor = aClass.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
      }
      else {
        val constructors = aClass.declaredConstructors
        constructors.sortBy { it.parameterCount }
        val constructor = constructors.first()
        constructor.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return when (constructor.parameterCount) {
          1 -> constructor.newInstance(this)
          else -> constructor.newInstance()
        } as T
      }
    }
    catch (e: Throwable) {
      if (e is InvocationTargetException) {
        val targetException = e.targetException
        if (targetException is ControlFlowException) {
          throw targetException
        }
      }
      else if (e is ControlFlowException) {
        throw e
      }

      val message = "Cannot create class ${aClass.name}"
      if (pluginId == null) {
        throw PluginException.createByClass(message, e, aClass)
      }
      else {
        throw PluginException(message, e, pluginId)
      }
    }
  }

  override fun createListener(descriptor: ListenerDescriptor): Any {
    val classLoader = descriptor.pluginDescriptor.pluginClassLoader
    val aClass = try {
      Class.forName(descriptor.listenerClassName, true, classLoader)
    }
    catch (e: Throwable) {
      throw PluginException("Cannot create listener " + descriptor.listenerClassName, e, descriptor.pluginDescriptor.pluginId)
    }
    return instantiate(aClass, descriptor.pluginDescriptor.pluginId)
  }
}

private fun <T> isLightService(serviceClass: Class<T>): Boolean {
  return Modifier.isFinal(serviceClass.modifiers) && serviceClass.isAnnotationPresent(Service::class.java)
}