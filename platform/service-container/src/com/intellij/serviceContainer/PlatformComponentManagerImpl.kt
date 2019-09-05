// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.diagnostic.*
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.*
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.IncorrectOperationException
import com.intellij.util.SmartList
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.messages.*
import com.intellij.util.messages.impl.MessageBusImpl
import com.intellij.util.pico.DefaultPicoContainer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentMap

abstract class PlatformComponentManagerImpl @JvmOverloads constructor(internal val parent: ComponentManager?, setExtensionsRootArea: Boolean = parent == null) : ComponentManagerImpl(parent), LazyListenerCreator {
  companion object {
    private val LOG = logger<PlatformComponentManagerImpl>()

    private val constructorParameterResolver = PlatformConstructorParameterResolver(isExtensionSupported = false)
    private val heavyConstructorParameterResolver = PlatformConstructorParameterResolver(isExtensionSupported = true)

    private class PlatformConstructorParameterResolver(private val isExtensionSupported: Boolean) : ConstructorParameterResolver() {
      override fun isResolvable(componentManager: PlatformComponentManagerImpl, requestorKey: Any, expectedType: Class<*>): Boolean {
        if (isLightService(expectedType) || super.isResolvable(componentManager, requestorKey, expectedType)) {
          return true
        }
        return isExtensionSupported && componentManager.extensionArea.findExtensionByClass(expectedType) != null
      }

      override fun resolveInstance(componentManager: PlatformComponentManagerImpl, requestorKey: Any, expectedType: Class<*>): Any? {
        if (isLightService(expectedType)) {
          return componentManager.getLightService(componentManager.lightServices!!, expectedType, true)
        }

        val result = super.resolveInstance(componentManager, requestorKey, expectedType)
        if (result == null && isExtensionSupported) {
          val extension = componentManager.extensionArea.findExtensionByClass(expectedType)
          if (extension != null) {
            LOG.warn("Do not use constructor injection to get extension instance (requestorKey=$requestorKey, extensionClass=${expectedType.name})")
          }
          return extension
        }
        return result
      }
    }

    @JvmStatic
    protected val fakeCorePluginDescriptor = object : PluginDescriptor {
      override fun getPluginClassLoader() = null

      override fun getPluginId() = PluginId.getId(PluginManagerCore.CORE_PLUGIN_ID)
    }
  }

  @Suppress("LeakingThis")
  private val extensionArea = ExtensionsAreaImpl(this)

  private var handlingInitComponentError = false

  var componentCreated = false
    private set

  private val lightServices: ConcurrentMap<Class<*>, Any>? = when {
    parent == null || parent.picoContainer.parent == null -> ContainerUtil.newConcurrentMap()
    else -> null
  }

  protected open val componentStore: IComponentStore
    get() = getService(IComponentStore::class.java)

  init {
    if (setExtensionsRootArea) {
      Extensions.setRootArea(extensionArea)
    }
  }

  final override fun getExtensionArea(): ExtensionsAreaImpl = extensionArea

  @Internal
  open fun registerComponents(plugins: List<IdeaPluginDescriptor>) {
    @Suppress("UNCHECKED_CAST")
    plugins as List<IdeaPluginDescriptorImpl>
    val activityNamePrefix = activityNamePrefix()
    val parallelActivity = if (activityNamePrefix == null) null else ParallelActivity.PREPARE_APP_INIT

    val app = getApplication()
    val headless = app == null || app.isHeadlessEnvironment
    var componentConfigCount = 0
    var map: ConcurrentMap<String, MutableList<ListenerDescriptor>>? = null
    val isHeadlessMode = app?.isHeadlessEnvironment == true
    val isUnitTestMode = app?.isUnitTestMode == true

    parallelActivity.run("${activityNamePrefix}service and ep registration") {
      // register services before registering extensions because plugins can access services in their
      // extensions which can be invoked right away if the plugin is loaded dynamically
      for (plugin in plugins) {
        val containerDescriptor = getContainerDescriptor(plugin)
        registerServices(containerDescriptor.services, plugin)

        for (descriptor in containerDescriptor.components) {
          if (!descriptor.prepareClasses(headless) || !isComponentSuitable(descriptor)) {
            continue
          }

          try {
            registerComponent(descriptor, plugin)
            componentConfigCount++
          }
          catch (e: Throwable) {
            handleInitComponentError(e, null, plugin.pluginId)
          }
        }

        val listeners = getContainerDescriptor(plugin).listeners
        if (listeners.isNotEmpty()) {
          if (map == null) {
            map = ContainerUtil.newConcurrentMap()
          }

          for (listener in listeners) {
            if ((isUnitTestMode && !listener.activeInTestMode) || (isHeadlessMode && !listener.activeInHeadlessMode)) {
              continue
            }

            map!!.getOrPut(listener.topicClassName) { SmartList() }.add(listener)
          }
        }

        containerDescriptor.extensionPoints?.let {
          extensionArea.registerExtensionPoints(plugin, it, this)
        }
      }
    }

    parallelActivity.run("${activityNamePrefix}extension registration") {
      val notifyListeners = LoadingPhase.isStartupComplete()
      for (descriptor in plugins) {
        descriptor.registerExtensions(extensionArea, this, notifyListeners)
      }
    }

    if (myComponentConfigCount <= 0) {
      myComponentConfigCount = componentConfigCount
    }

    // app - phase must be set before getMessageBus()
    if (picoContainer.parent == null && !LoadingPhase.PROJECT_OPENED.isComplete /* loading plugin on the fly */) {
      LoadingPhase.setCurrentPhase(LoadingPhase.COMPONENT_REGISTERED)
    }

    // todo support lazy listeners for dynamically loaded plugins
    // ensure that messageBus is created, regardless of lazy listeners map state
    val messageBus = messageBus as MessageBusImpl
    if (map != null) {
      messageBus.setLazyListeners(map!!)
    }
  }

  protected fun createComponents(indicator: ProgressIndicator?) {
    LOG.assertTrue(!componentCreated)

    if (indicator != null) {
      indicator.isIndeterminate = false
    }

    val activityNamePrefix = activityNamePrefix()
    val activity = when (activityNamePrefix) {
      null -> null
      else -> StartUpMeasurer.start("$activityNamePrefix${StartUpMeasurer.Phases.CREATE_COMPONENTS_SUFFIX}")
    }

    for (componentAdapter in myPicoContainer.componentAdapters) {
      if (componentAdapter is MyComponentAdapter) {
        componentAdapter.getInstance<Any>(this, indicator = indicator)
      }
    }

    activity?.end()

    componentCreated = true
  }

  @TestOnly
  fun registerComponentImplementation(componentKey: Class<*>, componentImplementation: Class<*>, shouldBeRegistered: Boolean) {
    val picoContainer = picoContainer
    val adapter = picoContainer.unregisterComponent(componentKey) as MyComponentAdapter?
    if (shouldBeRegistered) {
      LOG.assertTrue(adapter != null)
    }
    picoContainer.registerComponent(MyComponentAdapter(componentKey, componentImplementation.name, DefaultPluginDescriptor("test registerComponentImplementation"), this, componentImplementation))
  }

  @TestOnly
  fun <T : Any> replaceComponentInstance(componentKey: Class<T>, componentImplementation: T, parentDisposable: Disposable?): T? {
    val adapter = myPicoContainer.getComponentAdapter(componentKey) as MyComponentAdapter
    return adapter.replaceInstance(componentImplementation, parentDisposable)
  }

  private fun registerComponent(config: ComponentConfig, pluginDescriptor: PluginDescriptor) {
    val interfaceClass = Class.forName(config.interfaceClass, true, pluginDescriptor.pluginClassLoader)

    if (config.options != null && java.lang.Boolean.parseBoolean(config.options!!.get("overrides"))) {
      myPicoContainer.unregisterComponent(interfaceClass) ?: throw PluginException("$config does not override anything", pluginDescriptor.pluginId)
    }

    val implementationClass = when {
      config.interfaceClass == config.implementationClass -> interfaceClass.name
      else -> config.implementationClass
    }

    // implementationClass == null means we want to unregister this component
    if (!implementationClass.isNullOrEmpty()) {
      val ws = config.options != null && java.lang.Boolean.parseBoolean(config.options!!.get("workspace"))
      myPicoContainer.registerComponent(MyComponentAdapter(interfaceClass, implementationClass, pluginDescriptor, this, null, ws))
    }
  }

  internal fun getApplication(): Application? = if (this is Application) this else ApplicationManager.getApplication()

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
        picoContainer.registerComponent(ServiceComponentAdapter(descriptor, pluginDescriptor, this))
      }
    }
  }

  @Internal
  fun handleInitComponentError(t: Throwable, componentClassName: String?, pluginId: PluginId) {
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

  @Internal
  fun initializeComponent(component: Any, serviceDescriptor: ServiceDescriptor?) {
    if (serviceDescriptor == null || !(component is PathMacroManager || component is IComponentStore || component is MessageBusFactory)) {
      LoadingPhase.CONFIGURATION_STORE_INITIALIZED.assertAtLeast()
      componentStore.initComponent(component, serviceDescriptor)
    }
  }

  protected abstract fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor

  final override fun <T : Any> getComponent(interfaceClass: Class<T>): T? {
    val picoContainer = picoContainer
    val adapter = picoContainer.getComponentAdapter(interfaceClass) ?: return null
    @Suppress("UNCHECKED_CAST")
    return when (adapter) {
      is BaseComponentAdapter -> {
        if (parent != null && adapter.componentManager !== this) {
          LOG.error("getComponent must be called on appropriate container (current: $this, expected: ${adapter.componentManager})")
        }
        adapter.getInstance(adapter.componentManager)
      }
      else -> adapter.getComponentInstance(picoContainer) as T
    }
  }

  final override fun <T : Any> getService(serviceClass: Class<T>, createIfNeeded: Boolean): T? {
    val lightServices = lightServices
    if (lightServices != null && isLightService(serviceClass)) {
      return getLightService(lightServices, serviceClass, createIfNeeded)
    }

    val key = serviceClass.name
    val adapter = picoContainer.getServiceAdapter(key) as? ServiceComponentAdapter
    if (adapter != null) {
      return adapter.getInstance(this, createIfNeeded)
    }

    ProgressManager.checkCanceled()

    if (parent != null) {
      val result = parent.getService(serviceClass, createIfNeeded)
      if (result != null) {
        LOG.error("$key is registered as application service, but requested as project one")
        return result
      }
    }

    val result = getComponent(serviceClass) ?: return null
    PluginException.logPluginError(LOG, "$key requested as a service, but it is a component - convert it to a service or " +
                                        "change call to ${if (parent == null) "ApplicationManager.getApplication().getComponent()" else "project.getComponent()"}",
                                   null, serviceClass)
    return result
  }

  private fun <T : Any> getLightService(lightServices: ConcurrentMap<Class<*>, Any>, serviceClass: Class<T>, createIfNeeded: Boolean): T? {
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

  @Internal
  fun getServiceImplementationClassNames(prefix: String): List<String> {
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

  final override fun createMessageBus(): MessageBus {
    val messageBus: MessageBus = MessageBusFactory.newMessageBus(this, parent?.messageBus)
    if (messageBus is MessageBusImpl && StartUpMeasurer.isMeasuringPluginStartupCosts()) {
      messageBus.setMessageDeliveryListener { topic, messageName, handler, duration ->
        if (!StartUpMeasurer.isMeasuringPluginStartupCosts()) {
          messageBus.setMessageDeliveryListener(null)
          return@setMessageDeliveryListener
        }

        logMessageBusDelivery(topic, messageName, handler, duration)
      }
    }

    registerServiceInstance(MessageBus::class.java, messageBus, fakeCorePluginDescriptor)
    return messageBus
  }

  protected open fun logMessageBusDelivery(topic: Topic<*>, messageName: String?, handler: Any, durationInNano: Long) {
    val loader = handler.javaClass.classLoader
    val pluginId = if (loader is PluginClassLoader) loader.pluginIdString else PluginManagerCore.CORE_PLUGIN_ID
    StartUpMeasurer.addPluginCost(pluginId, "MessageBus", durationInNano)
  }

  /**
   * Use only if approved by core team.
   */
  @Internal
  fun registerComponent(key: Class<*>, implementation: Class<*>, pluginDescriptor: PluginDescriptor, override: Boolean) {
    val picoContainer = picoContainer
    if (override && picoContainer.unregisterComponent(key) == null) {
      throw PluginException("Component $key doesn't override anything", pluginDescriptor.pluginId)
    }
    picoContainer.registerComponent(MyComponentAdapter(key, implementation.name, pluginDescriptor, this, implementation))
  }

  /**
   * Use only if approved by core team.
   */
  @Internal
  fun registerService(serviceInterface: Class<*>, implementation: Class<*>, pluginDescriptor: PluginDescriptor, override: Boolean) {
    val serviceKey = serviceInterface.name
    if (override && myPicoContainer.unregisterComponent(serviceKey) == null) {
      throw PluginException("Service $serviceKey doesn't override anything", pluginDescriptor.pluginId)
    }

    val descriptor = ServiceDescriptor()
    descriptor.serviceInterface = serviceInterface.name
    descriptor.serviceImplementation = implementation.name
    myPicoContainer.registerComponent(ServiceComponentAdapter(descriptor, pluginDescriptor, this, implementation))
  }

  /**
   * Use only if approved by core team.
   */
  @Internal
  fun <T : Any> registerServiceInstance(serviceInterface: Class<T>, instance: T, pluginDescriptor: PluginDescriptor) {
    val serviceKey = serviceInterface.name
    myPicoContainer.unregisterComponent(serviceKey)

    val descriptor = ServiceDescriptor()
    descriptor.serviceInterface = serviceKey
    descriptor.serviceImplementation = instance.javaClass.name
    picoContainer.registerComponent(ServiceComponentAdapter(descriptor, pluginDescriptor, this, instance.javaClass, instance))
  }

  @TestOnly
  @Internal
  fun <T : Any> replaceServiceInstance(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable) {
    val adapter = myPicoContainer.getServiceAdapter(serviceInterface.name) as ServiceComponentAdapter
    adapter.replaceInstance(instance, parentDisposable)
  }

  private fun <T : Any> createLightService(serviceClass: Class<T>): T {
    val startTime = StartUpMeasurer.getCurrentTime()

    val result = instantiateClass(serviceClass, null)
    if (result is Disposable) {
      Disposer.register(this, result)
    }

    initializeComponent(result, null)
    ParallelActivity.SERVICE.record(startTime, serviceClass, getActivityLevel())
    return result
  }

  final override fun <T : Any> instantiateClass(aClass: Class<T>, pluginId: PluginId?): T {
    try {
      ProgressManager.checkCanceled()
    }
    catch (e: ProcessCanceledException) {
      // otherwise ExceptionInInitializerError happens and the class is screwed forever
      @Suppress("SpellCheckingInspection")
      if (!e.stackTrace.any { it.methodName == "<clinit>" }) {
        throw e
      }
    }

    try {
      if (parent == null) {
        val constructor = aClass.getDeclaredConstructor()
        constructor.isAccessible = true
        return constructor.newInstance()
      }
      else {
        val constructors = aClass.declaredConstructors

        var constructor: Constructor<*>? = if (constructors.size > 1) {
          // see ConfigurableEP - prefer constructor that accepts our instance
          constructors.firstOrNull { it.parameterCount == 1 && it.parameterTypes[0].isAssignableFrom(javaClass) }
        }
        else {
          null
        }

        if (constructor == null) {
          constructors.sortBy { it.parameterCount }
          constructor = constructors.first()!!
        }

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

  final override fun <T : Any> instantiateClassWithConstructorInjection(aClass: Class<T>, key: Any, pluginId: PluginId?): T {
    // constructorParameterResolver is very expensive, because pico container behaviour is to find greediest satisfiable constructor,
    // so, if class has constructors (Project) and (Project, Foo, Bar), then Foo and Bar unrelated classes will be searched for.
    // To avoid this expensive nearly linear search of extension, first resolve without our logic to resolve extensions, and in case of error try expensive.
    try {
      return instantiateUsingPicoContainer(aClass, key, this, constructorParameterResolver)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: ExtensionNotApplicableException) {
      throw e
    }
    catch (e: Exception) {
      if (lightServices == null || e is IncorrectOperationException) {
        throw e
      }
      else {
        assertExtensionInjection(pluginId, e)
        return instantiateUsingPicoContainer(aClass, key, this, heavyConstructorParameterResolver)
      }
    }
  }

  protected open fun assertExtensionInjection(pluginId: PluginId?, e: Exception) {
    val app = getApplication()
    @Suppress("SpellCheckingInspection")
    if (app != null && app.isUnitTestMode && pluginId?.idString != "org.jetbrains.kotlin" && pluginId?.idString != "Lombook Plugin") {
      throw UnsupportedOperationException("In tests, extension classes are not resolved for constructor injection, to enforce removing such deprecated references.", e)
    }
  }

  final override fun <T : Any> instantiateExtensionWithPicoContainerOnlyIfNeeded(className: String?, pluginDescriptor: PluginDescriptor?): T {
    val pluginId = pluginDescriptor?.pluginId ?: PluginId.getId("unknown")
    if (className == null) {
      throw PluginException("implementation class is not specified", pluginId)
    }

    val aClass = try {
      @Suppress("UNCHECKED_CAST")
      Class.forName(className, true, pluginDescriptor?.pluginClassLoader ?: javaClass.classLoader) as Class<T>
    }
    catch (e: Throwable) {
      throw PluginException(e, pluginId)
    }

    try {
      return instantiateClass(aClass, pluginId)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: ExtensionNotApplicableException) {
      throw e
    }
    catch (e: Throwable) {
      when {
        e.cause is NoSuchMethodException || e.cause is IllegalArgumentException -> {
          val exception = PluginException("Bean extension class constructor must not have parameters: $className", pluginId)
          if ((pluginDescriptor?.isBundled == true) || getApplication()?.isUnitTestMode == true) {
            LOG.error(exception)
          }
          else {
            LOG.warn(exception)
          }
        }
        e is PluginException -> throw e
        else -> throw if (pluginDescriptor == null) PluginException.createByClass(e, aClass) else PluginException(e, pluginDescriptor.pluginId)
      }
    }

    return instantiateClassWithConstructorInjection(aClass, aClass, pluginId)
  }

  final override fun createListener(descriptor: ListenerDescriptor): Any {
    val pluginDescriptor = descriptor.pluginDescriptor
    val aClass = try {
      Class.forName(descriptor.listenerClassName, true, pluginDescriptor.pluginClassLoader)
    }
    catch (e: Throwable) {
      throw PluginException("Cannot create listener ${descriptor.listenerClassName}", e, pluginDescriptor.pluginId)
    }
    return instantiateClass(aClass, pluginDescriptor.pluginId)
  }

  final override fun logError(error: Throwable, pluginId: PluginId) {
    if (error is ProcessCanceledException || error is ExtensionNotApplicableException) {
      throw error
    }

    LOG.error(createPluginExceptionIfNeeded(error, pluginId))
  }

  final override fun createError(error: Throwable, pluginId: PluginId): RuntimeException {
    return when (error) {
      is ProcessCanceledException, is ExtensionNotApplicableException -> error as RuntimeException
      else -> createPluginExceptionIfNeeded(error, pluginId)
    }
  }

  final override fun createError(message: String, pluginId: PluginId) = PluginException(message, pluginId)

  @Internal
  fun getActivityLevel(): StartUpMeasurer.Level = DefaultPicoContainer.getActivityLevel(myPicoContainer)

  @Internal
  fun unloadServices(containerDescriptor: ContainerDescriptor): List<Any> {
    val unloadedInstances = ArrayList<Any>()
    for (service in containerDescriptor.services) {
      val adapter = myPicoContainer.unregisterComponent(service.getInterface()) as? ServiceComponentAdapter ?: continue
      val instance = adapter.getInstance<Any>(this, createIfNeeded = false) ?: continue
      if (instance is Disposable) {
        Disposer.dispose(instance)
      }
      unloadedInstances.add(instance)
    }
    return unloadedInstances
  }

  @Internal
  open fun activityNamePrefix(): String? = null

  @ApiStatus.Internal
  fun preloadServices(plugins: List<IdeaPluginDescriptor>): CompletableFuture<*> {
    @Suppress("UNCHECKED_CAST")
    plugins as List<IdeaPluginDescriptorImpl>

    val futures = mutableListOf<CompletableFuture<Void>>()
    val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("preload services", Runtime.getRuntime().availableProcessors(), false)
    for (plugin in plugins) {
      for (service in getContainerDescriptor(plugin).services) {
        if (service.preload) {
          futures.add(CompletableFuture.runAsync(Runnable {
            (myPicoContainer.getServiceAdapter(service.getInterface()) as ServiceComponentAdapter?)?.getInstance<Any>(this)
          }, executor))
        }
      }
    }
    executor.shutdown()
    return CompletableFuture.allOf(*futures.toTypedArray())
  }

  internal fun isContainerDisposed() = super.isDisposed()
}

private fun createPluginExceptionIfNeeded(error: Throwable, pluginId: PluginId): RuntimeException {
  return when (error) {
    is PluginException, is ExtensionInstantiationException -> error as RuntimeException
    else -> PluginException(error, pluginId)
  }
}

private fun <T> isLightService(serviceClass: Class<T>): Boolean {
  return Modifier.isFinal(serviceClass.modifiers) && serviceClass.isAnnotationPresent(Service::class.java)
}