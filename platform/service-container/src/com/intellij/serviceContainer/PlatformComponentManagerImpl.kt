// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.ServiceDescriptor.PreloadMode
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
import com.intellij.util.SmartList
import com.intellij.util.SystemProperties
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.messages.*
import com.intellij.util.messages.impl.MessageBusImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executor

internal val LOG = logger<PlatformComponentManagerImpl>()

abstract class PlatformComponentManagerImpl @JvmOverloads constructor(internal val parent: PlatformComponentManagerImpl?, setExtensionsRootArea: Boolean = parent == null) : ComponentManagerImpl(parent), LazyListenerCreator {
  companion object {
    private val constructorParameterResolver = ConstructorParameterResolver()

    @JvmStatic
    protected val fakeCorePluginDescriptor = DefaultPluginDescriptor(PluginManagerCore.CORE_ID, null)
  }

  @Suppress("LeakingThis")
  private val extensionArea = ExtensionsAreaImpl(this)

  private var handlingInitComponentError = false

  @Volatile
  private var isServicePreloadingCancelled = false

  var componentCreated = false
    private set

  private val lightServices: ConcurrentMap<Class<*>, Any>? = when {
    parent == null || parent.picoContainer.parent == null -> ContainerUtil.newConcurrentMap()
    else -> null
  }

  protected open val componentStore: IComponentStore
    get() = getService(IComponentStore::class.java)!!

  init {
    if (setExtensionsRootArea) {
      Extensions.setRootArea(extensionArea)
    }
  }

  final override fun getExtensionArea(): ExtensionsAreaImpl = extensionArea

  @Internal
  open fun registerComponents(plugins: List<IdeaPluginDescriptorImpl>, notifyListeners: Boolean) {
    val activityNamePrefix = activityNamePrefix()

    val app = getApplication()
    val headless = app == null || app.isHeadlessEnvironment
    var componentConfigCount = 0
    var map: ConcurrentMap<String, MutableList<ListenerDescriptor>>? = null
    val isHeadlessMode = app?.isHeadlessEnvironment == true
    val isUnitTestMode = app?.isUnitTestMode == true

    var activity = if (activityNamePrefix == null) null else StartUpMeasurer.startMainActivity("${activityNamePrefix}service and ep registration")
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

          map.getOrPut(listener.topicClassName) { SmartList() }.add(listener)
        }
      }

      containerDescriptor.extensionPoints?.let {
        extensionArea.registerExtensionPoints(plugin, it, this)
      }
    }

    if (activity != null) {
      activity = activity.endAndStart("${activityNamePrefix}extension registration")
    }

    for (descriptor in plugins) {
      descriptor.registerExtensions(extensionArea, this, notifyListeners)
    }
    activity?.end()

    if (myComponentConfigCount <= 0) {
      myComponentConfigCount = componentConfigCount
    }

    // app - phase must be set before getMessageBus()
    if (picoContainer.parent == null && !LoadingState.COMPONENTS_REGISTERED.isOccurred /* loading plugin on the fly */) {
      StartUpMeasurer.setCurrentState(LoadingState.COMPONENTS_REGISTERED)
    }

    // todo support lazy listeners for dynamically loaded plugins
    // ensure that messageBus is created, regardless of lazy listeners map state
    val messageBus = messageBus as MessageBusImpl
    if (map != null) {
      messageBus.setLazyListeners(map)
    }
  }

  protected fun createComponents(indicator: ProgressIndicator?) {
    LOG.assertTrue(!componentCreated)

    if (indicator != null) {
      indicator.isIndeterminate = false
    }

    val activity = when (val activityNamePrefix = activityNamePrefix()) {
      null -> null
      else -> StartUpMeasurer.startMainActivity("$activityNamePrefix${StartUpMeasurer.Phases.CREATE_COMPONENTS_SUFFIX}")
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

    val implementationClass = when (config.interfaceClass) {
      config.implementationClass -> interfaceClass.name
      else -> config.implementationClass
    }

    // implementationClass == null means we want to unregister this component
    if (!implementationClass.isNullOrEmpty()) {
      val ws = config.options != null && java.lang.Boolean.parseBoolean(config.options!!.get("workspace"))
      myPicoContainer.registerComponent(MyComponentAdapter(interfaceClass, implementationClass, pluginDescriptor, this, null, ws))
    }
  }

  internal open fun getApplication(): Application? = if (this is Application) this else ApplicationManager.getApplication()

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
      handleComponentError(t, componentClassName, pluginId)
    }
    finally {
      handlingInitComponentError = false
    }
  }

  @Internal
  fun initializeComponent(component: Any, serviceDescriptor: ServiceDescriptor?) {
    if (serviceDescriptor == null || !(component is PathMacroManager || component is IComponentStore || component is MessageBusFactory)) {
      LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred()
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

  final override fun <T : Any> getService(serviceClass: Class<T>) = doGetService(serviceClass, true)

  final override fun <T : Any> getServiceIfCreated(serviceClass: Class<T>) = doGetService(serviceClass, false)

  private fun <T : Any> doGetService(serviceClass: Class<T>, createIfNeeded: Boolean): T? {
    val lightServices = lightServices
    if (lightServices != null && isLightService(serviceClass)) {
      return getLightService(serviceClass, createIfNeeded)
    }

    val key = serviceClass.name
    val adapter = picoContainer.getServiceAdapter(key) as? ServiceComponentAdapter
    if (adapter != null) {
      return adapter.getInstance(this, createIfNeeded)
    }

    checkCanceledIfNotInClassInit()

    if (parent != null) {
      val result = parent.doGetService(serviceClass, createIfNeeded)
      if (result != null) {
        LOG.error("$key is registered as application service, but requested as project one")
        return result
      }
    }

    val result = getComponent(serviceClass) ?: return null
    val message = "$key requested as a service, but it is a component - convert it to a service or " +
                  "change call to ${if (parent == null) "ApplicationManager.getApplication().getComponent()" else "project.getComponent()"}"
    if (SystemProperties.`is`("idea.test.getService.assert.as.warn")) {
      LOG.warn(message)
    }
    else {
      PluginException.logPluginError(LOG, message, null, serviceClass)
    }
    return result
  }

  internal fun <T : Any> getLightService(serviceClass: Class<T>, createIfNeeded: Boolean): T? {
    val lightServices = lightServices!!
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
    LoadingState.COMPONENTS_REGISTERED.checkOccurred()

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

  protected open fun logMessageBusDelivery(topic: Topic<*>, messageName: String?, handler: Any, duration: Long) {
    val loader = handler.javaClass.classLoader
    val pluginId = if (loader is PluginClassLoader) loader.pluginIdString else PluginManagerCore.CORE_ID.idString
    StartUpMeasurer.addPluginCost(pluginId, "MessageBus", duration)
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
    val pluginClassLoader = serviceClass.classLoader as? PluginClassLoader
    StartUpMeasurer.addCompletedActivity(startTime, serviceClass, getServiceActivityCategory(this), pluginClassLoader?.pluginIdString)
    return result
  }

  final override fun <T : Any> instantiateClass(aClass: Class<T>, pluginId: PluginId?): T {
    checkCanceledIfNotInClassInit()

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

  final override fun <T : Any> instantiateClassWithConstructorInjection(aClass: Class<T>, key: Any, pluginId: PluginId): T {
    return instantiateUsingPicoContainer(aClass, key, pluginId, this, constructorParameterResolver)
  }

  internal open val isGetComponentAdapterOfTypeCheckEnabled: Boolean
    get() = true

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

  data class ServicePreloadingResult(val asyncPreloadedServices: CompletableFuture<Void?>, val syncPreloadedServices: CompletableFuture<Void?>)

  @ApiStatus.Internal
  fun preloadServices(plugins: List<IdeaPluginDescriptor>, executor: Executor): ServicePreloadingResult {
    @Suppress("UNCHECKED_CAST")
    plugins as List<IdeaPluginDescriptorImpl>

    val asyncPreloadedServices = mutableListOf<CompletableFuture<Void>>()
    val syncPreloadedServices = mutableListOf<CompletableFuture<Void>>()
    for (plugin in plugins) {
      for (service in getContainerDescriptor(plugin).services) {
        val preloadPolicy = service.preload
        if (preloadPolicy == PreloadMode.FALSE) {
          continue
        }

        if (preloadPolicy == PreloadMode.NOT_HEADLESS && getApplication()!!.isHeadlessEnvironment) {
          continue
        }

        val future = CompletableFuture.runAsync(Runnable {
          if (!isServicePreloadingCancelled && !isContainerDisposedOrDisposeInProgress()) {
            val adapter = myPicoContainer.getServiceAdapter(service.getInterface()) as ServiceComponentAdapter? ?: return@Runnable
            try {
              adapter.getInstance<Any>(this)
            }
            catch (e: StartupAbortedException) {
              isServicePreloadingCancelled = true
              throw e
            }
          }
        }, executor)

        when (preloadPolicy) {
          PreloadMode.TRUE, PreloadMode.NOT_HEADLESS -> asyncPreloadedServices.add(future)
          PreloadMode.AWAIT -> syncPreloadedServices.add(future)
          else -> throw IllegalStateException("Unknown preload mode $preloadPolicy")
        }
      }
    }
    return ServicePreloadingResult(asyncPreloadedServices = CompletableFuture.allOf(*asyncPreloadedServices.toTypedArray()),
                                   syncPreloadedServices = CompletableFuture.allOf(*syncPreloadedServices.toTypedArray()))
  }

  // this method is required because of ProjectImpl.temporarilyDisposed (a lot of failed tests if check temporarilyDisposed)
  internal fun isContainerDisposedOrDisposeInProgress(): Boolean {
    return myContainerState.ordinal >= ContainerState.DISPOSE_IN_PROGRESS.ordinal
  }

  // todo fix tests to use this implementation in `isContainerDisposed`
  override fun isDisposedOrDisposeInProgress(): Boolean {
    return isContainerDisposedOrDisposeInProgress()
  }

  @Internal
  fun stopServicePreloading() {
    isServicePreloadingCancelled = true
  }
}

private fun createPluginExceptionIfNeeded(error: Throwable, pluginId: PluginId): RuntimeException {
  return when (error) {
    is PluginException, is ExtensionInstantiationException -> error as RuntimeException
    else -> PluginException(error, pluginId)
  }
}

internal fun <T> isLightService(serviceClass: Class<T>): Boolean {
  return Modifier.isFinal(serviceClass.modifiers) && serviceClass.isAnnotationPresent(Service::class.java)
}

internal fun checkCanceledIfNotInClassInit() {
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
}

fun handleComponentError(t: Throwable, componentClassName: String?, pluginId: PluginId?) {
  if (t is StartupAbortedException) {
    throw t
  }

  val app = ApplicationManager.getApplication()
  if (app != null && app.isUnitTestMode) {
    throw t
  }

  var effectivePluginId = pluginId
  if (effectivePluginId == null || PluginManagerCore.CORE_ID == effectivePluginId) {
    if (componentClassName != null) {
      effectivePluginId = PluginManagerCore.getPluginByClassName(componentClassName)
    }
  }

  if (effectivePluginId == null || PluginManagerCore.CORE_ID == effectivePluginId) {
    if (t is ExtensionInstantiationException) {
      effectivePluginId = t.extensionOwnerId
    }
  }

  if (effectivePluginId != null && PluginManagerCore.CORE_ID != effectivePluginId) {
    throw StartupAbortedException("Fatal error initializing plugin ${effectivePluginId.idString}", PluginException(t, effectivePluginId))
  }
  else {
    throw StartupAbortedException("Fatal error initializing '$componentClassName'", t)
  }
}
