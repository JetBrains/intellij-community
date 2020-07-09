// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.idea.Main
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.components.ServiceDescriptor.PreloadMode
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.*
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.ReflectionUtil
import com.intellij.util.SmartList
import com.intellij.util.SystemProperties
import com.intellij.util.messages.*
import com.intellij.util.messages.impl.MessageBusEx
import com.intellij.util.messages.impl.MessageBusImpl
import com.intellij.util.pico.DefaultPicoContainer
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nullable
import org.jetbrains.annotations.TestOnly
import org.picocontainer.PicoContainer
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicReference

internal val LOG = logger<ComponentManagerImpl>()

abstract class ComponentManagerImpl @JvmOverloads constructor(internal val parent: ComponentManagerImpl?,
                                                              setExtensionsRootArea: Boolean = parent == null) : ComponentManager, Disposable.Parent, MessageBusOwner, UserDataHolderBase() {
  protected enum class ContainerState {
    PRE_INIT, COMPONENT_CREATED, DISPOSE_IN_PROGRESS, DISPOSED, DISPOSE_COMPLETED
  }

  companion object {
    private val constructorParameterResolver = ConstructorParameterResolver()

    @JvmStatic
    @Internal
    val fakeCorePluginDescriptor = DefaultPluginDescriptor(PluginManagerCore.CORE_ID, null)

    // not as file level function to avoid scope cluttering
    @Internal
    fun <T> isLightService(serviceClass: Class<T>): Boolean {
      return Modifier.isFinal(serviceClass.modifiers) && serviceClass.isAnnotationPresent(Service::class.java)
    }

    @ApiStatus.Internal
    fun processAllDescriptors(componentManager: ComponentManager, consumer: (ServiceDescriptor) -> Unit) {
      for (plugin in PluginManagerCore.getLoadedPlugins()) {
        val pluginDescriptor = plugin as IdeaPluginDescriptorImpl
        val containerDescriptor = when (componentManager) {
          is Application -> pluginDescriptor.app
          is Project -> pluginDescriptor.project
          else -> pluginDescriptor.module
        }
        containerDescriptor.services.forEach(consumer)
      }
    }
  }

  internal val picoContainer: DefaultPicoContainer = DefaultPicoContainer(parent?.picoContainer)
  protected val containerState = AtomicReference(ContainerState.PRE_INIT)

  protected val containerStateName: String
    get() = containerState.get().name

  @Suppress("LeakingThis")
  private val extensionArea = ExtensionsAreaImpl(this)

  private var messageBus: MessageBusImpl? = null

  private var handlingInitComponentError = false

  @Volatile
  private var isServicePreloadingCancelled = false

  private var instantiatedComponentCount = 0
  private var componentConfigCount = -1

  @Suppress("LeakingThis")
  internal val serviceParentDisposable = Disposer.newDisposable("services of ${javaClass.name}@${System.identityHashCode(this)}")

  private val lightServices: ConcurrentMap<Class<*>, Any>? = when {
    parent == null || parent.picoContainer.parent == null -> ConcurrentHashMap()
    else -> null
  }

  @Suppress("DEPRECATION")
  private val baseComponents: MutableList<BaseComponent> = SmartList()

  protected open val componentStore: IComponentStore
    get() = getService(IComponentStore::class.java)!!

  init {
    if (setExtensionsRootArea) {
      Extensions.setRootArea(extensionArea)
    }
  }

  override fun getPicoContainer(): PicoContainer = checkStateAndGetPicoContainer()

  private fun checkStateAndGetPicoContainer(): DefaultPicoContainer {
    if (containerState.get() == ContainerState.DISPOSE_COMPLETED) {
      ProgressManager.checkCanceled()
      throw AlreadyDisposedException("Already disposed: $this")
    }
    return picoContainer
  }

  final override fun getMessageBus(): MessageBus {
    if (containerState.get() >= ContainerState.DISPOSE_IN_PROGRESS) {
      throw AlreadyDisposedException("Already disposed: $this")
    }
    return messageBus!!
  }

  protected open fun setProgressDuringInit(indicator: ProgressIndicator) {
    indicator.fraction = getPercentageOfComponentsLoaded()
  }

  protected fun getPercentageOfComponentsLoaded(): Double {
    return instantiatedComponentCount.toDouble() / componentConfigCount
  }

  final override fun getExtensionArea() = extensionArea

  @Internal
  fun registerComponents(plugins: List<IdeaPluginDescriptorImpl>) {
    registerComponents(plugins.map { DescriptorToLoad(it) }, null)
  }

  data class DescriptorToLoad(
    // plugin descriptor can have some definitions that are not applied until some specified plugin is not enabled,
    // if both descriptor and rootDescriptor are specified, descriptor it is such partial part
    val descriptor: IdeaPluginDescriptorImpl,
    val baseDescriptor: IdeaPluginDescriptorImpl = descriptor
  )

  @Internal
  open fun registerComponents(plugins: List<DescriptorToLoad>, listenerCallbacks: List<Runnable>?) {
    val activityNamePrefix = activityNamePrefix()

    val app = getApplication()
    val headless = app == null || app.isHeadlessEnvironment
    var newComponentConfigCount = 0
    var map: ConcurrentMap<String, MutableList<ListenerDescriptor>>? = null
    val isHeadlessMode = app?.isHeadlessEnvironment == true
    val isUnitTestMode = app?.isUnitTestMode == true

    val clonePoint = parent != null

    var activity = activityNamePrefix?.let { StartUpMeasurer.startMainActivity("${it}service and ep registration") }
    // register services before registering extensions because plugins can access services in their
    // extensions which can be invoked right away if the plugin is loaded dynamically
    for (plugin in plugins) {
      val containerDescriptor = getContainerDescriptor(plugin.descriptor)
      registerServices(containerDescriptor.services, plugin.baseDescriptor)

      for (descriptor in containerDescriptor.components) {
        if (!descriptor.prepareClasses(headless) || !isComponentSuitable(descriptor)) {
          continue
        }

        try {
          registerComponent(descriptor, plugin.descriptor)
          newComponentConfigCount++
        }
        catch (e: Throwable) {
          handleInitComponentError(e, descriptor.implementationClass ?: descriptor.interfaceClass, plugin.descriptor.pluginId)
        }
      }

      val listeners = containerDescriptor.listeners
      if (listeners.isNotEmpty()) {
        if (map == null) {
          map = ConcurrentHashMap()
        }

        for (listener in listeners) {
          if ((isUnitTestMode && !listener.activeInTestMode) || (isHeadlessMode && !listener.activeInHeadlessMode)) {
            continue
          }

          map.computeIfAbsent(listener.topicClassName) { ArrayList() }.add(listener)
        }
      }

      containerDescriptor.extensionPoints?.let {
        extensionArea.registerExtensionPoints(it, clonePoint)
      }
    }

    if (activity != null) {
      activity = activity.endAndStart("${activityNamePrefix}extension registration")
    }

    for ((subDescriptor, mainDescriptor) in plugins) {
      subDescriptor.registerExtensions(extensionArea, mainDescriptor, getContainerDescriptor(subDescriptor), listenerCallbacks)
    }
    activity?.end()

    if (componentConfigCount == -1) {
      componentConfigCount = newComponentConfigCount
    }

    // app - phase must be set before getMessageBus()
    if (picoContainer.parent == null && !LoadingState.COMPONENTS_REGISTERED.isOccurred /* loading plugin on the fly */) {
      StartUpMeasurer.setCurrentState(LoadingState.COMPONENTS_REGISTERED)
    }

    // ensure that messageBus is created, regardless of lazy listeners map state
    val messageBus = getOrCreateMessageBusUnderLock()
    if (map != null) {
      (messageBus as MessageBusEx).setLazyListeners(map)
    }
  }

  protected fun createComponents(indicator: ProgressIndicator?) {
    LOG.assertTrue(containerState.get() == ContainerState.PRE_INIT)

    if (indicator != null) {
      indicator.isIndeterminate = false
    }

    val activity = when (val activityNamePrefix = activityNamePrefix()) {
      null -> null
      else -> StartUpMeasurer.startMainActivity("$activityNamePrefix${StartUpMeasurer.Activities.CREATE_COMPONENTS_SUFFIX}")
    }

    for (componentAdapter in picoContainer.componentAdapters) {
      if (componentAdapter is MyComponentAdapter) {
        componentAdapter.getInstance<Any>(this, keyClass = null, indicator = indicator)
      }
    }

    activity?.end()

    LOG.assertTrue(containerState.compareAndSet(ContainerState.PRE_INIT, ContainerState.COMPONENT_CREATED))
  }

  @TestOnly
  fun registerComponentImplementation(componentKey: Class<*>, componentImplementation: Class<*>, shouldBeRegistered: Boolean) {
    val picoContainer = checkStateAndGetPicoContainer()
    val adapter = picoContainer.unregisterComponent(componentKey) as MyComponentAdapter?
    if (shouldBeRegistered) {
      LOG.assertTrue(adapter != null)
    }
    val pluginDescriptor = DefaultPluginDescriptor("test registerComponentImplementation")
    picoContainer.registerComponent(MyComponentAdapter(componentKey, componentImplementation.name, pluginDescriptor, this, componentImplementation))
  }

  @TestOnly
  fun <T : Any> replaceComponentInstance(componentKey: Class<T>, componentImplementation: T, parentDisposable: Disposable?): T? {
    val adapter = checkStateAndGetPicoContainer().getComponentAdapter(componentKey) as MyComponentAdapter
    return adapter.replaceInstance(componentImplementation, parentDisposable)
  }

  private fun registerComponent(config: ComponentConfig, pluginDescriptor: PluginDescriptor) {
    val interfaceClass = Class.forName(config.interfaceClass, true, pluginDescriptor.pluginClassLoader)

    if (config.options != null && java.lang.Boolean.parseBoolean(config.options!!.get("overrides"))) {
      picoContainer.unregisterComponent(interfaceClass) ?: throw PluginException("$config does not override anything",
                                                                                   pluginDescriptor.pluginId)
    }

    val implementationClass = when (config.interfaceClass) {
      config.implementationClass -> interfaceClass.name
      else -> config.implementationClass
    }

    // implementationClass == null means we want to unregister this component
    if (!implementationClass.isNullOrEmpty()) {
      val ws = config.options != null && java.lang.Boolean.parseBoolean(config.options!!.get("workspace"))
      picoContainer.registerComponent(MyComponentAdapter(interfaceClass, implementationClass, pluginDescriptor, this, null, ws))
    }
  }

  internal open fun getApplication(): Application? = if (this is Application) this else ApplicationManager.getApplication()

  private fun registerServices(services: List<ServiceDescriptor>, pluginDescriptor: IdeaPluginDescriptor) {
    val picoContainer = checkStateAndGetPicoContainer()
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
  internal fun handleInitComponentError(error: Throwable, componentClassName: String, pluginId: PluginId) {
    if (handlingInitComponentError) {
      return
    }

    handlingInitComponentError = true
    try {
      // not logged but thrown PluginException means some fatal error
      if (error is StartupAbortedException || error is ProcessCanceledException || error is PluginException) {
        throw error
      }

      var effectivePluginId = pluginId
      if (effectivePluginId == PluginManagerCore.CORE_ID) {
        if (error is ExtensionInstantiationException) {
          effectivePluginId = error.extensionOwnerId ?: PluginManagerCore.CORE_ID
        }
        if (effectivePluginId == PluginManagerCore.CORE_ID) {
          effectivePluginId = PluginManagerCore.getPluginOrPlatformByClassName(componentClassName) ?: PluginManagerCore.CORE_ID
        }
      }

      throw PluginException("Fatal error initializing '$componentClassName'", error, effectivePluginId)
    }
    finally {
      handlingInitComponentError = false
    }
  }

  internal fun initializeComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId?) {
    if (serviceDescriptor == null || !(component is PathMacroManager || component is IComponentStore || component is MessageBusFactory)) {
      LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred()
      componentStore.initComponent(component, serviceDescriptor, pluginId)
    }
  }

  protected abstract fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor

  final override fun <T : Any> getComponent(interfaceClass: Class<T>): T? {
    val picoContainer = checkStateAndGetPicoContainer()
    val adapter = picoContainer.getComponentAdapter(interfaceClass)
    if (adapter == null) {
      checkCanceledIfNotInClassInit()
      if (containerState.get() == ContainerState.DISPOSE_COMPLETED) {
        throwContainerIsAlreadyDisposed(interfaceClass, ProgressManager.getGlobalProgressIndicator())
      }
      return null
    }

    if (adapter is ServiceComponentAdapter) {
      LOG.error("$interfaceClass it is a service, use getService instead of getComponent")
    }

    @Suppress("UNCHECKED_CAST")
    return when (adapter) {
      is BaseComponentAdapter -> {
        if (parent != null && adapter.componentManager !== this) {
          LOG.error("getComponent must be called on appropriate container (current: $this, expected: ${adapter.componentManager})")
        }

        val indicator = ProgressManager.getGlobalProgressIndicator()
        if (containerState.get() == ContainerState.DISPOSE_COMPLETED) {
          adapter.throwAlreadyDisposedError(this, indicator)
        }
        adapter.getInstance(adapter.componentManager, interfaceClass, indicator = indicator)
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
    val indicator = ProgressManager.getGlobalProgressIndicator()

    if (adapter != null) {
      if (createIfNeeded && containerState.get() == ContainerState.DISPOSE_COMPLETED) {
        adapter.throwAlreadyDisposedError(this, indicator)
      }
      return adapter.getInstance(this, serviceClass, createIfNeeded, indicator)
    }

    checkCanceledIfNotInClassInit()

    // if container is fully disposed, all adapters maybe removed
    if (containerState.get() == ContainerState.DISPOSE_COMPLETED) {
      if (!createIfNeeded) {
        return null
      }
      throwContainerIsAlreadyDisposed(serviceClass, indicator)
    }

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

  private fun throwContainerIsAlreadyDisposed(interfaceClass: Class<*>, indicator: @Nullable ProgressIndicator?) {
    val error = AlreadyDisposedException("Cannot create ${interfaceClass.name} because container is already disposed: ${toString()}")
    if (indicator == null) {
      throw error
    }
    else {
      throw ProcessCanceledException(error)
    }
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
    processAllDescriptors(this) { serviceDescriptor ->
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

    if (ProgressIndicatorProvider.getGlobalProgressIndicator() == null) {
      result = createLightService(serviceClass)
    }
    else {
      ProgressManager.getInstance().executeNonCancelableSection {
        result = createLightService(serviceClass)
      }
    }

    val prevValue = cache.put(serviceClass, result)
    LOG.assertTrue(prevValue == null)
    return result!!
  }

  @Synchronized
  protected open fun getOrCreateMessageBusUnderLock(): MessageBus {
    var messageBus = this.messageBus
    if (messageBus != null) {
      return messageBus
    }

    messageBus = MessageBusFactory.getInstance().createMessageBus(this, parent?.messageBus) as MessageBusImpl
    if (StartUpMeasurer.isMeasuringPluginStartupCosts()) {
      messageBus.setMessageDeliveryListener { topic, messageName, handler, duration ->
        if (!StartUpMeasurer.isMeasuringPluginStartupCosts()) {
          messageBus.setMessageDeliveryListener(null)
          return@setMessageDeliveryListener
        }

        logMessageBusDelivery(topic, messageName, handler, duration)
      }
    }

    registerServiceInstance(MessageBus::class.java, messageBus, fakeCorePluginDescriptor)
    this.messageBus = messageBus
    return messageBus
  }

  protected open fun logMessageBusDelivery(topic: Topic<*>, messageName: String?, handler: Any, duration: Long) {
    val loader = handler.javaClass.classLoader
    val pluginId = if (loader is PluginClassLoader) loader.pluginId.idString else PluginManagerCore.CORE_ID.idString
    StartUpMeasurer.addPluginCost(pluginId, "MessageBus", duration)
  }

  /**
   * Use only if approved by core team.
   */
  @Internal
  fun registerComponent(key: Class<*>, implementation: Class<*>, pluginDescriptor: PluginDescriptor, override: Boolean) {
    val picoContainer = checkStateAndGetPicoContainer()
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
    val picoContainer = checkStateAndGetPicoContainer()
    if (override && picoContainer.unregisterComponent(serviceKey) == null) {
      throw PluginException("Service $serviceKey doesn't override anything", pluginDescriptor.pluginId)
    }

    val descriptor = ServiceDescriptor()
    descriptor.serviceInterface = serviceInterface.name
    descriptor.serviceImplementation = implementation.name
    picoContainer.registerComponent(ServiceComponentAdapter(descriptor, pluginDescriptor, this, implementation))
  }

  /**
   * Use only if approved by core team.
   */
  @Internal
  fun <T : Any> registerServiceInstance(serviceInterface: Class<T>, instance: T, pluginDescriptor: PluginDescriptor) {
    val serviceKey = serviceInterface.name
    val picoContainer = checkStateAndGetPicoContainer()
    picoContainer.unregisterComponent(serviceKey)

    val descriptor = ServiceDescriptor()
    descriptor.serviceInterface = serviceKey
    descriptor.serviceImplementation = instance.javaClass.name
    picoContainer.registerComponent(ServiceComponentAdapter(descriptor, pluginDescriptor, this, instance.javaClass, instance))
  }

  @TestOnly
  @Internal
  fun <T : Any> replaceServiceInstance(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable) {
    if (isLightService(serviceInterface)) {
      lightServices!!.put(serviceInterface, instance)
      Disposer.register(parentDisposable, Disposable {
        lightServices.remove(serviceInterface)
      })
    }
    else {
      val adapter = checkStateAndGetPicoContainer().getServiceAdapter(serviceInterface.name) as ServiceComponentAdapter
      adapter.replaceInstance(instance, parentDisposable)
    }
  }

  private fun <T : Any> createLightService(serviceClass: Class<T>): T {
    val startTime = StartUpMeasurer.getCurrentTime()

    val result = instantiateClass(serviceClass, null)
    if (result is Disposable) {
      Disposer.register(serviceParentDisposable, result)
    }

    val pluginId = (serviceClass.classLoader as? PluginClassLoader)?.pluginId
    initializeComponent(result, null, pluginId)
    StartUpMeasurer.addCompletedActivity(startTime, serviceClass, getServiceActivityCategory(this), pluginId?.idString)
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
          1 -> constructor.newInstance(getActualContainerInstance())
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

  protected open fun getActualContainerInstance(): ComponentManager = this

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
          val exception = PluginException("Class constructor must not have parameters: $className", pluginId)
          if ((pluginDescriptor?.isBundled == true) || getApplication()?.isUnitTestMode == true) {
            LOG.error(exception)
          }
          else {
            LOG.warn(exception)
          }
        }
        e is PluginException -> throw e
        else -> throw if (pluginDescriptor == null) PluginException.createByClass(e, aClass)
        else PluginException(e, pluginDescriptor.pluginId)
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
  fun unloadServices(services: List<ServiceDescriptor>, pluginId: PluginId) {
    if (services.isNotEmpty()) {
      val container = checkStateAndGetPicoContainer()
      val stateStore = stateStore
      for (service in services) {
        val adapter = (container.unregisterComponent(service.`interface`) ?: continue) as ServiceComponentAdapter
        val instance = adapter.getInitializedInstance() ?: continue
        if (instance is Disposable) {
          Disposer.dispose(instance)
        }
        stateStore.unloadComponent(instance)
      }
    }

    if (lightServices != null) {
      val iterator = lightServices.iterator()
      while (iterator.hasNext()) {
        val entry = iterator.next()
        if ((entry.key.classLoader as? PluginClassLoader)?.pluginId == pluginId) {
          val instance = entry.value
          if (instance is Disposable) {
            Disposer.dispose(instance)
          }
          stateStore.unloadComponent(instance)
          iterator.remove()
        }
      }
    }
  }

  @Internal
  open fun activityNamePrefix(): String? = null

  data class ServicePreloadingResult(val asyncPreloadedServices: CompletableFuture<Void?>,
                                     val syncPreloadedServices: CompletableFuture<Void?>)

  @ApiStatus.Internal
  fun preloadServices(plugins: List<IdeaPluginDescriptorImpl>, executor: Executor, onlyIfAwait: Boolean = false): ServicePreloadingResult {
    val asyncPreloadedServices = mutableListOf<CompletableFuture<Void>>()
    val syncPreloadedServices = mutableListOf<CompletableFuture<Void>>()
    for (plugin in plugins) {
      serviceLoop@
      for (service in getContainerDescriptor(plugin).services) {
        val list = when (service.preload) {
          PreloadMode.TRUE -> {
            if (onlyIfAwait) {
              continue@serviceLoop
            }
            else {
              asyncPreloadedServices
            }
          }
          PreloadMode.NOT_HEADLESS -> {
            if (onlyIfAwait || getApplication()!!.isHeadlessEnvironment) {
              continue@serviceLoop
            }
            else {
              asyncPreloadedServices
            }
          }
          PreloadMode.NOT_LIGHT_EDIT -> {
            if (onlyIfAwait || Main.isLightEdit()) {
              continue@serviceLoop
            }
            else {
              asyncPreloadedServices
            }
          }
          PreloadMode.AWAIT -> {
            syncPreloadedServices
          }
          PreloadMode.FALSE -> continue@serviceLoop
          else -> throw IllegalStateException("Unknown preload mode ${service.preload}")
        }

        val future = CompletableFuture.runAsync(Runnable {
          if (!isServicePreloadingCancelled && !isDisposed) {
            val adapter = picoContainer.getServiceAdapter(service.getInterface()) as ServiceComponentAdapter? ?: return@Runnable
            try {
              adapter.getInstance<Any>(this, null)
            }
            catch (ignore: AlreadyDisposedException) {
            }
            catch (e: StartupAbortedException) {
              isServicePreloadingCancelled = true
              throw e
            }
          }
        }, executor)

        list.add(future)
      }
    }
    return ServicePreloadingResult(asyncPreloadedServices = CompletableFuture.allOf(*asyncPreloadedServices.toTypedArray()),
                                   syncPreloadedServices = CompletableFuture.allOf(*syncPreloadedServices.toTypedArray()))
  }

  override fun isDisposed(): Boolean {
    return containerState.get() >= ContainerState.DISPOSE_IN_PROGRESS
  }

  final override fun beforeTreeDispose() {
    stopServicePreloading()

    ApplicationManager.getApplication().assertIsWriteThread()

    if (!(containerState.compareAndSet(ContainerState.COMPONENT_CREATED, ContainerState.DISPOSE_IN_PROGRESS) ||
          containerState.compareAndSet(ContainerState.PRE_INIT, ContainerState.DISPOSE_IN_PROGRESS))) {
      // disposed in a recommended way using ProjectManager
      return
    }

    // disposed directly using Disposer.dispose()
    // we don't care that state DISPOSE_IN_PROGRESS is already set,
    // and exceptions because of that possible - use ProjectManager to close and dispose project.
    startDispose()
  }

  @Internal
  fun startDispose() {
    stopServicePreloading()

    Disposer.disposeChildren(this)

    val messageBus = messageBus
    // There is a chance that someone will try to connect to message bus and will get NPE because of disposed connection disposable,
    // because container state is not yet set to DISPOSE_IN_PROGRESS.
    // So, 1) dispose connection children 2) set state DISPOSE_IN_PROGRESS 3) dispose connection
    messageBus?.disposeConnectionChildren()

    containerState.set(ContainerState.DISPOSE_IN_PROGRESS)

    messageBus?.disposeConnection()
  }

  override fun dispose() {
    if (!containerState.compareAndSet(ContainerState.DISPOSE_IN_PROGRESS, ContainerState.DISPOSED)) {
      throw IllegalStateException("Expected current state is DISPOSE_IN_PROGRESS, but actual state is ${containerState.get()} ($this)")
    }

    // dispose components and services
    Disposer.dispose(serviceParentDisposable)

    // release references to services instances
    picoContainer.release()

    val messageBus = messageBus
    if (messageBus != null ) {
      // Must be after disposing of serviceParentDisposable, because message bus disposes child buses, so, we must dispose all services first.
      // For example, service ModuleManagerImpl disposes modules, each module, in turn, disposes module's message bus (child bus of application).
      Disposer.dispose(messageBus)
      this.messageBus = null
    }

    if (!containerState.compareAndSet(ContainerState.DISPOSED, ContainerState.DISPOSE_COMPLETED)) {
      throw IllegalStateException("Expected current state is DISPOSED, but actual state is ${containerState.get()} ($this)")
    }

    componentConfigCount = -1
  }

  @Internal
  fun stopServicePreloading() {
    isServicePreloadingCancelled = true
  }

  @Suppress("DEPRECATION")
  override fun getComponent(name: String): BaseComponent? {
    for (componentAdapter in checkStateAndGetPicoContainer().unsafeGetAdapters()) {
      if (componentAdapter is MyComponentAdapter) {
        val instance = componentAdapter.getInitializedInstance()
        if (instance is BaseComponent && name == instance.componentName) {
          return instance
        }
      }
    }
    return null
  }

  @Internal
  internal fun componentCreated(indicator: ProgressIndicator?) {
    instantiatedComponentCount++

    if (indicator != null) {
      indicator.checkCanceled()
      setProgressDuringInit(indicator)
    }
  }

  override fun <T : Any> getComponentInstancesOfType(baseClass: Class<T>, createIfNeeded: Boolean): List<T> {
    var result: MutableList<T>? = null
    // we must use instances only from our adapter (could be service or something else)
    for (componentAdapter in checkStateAndGetPicoContainer().componentAdapters) {
      if (componentAdapter is MyComponentAdapter && ReflectionUtil.isAssignable(baseClass, componentAdapter.componentImplementation)) {
        val instance = componentAdapter.getInstance<T>(this, null, createIfNeeded, null)
        if (instance != null) {
          if (result == null) {
            result = ArrayList()
          }
          result.add(instance)
        }
      }
    }
    return result ?: emptyList()
  }

  protected open fun isComponentSuitable(componentConfig: ComponentConfig): Boolean {
    val options = componentConfig.options ?: return true
    return !java.lang.Boolean.parseBoolean(options.get("internal")) || ApplicationManager.getApplication().isInternal
  }

  override fun getDisposed(): Condition<*> {
    return Condition<Any?> { isDisposed }
  }
}

private fun createPluginExceptionIfNeeded(error: Throwable, pluginId: PluginId): RuntimeException {
  return when (error) {
    is PluginException, is ExtensionInstantiationException -> error as RuntimeException
    else -> PluginException(error, pluginId)
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
    throw StartupAbortedException("Fatal error initializing plugin $effectivePluginId", PluginException(t, effectivePluginId))
  }
  else {
    throw StartupAbortedException("Fatal error initializing '$componentClassName'", t)
  }
}
