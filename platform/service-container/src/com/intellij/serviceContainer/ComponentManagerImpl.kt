// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DeprecatedCallableAddReplaceWith", "ReplaceNegatedIsEmptyWithIsNotEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment")
package com.intellij.serviceContainer

import com.intellij.diagnostic.*
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.idea.Main
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.*
import com.intellij.openapi.components.ServiceDescriptor.PreloadMode
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IComponentStoreOwner
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.*
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.ArrayUtil
import com.intellij.util.messages.*
import com.intellij.util.messages.impl.MessageBusEx
import com.intellij.util.messages.impl.MessageBusImpl
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.picocontainer.ComponentAdapter
import org.picocontainer.PicoContainer
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference

internal val LOG = logger<ComponentManagerImpl>()
private val constructorParameterResolver = ConstructorParameterResolver()
private val methodLookup = MethodHandles.lookup()
private val emptyConstructorMethodType = MethodType.methodType(Void.TYPE)

@ApiStatus.Internal
abstract class ComponentManagerImpl @JvmOverloads constructor(
  internal val parent: ComponentManagerImpl?,
  setExtensionsRootArea: Boolean = parent == null
) : ComponentManager, Disposable.Parent, MessageBusOwner, UserDataHolderBase(), PicoContainer, ComponentManagerEx, IComponentStoreOwner {
  protected enum class ContainerState {
    PRE_INIT, COMPONENT_CREATED, DISPOSE_IN_PROGRESS, DISPOSED, DISPOSE_COMPLETED
  }

  companion object {
    @ApiStatus.Internal
    @JvmField val fakeCorePluginDescriptor = DefaultPluginDescriptor(PluginManagerCore.CORE_ID, null)

    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
    @ApiStatus.Internal
    @JvmField val badWorkspaceComponents: Set<String> = java.util.Set.of(
      "jetbrains.buildServer.codeInspection.InspectionPassRegistrar",
      "jetbrains.buildServer.testStatus.TestStatusPassRegistrar",
      "jetbrains.buildServer.customBuild.lang.gutterActions.CustomBuildParametersGutterActionsHighlightingPassRegistrar",
    )

    // not as file level function to avoid scope cluttering
    @ApiStatus.Internal
    fun createAllServices(componentManager: ComponentManagerImpl, exclude: Set<String>) {
      for (o in componentManager.componentKeyToAdapter.values) {
        if (o !is ServiceComponentAdapter) {
          continue
        }

        val implementation = o.descriptor.serviceImplementation
        try {
          if (implementation == "org.jetbrains.plugins.groovy.mvc.MvcConsole") {
            // NPE in RunnerContentUi.setLeftToolbar
            continue
          }
          if (implementation == "org.jetbrains.plugins.grails.lang.gsp.psi.gsp.impl.gtag.GspTagDescriptorService") {
            // requires a read action
            continue
          }

          if (exclude.contains(implementation)) {
            invokeAndWaitIfNeeded {
              o.getInstance<Any>(componentManager, null)
            }
          }
          else {
            o.getInstance<Any>(componentManager, null)
          }
        }
        catch (e: Throwable) {
          LOG.error("Cannot create $implementation", e)
        }
      }
    }
  }

  private val componentKeyToAdapter = ConcurrentHashMap<Any, ComponentAdapter>()
  private val componentAdapters = LinkedHashSetWrapper<ComponentAdapter>()
  private val serviceInstanceHotCache = ConcurrentHashMap<Class<*>, Any?>()

  protected val containerState = AtomicReference(ContainerState.PRE_INIT)

  protected val containerStateName: String
    get() = containerState.get().name

  @Suppress("LeakingThis")
  private val _extensionArea by lazy { ExtensionsAreaImpl(this) }

  private var messageBus: MessageBusImpl? = null

  private var handlingInitComponentError = false

  @Volatile
  private var isServicePreloadingCancelled = false

  private var instantiatedComponentCount = 0
  private var componentConfigCount = -1

  @Suppress("LeakingThis")
  internal val serviceParentDisposable = Disposer.newDisposable("services of ${javaClass.name}@${System.identityHashCode(this)}")

  protected open val isLightServiceSupported = parent?.parent == null
  protected open val isMessageBusSupported = parent?.parent == null
  protected open val isComponentSupported = true
  protected open val isExtensionSupported = true

  @Volatile
  internal var componentContainerIsReadonly: String? = null

  override val componentStore: IComponentStore
    get() = getService(IComponentStore::class.java)!!

  init {
    if (setExtensionsRootArea) {
      Extensions.setRootArea(_extensionArea)
    }
  }

  @Deprecated("Use ComponentManager API", level = DeprecationLevel.ERROR)
  final override fun getPicoContainer(): PicoContainer {
    checkState()
    return this
  }

  private fun registerAdapter(componentAdapter: ComponentAdapter, pluginDescriptor: PluginDescriptor?) {
    if (componentKeyToAdapter.putIfAbsent(componentAdapter.componentKey, componentAdapter) != null) {
      val error = "Key ${componentAdapter.componentKey} duplicated"
      if (pluginDescriptor == null) {
        throw PluginException.createByClass(error, null, componentAdapter.javaClass)
      }
      else {
        throw PluginException(error, null, pluginDescriptor.pluginId)
      }
    }
  }

  fun forbidGettingServices(reason: String): AccessToken {
    val token = object : AccessToken() {
      override fun finish() {
        componentContainerIsReadonly = null
      }
    }
    componentContainerIsReadonly = reason
    return token
  }

  private fun checkState() {
    if (containerState.get() == ContainerState.DISPOSE_COMPLETED) {
      ProgressManager.checkCanceled()
      throw AlreadyDisposedException("Already disposed: $this")
    }
  }

  final override fun getMessageBus(): MessageBus {
    if (containerState.get() >= ContainerState.DISPOSE_IN_PROGRESS) {
      ProgressManager.checkCanceled()
      throw AlreadyDisposedException("Already disposed: $this")
    }

    val messageBus = messageBus
    if (messageBus == null || !isMessageBusSupported) {
      LOG.error("Do not use module level message bus")
      return getOrCreateMessageBusUnderLock()
    }
    return messageBus
  }

  fun getDeprecatedModuleLevelMessageBus(): MessageBus {
    if (containerState.get() >= ContainerState.DISPOSE_IN_PROGRESS) {
      ProgressManager.checkCanceled()
      throw AlreadyDisposedException("Already disposed: $this")
    }
    return messageBus ?: getOrCreateMessageBusUnderLock()
  }

  protected open fun setProgressDuringInit(indicator: ProgressIndicator) {
    indicator.fraction = getPercentageOfComponentsLoaded()
  }

  fun getPercentageOfComponentsLoaded(): Double {
    return instantiatedComponentCount.toDouble() / componentConfigCount
  }

  final override fun getExtensionArea(): ExtensionsAreaImpl {
    if (!isExtensionSupported) {
      error("Extensions aren't supported")
    }
    return _extensionArea
  }

  // convenient method - use only in a not important code
  fun registerComponents() {
    registerComponents(modules = PluginManagerCore.getPluginSet().getEnabledModules(),
                       app = getApplication(),
                       precomputedExtensionModel = null,
                       listenerCallbacks = null)
  }

  open fun registerComponents(modules: Sequence<IdeaPluginDescriptorImpl>,
                              app: Application?,
                              precomputedExtensionModel: PrecomputedExtensionModel?,
                              listenerCallbacks: List<Runnable>?) {
    val activityNamePrefix = activityNamePrefix()

    var newComponentConfigCount = 0
    var map: ConcurrentMap<String, MutableList<ListenerDescriptor>>? = null
    val isHeadless = app == null || app.isHeadlessEnvironment
    val isUnitTestMode = app?.isUnitTestMode ?: false

    var activity = activityNamePrefix?.let { StartUpMeasurer.startActivity("${it}service and ep registration") }

    // register services before registering extensions because plugins can access services in their
    // extensions which can be invoked right away if the plugin is loaded dynamically
    val extensionPoints = if (precomputedExtensionModel == null) HashMap(extensionArea.extensionPoints) else null
    for (rootModule in modules) {
      executeRegisterTask(rootModule) { module ->
        val containerDescriptor = getContainerDescriptor(module)
        registerServices(containerDescriptor.services, module)
        newComponentConfigCount += registerComponents(module, containerDescriptor, isHeadless)

        containerDescriptor.listeners?.let { listeners ->
          var m = map
          if (m == null) {
            m = ConcurrentHashMap()
            map = m
          }
          for (listener in listeners) {
            if ((isUnitTestMode && !listener.activeInTestMode) || (isHeadless && !listener.activeInHeadlessMode)) {
              continue
            }

            if (listener.os != null && !isSuitableForOs(listener.os)) {
              continue
            }

            listener.pluginDescriptor = module
            m.computeIfAbsent(listener.topicClassName) { ArrayList() }.add(listener)
          }
        }

        if (extensionPoints != null) {
          containerDescriptor.extensionPoints?.let {
            ExtensionsAreaImpl.createExtensionPoints(it, this, extensionPoints, module)
          }
        }
      }
    }

    if (activity != null) {
      activity = activity.endAndStart("${activityNamePrefix}extension registration")
    }

    if (precomputedExtensionModel == null) {
      val immutableExtensionPoints = if (extensionPoints!!.isEmpty()) Collections.emptyMap() else java.util.Map.copyOf(extensionPoints)
      extensionArea.setPoints(immutableExtensionPoints)

      for (rootModule in modules) {
        executeRegisterTask(rootModule) { module ->
          module.registerExtensions(immutableExtensionPoints, getContainerDescriptor(module), listenerCallbacks)
        }
      }
    }
    else {
      registerExtensionPointsAndExtensionByPrecomputedModel(precomputedExtensionModel, listenerCallbacks)
    }

    activity?.end()

    if (componentConfigCount == -1) {
      componentConfigCount = newComponentConfigCount
    }

    // app - phase must be set before getMessageBus()
    if (parent == null && !LoadingState.COMPONENTS_REGISTERED.isOccurred /* loading plugin on the fly */) {
      StartUpMeasurer.setCurrentState(LoadingState.COMPONENTS_REGISTERED)
    }

    // ensure that messageBus is created, regardless of lazy listeners map state
    if (isMessageBusSupported) {
      val messageBus = getOrCreateMessageBusUnderLock()
      map?.let {
        (messageBus as MessageBusEx).setLazyListeners(it)
      }
    }
  }

  private fun registerExtensionPointsAndExtensionByPrecomputedModel(precomputedExtensionModel: PrecomputedExtensionModel,
                                                                    listenerCallbacks: List<Runnable>?) {
    assert(extensionArea.extensionPoints.isEmpty())
    val n = precomputedExtensionModel.pluginDescriptors.size
    if (n == 0) {
      return
    }

    val result = HashMap<String, ExtensionPointImpl<*>>(precomputedExtensionModel.extensionPointTotalCount)
    for (i in 0 until n) {
      ExtensionsAreaImpl.createExtensionPoints(precomputedExtensionModel.extensionPoints[i],
                                               this,
                                               result,
                                               precomputedExtensionModel.pluginDescriptors[i])
    }

    val immutableExtensionPoints = java.util.Map.copyOf(result)
    extensionArea.setPoints(immutableExtensionPoints)

    for ((name, pairs) in precomputedExtensionModel.nameToExtensions) {
      val point = immutableExtensionPoints.get(name) ?: continue
      for ((pluginDescriptor, list) in pairs) {
        if (!list.isEmpty()) {
          point.registerExtensions(list, pluginDescriptor, listenerCallbacks)
        }
      }
    }
  }

  private fun registerComponents(pluginDescriptor: IdeaPluginDescriptor, containerDescriptor: ContainerDescriptor, headless: Boolean): Int {
    var count = 0
    for (descriptor in (containerDescriptor.components ?: return 0)) {
      var implementationClassName = descriptor.implementationClass
      if (headless && descriptor.headlessImplementationClass != null) {
        if (descriptor.headlessImplementationClass.isEmpty()) {
          continue
        }

        implementationClassName = descriptor.headlessImplementationClass
      }

      if (descriptor.os != null && !isSuitableForOs(descriptor.os)) {
        continue
      }

      if (!isComponentSuitable(descriptor)) {
        continue
      }

      val componentClassName = descriptor.interfaceClass ?: descriptor.implementationClass!!
      try {
        registerComponent(
          interfaceClassName = componentClassName,
          implementationClassName = implementationClassName,
          config = descriptor,
          pluginDescriptor = pluginDescriptor,
        )
        count++
      }
      catch (e: Throwable) {
        handleInitComponentError(e, componentClassName, pluginDescriptor.pluginId)
      }
    }
    return count
  }

  fun createInitOldComponentsTask(): Runnable? {
    val components = componentAdapters.getImmutableSet().filterIsInstance<MyComponentAdapter>()
    if (components.isEmpty()) {
      return null
    }
    return Runnable {
      for (componentAdapter in componentAdapters.getImmutableSet()) {
        if (componentAdapter is MyComponentAdapter) {
          componentAdapter.getInstance<Any>(this, keyClass = null, indicator = null)
        }
      }
    }
  }

  protected fun createComponents(indicator: ProgressIndicator?) {
    LOG.assertTrue(containerState.get() == ContainerState.PRE_INIT)

    if (indicator != null) {
      indicator.isIndeterminate = false
    }

    val activity = when (val activityNamePrefix = activityNamePrefix()) {
      null -> null
      else -> StartUpMeasurer.startActivity("$activityNamePrefix${StartUpMeasurer.Activities.CREATE_COMPONENTS_SUFFIX}")
    }

    for (componentAdapter in componentAdapters.getImmutableSet()) {
      if (componentAdapter is MyComponentAdapter) {
        componentAdapter.getInstance<Any>(this, keyClass = null, indicator = indicator)
      }
    }

    activity?.end()

    LOG.assertTrue(containerState.compareAndSet(ContainerState.PRE_INIT, ContainerState.COMPONENT_CREATED))
  }

  @TestOnly
  fun registerComponentImplementation(componentKey: Class<*>, componentImplementation: Class<*>, shouldBeRegistered: Boolean) {
    checkState()
    var adapter = unregisterComponent(componentKey) as MyComponentAdapter?
    if (shouldBeRegistered) {
      LOG.assertTrue(adapter != null)
    }

    val pluginDescriptor = DefaultPluginDescriptor("test registerComponentImplementation")
    adapter = MyComponentAdapter(componentKey, componentImplementation.name, pluginDescriptor, this, componentImplementation)
    registerAdapter(adapter, adapter.pluginDescriptor)
    componentAdapters.add(adapter)
  }

  @TestOnly
  fun <T : Any> replaceComponentInstance(componentKey: Class<T>, componentImplementation: T, parentDisposable: Disposable?) {
    checkState()
    val adapter = getComponentAdapter(componentKey) as MyComponentAdapter
    adapter.replaceInstance(componentKey, componentImplementation, parentDisposable, null)
  }

  private fun registerComponent(
    interfaceClassName: String,
    implementationClassName: String?,
    config: ComponentConfig,
    pluginDescriptor: IdeaPluginDescriptor,
  ) {
    val interfaceClass = pluginDescriptor.classLoader.loadClass(interfaceClassName)
    val options = config.options
    if (config.overrides) {
      unregisterComponent(interfaceClass) ?: throw PluginException("$config does not override anything", pluginDescriptor.pluginId)
    }

    // implementationClass == null means we want to unregister this component
    if (implementationClassName == null) {
      return
    }

    if (options != null && java.lang.Boolean.parseBoolean(options.get("workspace")) &&
        !badWorkspaceComponents.contains(implementationClassName)) {
      LOG.error("workspace option is deprecated (implementationClass=$implementationClassName)")
    }

    val adapter = MyComponentAdapter(interfaceClass, implementationClassName, pluginDescriptor, this, null)
    registerAdapter(adapter, adapter.pluginDescriptor)
    componentAdapters.add(adapter)
  }

  open fun getApplication(): Application? {
    return if (parent == null || this is Application) this as Application else parent.getApplication()
  }

  protected fun registerServices(services: List<ServiceDescriptor>, pluginDescriptor: IdeaPluginDescriptor) {
    checkState()

    val app = getApplication()!!
    for (descriptor in services) {
      if (!isServiceSuitable(descriptor) || descriptor.os != null && !isSuitableForOs(descriptor.os)) {
        continue
      }

      // Allow to re-define service implementations in plugins.
      // Empty serviceImplementation means we want to unregister service.
      val key = descriptor.getInterface()
      if (descriptor.overrides && componentKeyToAdapter.remove(key) == null) {
        throw PluginException("Service $key doesn't override anything", pluginDescriptor.pluginId)
      }

      // empty serviceImplementation means we want to unregister service
      val implementation = when {
        descriptor.testServiceImplementation != null && app.isUnitTestMode -> descriptor.testServiceImplementation
        descriptor.headlessImplementation != null && app.isHeadlessEnvironment -> descriptor.headlessImplementation
        else -> descriptor.serviceImplementation
      }

      if (implementation != null) {
        val componentAdapter = ServiceComponentAdapter(descriptor, pluginDescriptor, this)
        val existingAdapter = componentKeyToAdapter.putIfAbsent(key, componentAdapter)
        if (existingAdapter != null) {
          throw PluginException("Key $key duplicated; existingAdapter: $existingAdapter; descriptor: ${descriptor.implementation}; app: $app; current plugin: ${pluginDescriptor.pluginId}", pluginDescriptor.pluginId)
        }
      }
    }
  }

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
        effectivePluginId = PluginManagerCore.getPluginDescriptorOrPlatformByClassName(componentClassName)?.pluginId
                            ?: PluginManagerCore.CORE_ID
      }

      throw PluginException("Fatal error initializing '$componentClassName'", error, effectivePluginId)
    }
    finally {
      handlingInitComponentError = false
    }
  }

  internal fun initializeComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId?) {
    if (serviceDescriptor == null || !isPreInitialized(component)) {
      LoadingState.CONFIGURATION_STORE_INITIALIZED.checkOccurred()
      componentStore.initComponent(component, serviceDescriptor, pluginId)
    }
  }

  protected open fun isPreInitialized(component: Any): Boolean {
    return component is PathMacroManager || component is IComponentStore || component is MessageBusFactory
  }

  protected abstract fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor

  final override fun <T : Any> getComponent(interfaceClass: Class<T>): T? {
    assertComponentsSupported()
    checkState()

    val adapter = getComponentAdapter(interfaceClass)
    if (adapter == null) {
      checkCanceledIfNotInClassInit()
      if (containerState.get() == ContainerState.DISPOSE_COMPLETED) {
        throwAlreadyDisposedError(interfaceClass.name, this, ProgressManager.getGlobalProgressIndicator())
      }
      return null
    }

    if (adapter is ServiceComponentAdapter) {
      LOG.error("$interfaceClass it is a service, use getService instead of getComponent")
    }

    if (adapter is BaseComponentAdapter) {
      if (parent != null && adapter.componentManager !== this) {
        LOG.error("getComponent must be called on appropriate container (current: $this, expected: ${adapter.componentManager})")
      }

      val indicator = ProgressManager.getGlobalProgressIndicator()
      if (containerState.get() == ContainerState.DISPOSE_COMPLETED) {
        adapter.throwAlreadyDisposedError(this, indicator)
      }
      return adapter.getInstance(adapter.componentManager, interfaceClass, indicator = indicator)
    }
    else {
      @Suppress("UNCHECKED_CAST")
      return adapter.getComponentInstance(this) as T
    }
  }

  override fun <T : Any> getService(serviceClass: Class<T>): T? {
    // `computeIfAbsent` cannot be used because of recursive update
    var result = serviceInstanceHotCache.get(serviceClass)
    if (result == null) {
      result = doGetService(serviceClass, true) ?: return null
      serviceInstanceHotCache.putIfAbsent(serviceClass, result)
    }
    @Suppress("UNCHECKED_CAST")
    return result as T?
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T : Any> getServiceIfCreated(serviceClass: Class<T>): T? {
    var result = serviceInstanceHotCache.get(serviceClass) as T?
    if (result == null) {
      result = doGetService(serviceClass, createIfNeeded = false)
      if (result != null) {
        serviceInstanceHotCache.putIfAbsent(serviceClass, result)
      }
    }
    return result
  }

  protected open fun <T : Any> doGetService(serviceClass: Class<T>, createIfNeeded: Boolean): T? {
    val key = serviceClass.name
    val adapter = componentKeyToAdapter.get(key)
    if (adapter is ServiceComponentAdapter) {
      if (createIfNeeded && containerState.get() == ContainerState.DISPOSE_COMPLETED) {
        throwAlreadyDisposedError(adapter.toString(), this, ProgressIndicatorProvider.getGlobalProgressIndicator())
      }
      return adapter.getInstance(this, serviceClass, createIfNeeded)
    }
    else if (adapter is LightServiceComponentAdapter) {
      if (createIfNeeded && containerState.get() == ContainerState.DISPOSE_COMPLETED) {
        throwAlreadyDisposedError(adapter.toString(), this, ProgressIndicatorProvider.getGlobalProgressIndicator())
      }

      @Suppress("UNCHECKED_CAST")
      return adapter.getComponentInstance(null) as T
    }

    if (isLightServiceSupported && isLightService(serviceClass)) {
      return if (createIfNeeded) getOrCreateLightService(serviceClass) else null
    }

    checkCanceledIfNotInClassInit()

    // if the container is fully disposed, all adapters may be removed
    if (containerState.get() == ContainerState.DISPOSE_COMPLETED) {
      if (!createIfNeeded) {
        return null
      }
      throwAlreadyDisposedError(serviceClass.name, this, ProgressIndicatorProvider.getGlobalProgressIndicator())
    }

    if (parent != null) {
      val result = parent.doGetService(serviceClass, createIfNeeded)
      if (result != null) {
        LOG.error("$key is registered as application service, but requested as project one")
        return result
      }
    }

    if (isLightServiceSupported && !serviceClass.isInterface && !Modifier.isFinal(serviceClass.modifiers) &&
        serviceClass.isAnnotationPresent(Service::class.java)) {
      throw PluginException.createByClass("Light service class $serviceClass must be final", null, serviceClass)
    }

    val result = getComponent(serviceClass) ?: return null
    PluginException.logPluginError(LOG,
      "$key requested as a service, but it is a component - " +
      "convert it to a service or change call to " +
      if (parent == null) "ApplicationManager.getApplication().getComponent()" else "project.getComponent()",
      null, serviceClass)
    return result
  }

  private fun <T : Any> getOrCreateLightService(serviceClass: Class<T>): T {
    checkThatCreatingOfLightServiceIsAllowed(serviceClass)
    synchronized(serviceClass) {
      val adapter = componentKeyToAdapter.get(serviceClass.name) as LightServiceComponentAdapter?
      if (adapter != null) {
        @Suppress("UNCHECKED_CAST")
        return adapter.getComponentInstance(null) as T
      }

      LoadingState.COMPONENTS_REGISTERED.checkOccurred()

      var result: T? = null
      if (ProgressIndicatorProvider.getGlobalProgressIndicator() == null) {
        result = createLightService(serviceClass)
      }
      else {
        ProgressManager.getInstance().executeNonCancelableSection {
          result = createLightService(serviceClass)
        }
      }
      result!!.let {
        registerAdapter(LightServiceComponentAdapter(it), pluginDescriptor = null)
        return it
      }
    }
  }

  private fun checkThatCreatingOfLightServiceIsAllowed(serviceClass: Class<*>) {
    if (isDisposed) {
      throwAlreadyDisposedError("light service ${serviceClass.name}", this, ProgressIndicatorProvider.getGlobalProgressIndicator())
    }

    // assertion only for non-platform plugins
    val classLoader = serviceClass.classLoader
    if (classLoader is PluginAwareClassLoader && !isGettingServiceAllowedDuringPluginUnloading(classLoader.pluginDescriptor)) {
      componentContainerIsReadonly?.let {
        val error = AlreadyDisposedException(
          "Cannot create light service ${serviceClass.name} because container in read-only mode (reason=$it, container=$this"
        )
        throw if (ProgressIndicatorProvider.getGlobalProgressIndicator() == null) error else ProcessCanceledException(error)
      }
    }
  }

  @Synchronized
  private fun getOrCreateMessageBusUnderLock(): MessageBus {
    var messageBus = this.messageBus
    if (messageBus != null) {
      return messageBus
    }

    messageBus = getApplication()!!.getService(MessageBusFactory::class.java).createMessageBus(this, parent?.messageBus) as MessageBusImpl
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

  protected open fun logMessageBusDelivery(topic: Topic<*>, messageName: String, handler: Any, duration: Long) {
    val loader = handler.javaClass.classLoader
    val pluginId = if (loader is PluginAwareClassLoader) loader.pluginId.idString else PluginManagerCore.CORE_ID.idString
    StartUpMeasurer.addPluginCost(pluginId, "MessageBus", duration)
  }

  /**
   * Use only if approved by core team.
   */
  fun registerComponent(key: Class<*>, implementation: Class<*>, pluginDescriptor: PluginDescriptor, override: Boolean) {
    assertComponentsSupported()
    checkState()

    val adapter = MyComponentAdapter(key, implementation.name, pluginDescriptor, this, implementation)
    if (override) {
      overrideAdapter(adapter, pluginDescriptor)
      componentAdapters.replace(adapter)
    }
    else {
      registerAdapter(adapter, pluginDescriptor)
      componentAdapters.add(adapter)
    }
  }

  private fun overrideAdapter(adapter: ComponentAdapter, pluginDescriptor: PluginDescriptor) {
    val componentKey = adapter.componentKey
    if (componentKeyToAdapter.put(componentKey, adapter) == null) {
      componentKeyToAdapter.remove(componentKey)
      throw PluginException("Key $componentKey doesn't override anything", pluginDescriptor.pluginId)
    }
  }

  /**
   * Use only if approved by core team.
   */
  fun registerService(serviceInterface: Class<*>,
                      implementation: Class<*>,
                      pluginDescriptor: PluginDescriptor,
                      override: Boolean,
                      preloadMode: PreloadMode = PreloadMode.FALSE) {
    checkState()

    val descriptor = ServiceDescriptor(serviceInterface.name, implementation.name, null, null, false,
                                       null, preloadMode, null, null)
    val adapter = ServiceComponentAdapter(descriptor, pluginDescriptor, this, implementation)
    if (override) {
      overrideAdapter(adapter, pluginDescriptor)
    }
    else {
      registerAdapter(adapter, pluginDescriptor)
    }
    serviceInstanceHotCache.remove(serviceInterface)
  }

  /**
   * Use only if approved by core team.
   */
  fun <T : Any> registerServiceInstance(serviceInterface: Class<T>, instance: T, pluginDescriptor: PluginDescriptor) {
    val serviceKey = serviceInterface.name
    checkState()

    val descriptor = ServiceDescriptor(serviceKey, instance.javaClass.name, null, null, false,
                                       null, PreloadMode.FALSE, null, null)
    componentKeyToAdapter.put(serviceKey, ServiceComponentAdapter(descriptor, pluginDescriptor, this, instance.javaClass, instance))
    serviceInstanceHotCache.put(serviceInterface, instance)
  }

  @TestOnly
  fun <T : Any> replaceServiceInstance(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable) {
    checkState()
    if (isLightService(serviceInterface)) {
      val adapter = LightServiceComponentAdapter(instance)
      val key = adapter.componentKey
      componentKeyToAdapter.put(key, adapter)
      Disposer.register(parentDisposable, Disposable {
        componentKeyToAdapter.remove(key)
        serviceInstanceHotCache.remove(serviceInterface)
      })
      serviceInstanceHotCache.put(serviceInterface, instance)
    }
    else {
      val adapter = componentKeyToAdapter.get(serviceInterface.name) as ServiceComponentAdapter
      adapter.replaceInstance(serviceInterface, instance, parentDisposable, serviceInstanceHotCache)
    }
  }

  fun <T : Any> replaceRegularServiceInstance(serviceInterface: Class<T>, instance: T) {
    checkState()
    val adapter = componentKeyToAdapter.get(serviceInterface.name) as ServiceComponentAdapter
    (adapter.replaceInstance(serviceInterface, instance, serviceParentDisposable, serviceInstanceHotCache) as? Disposable)?.let {
      Disposer.dispose(it)
    }
    serviceInstanceHotCache.put(serviceInterface, instance)
  }

  private fun <T : Any> createLightService(serviceClass: Class<T>): T {
    val startTime = StartUpMeasurer.getCurrentTime()
    val pluginId = (serviceClass.classLoader as? PluginAwareClassLoader)?.pluginId ?: PluginManagerCore.CORE_ID

    val result = instantiateClass(serviceClass, pluginId)
    if (result is Disposable) {
      Disposer.register(serviceParentDisposable, result)
    }

    initializeComponent(result, null, pluginId)
    StartUpMeasurer.addCompletedActivity(startTime, serviceClass, getActivityCategory(isExtension = false), pluginId.idString)
    return result
  }

  final override fun <T : Any> loadClass(className: String, pluginDescriptor: PluginDescriptor): Class<T> {
    @Suppress("UNCHECKED_CAST")
    return doLoadClass(className, pluginDescriptor) as Class<T>
  }

  final override fun <T : Any> instantiateClass(aClass: Class<T>, pluginId: PluginId): T {
    checkCanceledIfNotInClassInit()

    try {
      if (parent == null) {
        @Suppress("UNCHECKED_CAST")
        return MethodHandles.privateLookupIn(aClass, methodLookup).findConstructor(aClass, emptyConstructorMethodType).invoke() as T
      }
      else {
        val constructors: Array<Constructor<*>> = aClass.declaredConstructors
        var constructor = if (constructors.size > 1) {
          // see ConfigurableEP - prefer constructor that accepts our instance
          constructors.firstOrNull { it.parameterCount == 1 && it.parameterTypes[0].isAssignableFrom(javaClass) }
        }
        else {
          null
        }

        if (constructor == null) {
          constructors.sortBy { it.parameterCount }
          constructor = constructors.first()
        }

        constructor.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        if (constructor.parameterCount == 1) {
          return constructor.newInstance(getActualContainerInstance()) as T
        }
        else {
          @Suppress("UNCHECKED_CAST")
          return MethodHandles.privateLookupIn(aClass, methodLookup).unreflectConstructor(constructor).invoke() as T
        }
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

      throw PluginException("Cannot create class ${aClass.name} (classloader=${aClass.classLoader})", e, pluginId)
    }
  }

  protected open fun getActualContainerInstance(): ComponentManager = this

  final override fun <T : Any> instantiateClassWithConstructorInjection(aClass: Class<T>, key: Any, pluginId: PluginId): T {
    return instantiateUsingPicoContainer(aClass, key, pluginId, this, constructorParameterResolver)
  }

  internal open val isGetComponentAdapterOfTypeCheckEnabled: Boolean
    get() = true

  final override fun <T : Any> instantiateClass(className: String, pluginDescriptor: PluginDescriptor): T {
    val pluginId = pluginDescriptor.pluginId
    try {
      @Suppress("UNCHECKED_CAST")
      return instantiateClass(doLoadClass(className, pluginDescriptor) as Class<T>, pluginId)
    }
    catch (e: Throwable) {
      when {
        e is PluginException || e is ExtensionNotApplicableException || e is ProcessCanceledException -> throw e
        e.cause is NoSuchMethodException || e.cause is IllegalArgumentException -> {
          throw PluginException("Class constructor must not have parameters: $className", e, pluginId)
        }
        else -> throw PluginException(e, pluginDescriptor.pluginId)
      }
    }
  }

  final override fun createListener(descriptor: ListenerDescriptor): Any {
    val pluginDescriptor = descriptor.pluginDescriptor
    val aClass = try {
      doLoadClass(descriptor.listenerClassName, pluginDescriptor)
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
    return when (val effectiveError: Throwable = if (error is InvocationTargetException) error.targetException else error) {
      is ProcessCanceledException, is ExtensionNotApplicableException, is PluginException -> effectiveError as RuntimeException
      else -> PluginException(effectiveError, pluginId)
    }
  }

  final override fun createError(message: String, pluginId: PluginId) = PluginException(message, pluginId)

  final override fun createError(message: String,
                                 error: Throwable?,
                                 pluginId: PluginId, 
                                 attachments: MutableMap<String, String>?): RuntimeException {
    return PluginException(message, error, pluginId, attachments?.map { Attachment(it.key, it.value) } ?: emptyList())
  }

  open fun unloadServices(services: List<ServiceDescriptor>, pluginId: PluginId) {
    checkState()

    if (!services.isEmpty()) {
      val store = componentStore
      for (service in services) {
        val adapter = (componentKeyToAdapter.remove(service.`interface`) ?: continue) as ServiceComponentAdapter
        val instance = adapter.getInitializedInstance() ?: continue
        if (instance is Disposable) {
          Disposer.dispose(instance)
        }
        store.unloadComponent(instance)
      }
    }

    if (isLightServiceSupported) {
      val store = componentStore
      val iterator = componentKeyToAdapter.values.iterator()
      while (iterator.hasNext()) {
        val adapter = iterator.next() as? LightServiceComponentAdapter ?: continue
        val instance = adapter.getComponentInstance(null)
        if ((instance.javaClass.classLoader as? PluginAwareClassLoader)?.pluginId == pluginId) {
          if (instance is Disposable) {
            Disposer.dispose(instance)
          }
          store.unloadComponent(instance)
          iterator.remove()
        }
      }
    }

    serviceInstanceHotCache.clear()
  }

  open fun activityNamePrefix(): String? = null

  class PreloadServicesResult(@JvmField val sync: CompletableFuture<*>, @JvmField val async: CompletableFuture<*>)

  open fun preloadServices(modules: Sequence<IdeaPluginDescriptorImpl>,
                           activityPrefix: String,
                           onlyIfAwait: Boolean = false): PreloadServicesResult {
    val asyncPreloadedServices = mutableListOf<ForkJoinTask<*>>()
    val syncPreloadedServices = mutableListOf<ForkJoinTask<*>>()
    for (plugin in modules) {
      serviceLoop@ for (service in getContainerDescriptor(plugin).services) {
        if (!isServiceSuitable(service) || service.os != null && !isSuitableForOs(service.os)) {
          continue@serviceLoop
        }
        val list: MutableList<ForkJoinTask<*>> = when (service.preload) {
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
          PreloadMode.AWAIT -> syncPreloadedServices
          PreloadMode.FALSE -> continue@serviceLoop
          else -> throw IllegalStateException("Unknown preload mode ${service.preload}")
        }

        list.add(ForkJoinTask.adapt task@{
          if (isServicePreloadingCancelled || isDisposed) {
            return@task
          }

          val activity = StartUpMeasurer.startActivity("${service.`interface`} preloading")
          try {
            preloadService(service)
          }
          catch (ignore: AlreadyDisposedException) {
          }
          catch (e: StartupAbortedException) {
            isServicePreloadingCancelled = true
            throw e
          }

          activity.end()
        })
      }
    }

    return PreloadServicesResult(
      sync = CompletableFuture.runAsync({
                                          runActivity("${activityPrefix}service sync preloading") {
                                            ForkJoinTask.invokeAll(syncPreloadedServices)
                                          }
                                        }, ForkJoinPool.commonPool()),
      async = CompletableFuture.runAsync({
                                           runActivity("${activityPrefix}service async preloading") {
                                             ForkJoinTask.invokeAll(asyncPreloadedServices)
                                           }
                                         }, ForkJoinPool.commonPool())
    )
  }

  protected open fun preloadService(service: ServiceDescriptor) {
    val adapter = componentKeyToAdapter.get(service.getInterface()) as ServiceComponentAdapter? ?: return
    if (adapter.isInitializing) {
      // already in initialization
      return
    }

    val instance = adapter.getInstance<Any>(this, null)
    if (instance != null) {
      val implClass = instance.javaClass
      // well, we don't know the interface class, so, we cannot add any service to a hot cache
      if (Modifier.isFinal(implClass.modifiers)) {
        serviceInstanceHotCache.putIfAbsent(implClass, instance)
      }
    }
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

  fun startDispose() {
    stopServicePreloading()

    Disposer.disposeChildren(this, null)

    val messageBus = messageBus
    // There is a chance that someone will try to connect to the message bus and will get NPE because of disposed connection disposable,
    // because the container state is not yet set to DISPOSE_IN_PROGRESS.
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

    // release references to the service instances
    componentKeyToAdapter.clear()
    componentAdapters.clear()
    serviceInstanceHotCache.clear()

    val messageBus = messageBus
    if (messageBus != null) {
      // Must be after disposing of serviceParentDisposable, because message bus disposes child buses, so, we must dispose all services first.
      // For example, service ModuleManagerImpl disposes modules; each module, in turn, disposes module's message bus (child bus of application).
      Disposer.dispose(messageBus)
      this.messageBus = null
    }

    if (!containerState.compareAndSet(ContainerState.DISPOSED, ContainerState.DISPOSE_COMPLETED)) {
      throw IllegalStateException("Expected current state is DISPOSED, but actual state is ${containerState.get()} ($this)")
    }

    componentConfigCount = -1
  }

  fun stopServicePreloading() {
    isServicePreloadingCancelled = true
  }

  @Suppress("DEPRECATION")
  final override fun getComponent(name: String): BaseComponent? {
    checkState()
    for (componentAdapter in componentKeyToAdapter.values) {
      if (componentAdapter is MyComponentAdapter) {
        val instance = componentAdapter.getInitializedInstance()
        if (instance is BaseComponent && name == instance.componentName) {
          return instance
        }
      }
    }
    return null
  }

  internal fun componentCreated(indicator: ProgressIndicator?) {
    instantiatedComponentCount++

    if (indicator != null) {
      indicator.checkCanceled()
      setProgressDuringInit(indicator)
    }
  }

  final override fun <T : Any> getServiceByClassName(serviceClassName: String): T? {
    checkState()
    val adapter = componentKeyToAdapter.get(serviceClassName) as ServiceComponentAdapter?
    return adapter?.getInstance(this, keyClass = null)
  }

  open fun isServiceSuitable(descriptor: ServiceDescriptor): Boolean {
    return descriptor.client == null
  }

  protected open fun isComponentSuitable(componentConfig: ComponentConfig): Boolean {
    val options = componentConfig.options ?: return true
    return !java.lang.Boolean.parseBoolean(options.get("internal")) || ApplicationManager.getApplication().isInternal
  }

  final override fun getDisposed(): Condition<*> = Condition<Any?> { isDisposed }

  fun processInitializedComponentsAndServices(processor: (Any) -> Unit) {
    for (adapter in componentKeyToAdapter.values) {
      if (adapter is BaseComponentAdapter) {
        processor(adapter.getInitializedInstance() ?: continue)
      }
      else if (adapter is LightServiceComponentAdapter) {
        processor(adapter.getComponentInstance(null))
      }
    }
  }

  fun processAllImplementationClasses(processor: (componentClass: Class<*>, plugin: PluginDescriptor?) -> Unit) {
    for (adapter in componentKeyToAdapter.values) {
      if (adapter is ServiceComponentAdapter) {
        val aClass = try {
          adapter.getImplementationClass()
        }
        catch (e: Throwable) {
          // well, the component is registered, but the required jar is not added to the classpath (community edition or junior IDE)
          LOG.warn(e)
          continue
        }

        processor(aClass, adapter.pluginDescriptor)
      }
      else {
        val pluginDescriptor = if (adapter is BaseComponentAdapter) adapter.pluginDescriptor else null
        if (pluginDescriptor != null) {
          val aClass = try {
            adapter.componentImplementation
          }
          catch (e: Throwable) {
            LOG.warn(e)
            continue
          }

          processor(aClass, pluginDescriptor)
        }
      }
    }
  }

  final override fun getComponentAdapter(componentKey: Any): ComponentAdapter? {
    assertComponentsSupported()

    val adapter = getFromCache(componentKey)
    return if (adapter == null && parent != null) parent.getComponentAdapter(componentKey) else adapter
  }

  private fun getFromCache(componentKey: Any): ComponentAdapter? {
    val adapter = componentKeyToAdapter.get(componentKey)
    if (adapter == null) {
      return if (componentKey is Class<*>) componentKeyToAdapter.get(componentKey.name) else null
    }
    else {
      return adapter
    }
  }

  fun unregisterComponent(componentKey: Any): ComponentAdapter? {
    assertComponentsSupported()

    val adapter = componentKeyToAdapter.remove(componentKey) ?: return null
    componentAdapters.remove(adapter)
    if (componentKey is Class<*>) {
      serviceInstanceHotCache.remove(componentKey)
    }
    return adapter
  }

  final override fun getComponentInstance(componentKey: Any): Any? {
    assertComponentsSupported()

    val adapter = getFromCache(componentKey)
    if (adapter == null) {
      return parent?.getComponentInstance(componentKey)
    }
    else {
      return adapter.getComponentInstance(this)
    }
  }

  final override fun getComponentInstanceOfType(componentType: Class<*>): Any? {
    throw UnsupportedOperationException("Do not use getComponentInstanceOfType()")
  }

  fun registerComponentInstance(componentKey: Any, componentInstance: Any): ComponentAdapter {
    assertComponentsSupported()

    val componentAdapter = object : ComponentAdapter {
      override fun getComponentInstance(container: PicoContainer) = componentInstance

      override fun getComponentKey() = componentKey

      override fun getComponentImplementation() = componentInstance.javaClass

      override fun toString() = "${javaClass.name}[$componentKey]"
    }
    if (componentKeyToAdapter.putIfAbsent(componentAdapter.componentKey, componentAdapter) != null) {
      throw IllegalStateException("Key ${componentAdapter.componentKey} duplicated")
    }
    componentAdapters.add(componentAdapter)
    return componentAdapter
  }

  private fun assertComponentsSupported() {
    if (!isComponentSupported) {
      error("components aren't support")
    }
  }

  // project level extension requires Project as constructor argument, so, for now, constructor injection is disabled only for app level
  final override fun isInjectionForExtensionSupported() = parent != null

  internal fun getComponentAdapterOfType(componentType: Class<*>): ComponentAdapter? {
    componentKeyToAdapter.get(componentType)?.let {
      return it
    }

    for (adapter in componentAdapters.getImmutableSet()) {
      val descendant = adapter.componentImplementation
      if (componentType === descendant || componentType.isAssignableFrom(descendant)) {
        return adapter
      }
    }
    return null
  }

  final override fun <T : Any> processInitializedComponents(aClass: Class<T>, processor: (T, PluginDescriptor) -> Unit) {
    // We must use instances only from our adapter (could be service or something else).
    // unsafeGetAdapters should be not used here as ProjectManagerImpl uses it to call projectOpened
    for (adapter in componentAdapters.getImmutableSet()) {
      if (adapter is MyComponentAdapter) {
        val component = adapter.getInitializedInstance()
        if (component != null && aClass.isAssignableFrom(component.javaClass)) {
          @Suppress("UNCHECKED_CAST")
          processor(component as T, adapter.pluginDescriptor)
        }
      }
    }
  }

  final override fun getActivityCategory(isExtension: Boolean): ActivityCategory {
    return when {
      parent == null -> if (isExtension) ActivityCategory.APP_EXTENSION else ActivityCategory.APP_SERVICE
      parent.parent == null -> if (isExtension) ActivityCategory.PROJECT_EXTENSION else ActivityCategory.PROJECT_SERVICE
      else -> if (isExtension) ActivityCategory.MODULE_EXTENSION else ActivityCategory.MODULE_SERVICE
    }
  }

  final override fun hasComponent(componentKey: Class<*>): Boolean {
    val adapter = componentKeyToAdapter.get(componentKey) ?: componentKeyToAdapter.get(componentKey.name)
    return adapter != null || (parent != null && parent.hasComponent(componentKey))
  }

  @Deprecated(message = "Use extensions", level = DeprecationLevel.ERROR)
  final override fun <T : Any> getComponents(baseClass: Class<T>): Array<T> {
    checkState()
    val result = mutableListOf<T>()
    // we must use instances only from our adapter (could be service or something else)
    for (componentAdapter in componentAdapters.getImmutableSet()) {
      if (componentAdapter is MyComponentAdapter) {
        val implementationClass = componentAdapter.getImplementationClass()
        if (baseClass === implementationClass || baseClass.isAssignableFrom(implementationClass)) {
          val instance = componentAdapter.getInstance<T>(componentManager = this, keyClass = null, createIfNeeded = false)
          if (instance != null) {
            result.add(instance)
          }
        }
      }
    }
    @Suppress("DEPRECATION")
    return ArrayUtil.toObjectArray(result, baseClass)
  }

  final override fun isSuitableForOs(os: ExtensionDescriptor.Os): Boolean {
    return when (os) {
      ExtensionDescriptor.Os.mac -> SystemInfoRt.isMac
      ExtensionDescriptor.Os.linux -> SystemInfoRt.isLinux
      ExtensionDescriptor.Os.windows -> SystemInfoRt.isWindows
      ExtensionDescriptor.Os.unix -> SystemInfoRt.isUnix
      ExtensionDescriptor.Os.freebsd -> SystemInfoRt.isFreeBSD
      else -> throw IllegalArgumentException("Unknown OS '$os'")
    }
  }
}

/**
 * A linked hash set that's copied on write operations.
 */
private class LinkedHashSetWrapper<T : Any> {
  private val lock = Any()

  @Volatile
  private var immutableSet: Set<T>? = null
  private var synchronizedSet = LinkedHashSet<T>()

  fun add(element: T) {
    synchronized(lock) {
      if (!synchronizedSet.contains(element)) {
        copySyncSetIfExposedAsImmutable().add(element)
      }
    }
  }

  private fun copySyncSetIfExposedAsImmutable(): LinkedHashSet<T> {
    if (immutableSet != null) {
      immutableSet = null
      synchronizedSet = LinkedHashSet(synchronizedSet)
    }
    return synchronizedSet
  }

  fun remove(element: T) {
    synchronized(lock) { copySyncSetIfExposedAsImmutable().remove(element) }
  }

  fun replace(element: T) {
    synchronized(lock) {
      val set = copySyncSetIfExposedAsImmutable()
      set.remove(element)
      set.add(element)
    }
  }

  fun clear() {
    synchronized(lock) {
      immutableSet = null
      synchronizedSet = LinkedHashSet()
    }
  }

  fun getImmutableSet(): Set<T> {
    var result = immutableSet
    if (result == null) {
      synchronized(lock) {
        result = immutableSet
        if (result == null) {
          // Expose the same set as immutable. It should never be modified again. Next add/remove operations will copy synchronizedSet
          result = Collections.unmodifiableSet(synchronizedSet)
          immutableSet = result
        }
      }
    }
    return result!!
  }
}

private fun createPluginExceptionIfNeeded(error: Throwable, pluginId: PluginId): RuntimeException {
  return if (error is PluginException) error else PluginException(error, pluginId)
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
      effectivePluginId = PluginManager.getPluginByClassNameAsNoAccessToClass(componentClassName)
    }
  }

  if (effectivePluginId != null && PluginManagerCore.CORE_ID != effectivePluginId) {
    throw StartupAbortedException("Fatal error initializing plugin $effectivePluginId", PluginException(t, effectivePluginId))
  }
  else {
    throw StartupAbortedException("Fatal error initializing '$componentClassName'", t)
  }
}

private fun doLoadClass(name: String, pluginDescriptor: PluginDescriptor): Class<*> {
  // maybe null in unit tests
  val classLoader = pluginDescriptor.pluginClassLoader ?: ComponentManagerImpl::class.java.classLoader
  if (classLoader is PluginAwareClassLoader) {
    return classLoader.tryLoadingClass(name, true) ?: throw ClassNotFoundException("$name $classLoader")
  }
  else {
    return classLoader.loadClass(name)
  }
}

private class LightServiceComponentAdapter(private val initializedInstance: Any) : ComponentAdapter {
  override fun getComponentKey(): String = initializedInstance.javaClass.name

  override fun getComponentImplementation() = initializedInstance.javaClass

  override fun getComponentInstance(container: PicoContainer?) = initializedInstance

  override fun toString() = componentKey
}

private inline fun executeRegisterTask(mainPluginDescriptor: IdeaPluginDescriptorImpl,
                                       crossinline task: (IdeaPluginDescriptorImpl) -> Unit) {
  task(mainPluginDescriptor)
  executeRegisterTaskForOldContent(mainPluginDescriptor, task)
}