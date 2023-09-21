// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceNegatedIsEmptyWithIsNotEmpty", "ReplaceGetOrSet", "ReplacePutWithAssignment")
package com.intellij.serviceContainer

import com.intellij.concurrency.resetThreadContext
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.idea.AppMode.isLightEdit
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.components.*
import com.intellij.openapi.components.ServiceDescriptor.PreloadMode
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.*
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.extensions.impl.createExtensionPoints
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.attachAsChildTo
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.messages.*
import com.intellij.util.messages.impl.MessageBusEx
import com.intellij.util.messages.impl.MessageBusImpl
import com.intellij.util.namedChildScope
import com.intellij.util.runSuppressing
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentHashMapOf
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.picocontainer.ComponentAdapter
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference

internal val LOG: Logger
  get() = logger<ComponentManagerImpl>()

private val methodLookup = MethodHandles.lookup()
@JvmField
@Internal
val emptyConstructorMethodType: MethodType = MethodType.methodType(Void.TYPE)
@JvmField
@Internal
val coroutineScopeMethodType: MethodType = MethodType.methodType(Void.TYPE, CoroutineScope::class.java)
private val applicationMethodType = MethodType.methodType(Void.TYPE, Application::class.java)

@Internal
fun MethodHandles.Lookup.findConstructorOrNull(clazz: Class<*>, type: MethodType): MethodHandle? {
  return try {
    findConstructor(clazz, type)
  }
  catch (e: NoSuchMethodException) {
    return null
  }
  catch (e: IllegalAccessException) {
    return null
  }
}

@Internal
abstract class ComponentManagerImpl(
  internal val parent: ComponentManagerImpl?,
  private val coroutineScope: CoroutineScope,
  setExtensionsRootArea: Boolean = parent == null,
) : ComponentManager, Disposable.Parent, MessageBusOwner, UserDataHolderBase(), ComponentManagerEx, ComponentStoreOwner {
  protected enum class ContainerState {
    PRE_INIT, COMPONENT_CREATED, DISPOSE_IN_PROGRESS, DISPOSED, DISPOSE_COMPLETED
  }

  companion object {
    @Internal
    @JvmField val fakeCorePluginDescriptor = DefaultPluginDescriptor(PluginManagerCore.CORE_ID, null)

    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
    @Internal
    @JvmField val badWorkspaceComponents: Set<String> = java.util.Set.of(
      "jetbrains.buildServer.codeInspection.InspectionPassRegistrar",
      "jetbrains.buildServer.testStatus.TestStatusPassRegistrar",
      "jetbrains.buildServer.customBuild.lang.gutterActions.CustomBuildParametersGutterActionsHighlightingPassRegistrar",
    )

    // not as a file level function to avoid scope cluttering
    @Internal
    fun createAllServices(componentManager: ComponentManagerImpl, requireEdt: Set<String>, requireReadAction: Set<String>) {
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

          val init = { o.getInstance<Any>(componentManager, null) }
          when {
            requireEdt.contains(implementation) -> invokeAndWaitIfNeeded(null, init)
            requireReadAction.contains(implementation) -> runReadAction(init)
            else -> init()
          }
        }
        catch (e: Throwable) {
          LOG.error("Cannot create $implementation", e)
        }
      }
    }
  }

  private val componentKeyToAdapter = ConcurrentHashMap<Any, ComponentAdapter>()
  private val componentAdapters = LinkedHashSetWrapper<MyComponentAdapter>()

  protected val containerState = AtomicReference(ContainerState.PRE_INIT)

  protected val containerStateName: String
    get() = containerState.get().name

  private val _extensionArea by lazy { ExtensionsAreaImpl(this) }

  private var messageBus: MessageBusImpl? = null

  @Volatile
  private var isServicePreloadingCancelled = false

  private fun debugString(short: Boolean = false): String {
    return "${if (short) javaClass.simpleName else javaClass.name}@${System.identityHashCode(this)}"
  }

  internal val serviceParentDisposable: Disposable = Disposer.newDisposable("services of ${debugString()}")

  protected open val isLightServiceSupported = parent?.parent == null
  protected open val isMessageBusSupported = parent?.parent == null
  protected open val isComponentSupported = true
  protected open val isExtensionSupported = true

  @Volatile
  @JvmField
  internal var componentContainerIsReadonly: String? = null

  @Suppress("MemberVisibilityCanBePrivate")
  fun getCoroutineScope(): CoroutineScope {
    return if (parent?.parent == null) {
      coroutineScope
    }
    else {
      throw RuntimeException("Module doesn't have coroutineScope")
    }
  }

  override val componentStore: IComponentStore
    get() = getService(IComponentStore::class.java)!!

  init {
    if (setExtensionsRootArea) {
      Extensions.setRootArea(_extensionArea)
    }
  }

  internal fun getComponentInstance(componentKey: Any): Any? {
    assertComponentsSupported()

    val adapter = componentKeyToAdapter.get(componentKey)
                  ?: if (componentKey is Class<*>) componentKeyToAdapter.get(componentKey.name) else null
    return if (adapter == null) parent?.getComponentInstance(componentKey) else adapter.componentInstance
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

  override fun getMessageBus(): MessageBus {
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

  final override fun getExtensionArea(): ExtensionsAreaImpl {
    if (!isExtensionSupported) {
      error("Extensions aren't supported")
    }
    return _extensionArea
  }

  fun registerComponents() {
    registerComponents(modules = PluginManagerCore.getPluginSet().getEnabledModules(),
                       app = getApplication(),
                       precomputedExtensionModel = null,
                       listenerCallbacks = null)
  }

  open fun registerComponents(modules: List<IdeaPluginDescriptorImpl>,
                              app: Application?,
                              precomputedExtensionModel: PrecomputedExtensionModel?,
                              listenerCallbacks: MutableList<in Runnable>?) {
    val activityNamePrefix = activityNamePrefix()

    var map: ConcurrentMap<String, MutableList<ListenerDescriptor>>? = null
    val isHeadless = app == null || app.isHeadlessEnvironment
    val isUnitTestMode = app?.isUnitTestMode ?: false

    var activity = activityNamePrefix?.let { StartUpMeasurer.startActivity("${it}service and ep registration") }

    // register services before registering extensions because plugins can access services in their extensions,
    // which can be invoked right away if the plugin is loaded dynamically
    val extensionPoints = if (precomputedExtensionModel == null) HashMap(extensionArea.extensionPoints) else null
    for (rootModule in modules) {
      executeRegisterTask(rootModule) { module ->
        val containerDescriptor = getContainerDescriptor(module)
        registerServices(containerDescriptor.services, module)
        registerComponents(module, containerDescriptor, isHeadless)

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
            createExtensionPoints(points = it, componentManager = this, result = extensionPoints, pluginDescriptor = module)
          }
        }
      }
    }

    if (activity != null) {
      activity = activity.endAndStart("${activityNamePrefix}extension registration")
    }

    if (precomputedExtensionModel == null) {
      val immutableExtensionPoints = if (extensionPoints!!.isEmpty()) Collections.emptyMap() else java.util.Map.copyOf(extensionPoints)
      extensionArea.extensionPoints = immutableExtensionPoints

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

    // app - phase must be set before getMessageBus()
    if (parent == null && !LoadingState.COMPONENTS_REGISTERED.isOccurred /* loading plugin on the fly */) {
      LoadingState.setCurrentState(LoadingState.COMPONENTS_REGISTERED)
    }

    // ensuring that `messageBus` is created, regardless of the lazy listener map state
    if (isMessageBusSupported) {
      val messageBus = getOrCreateMessageBusUnderLock()
      map?.let {
        (messageBus as MessageBusEx).setLazyListeners(it)
      }
    }
  }

  private fun registerExtensionPointsAndExtensionByPrecomputedModel(precomputedExtensionModel: PrecomputedExtensionModel,
                                                                    listenerCallbacks: MutableList<in Runnable>?) {
    assert(extensionArea.extensionPoints.isEmpty())
    val n = precomputedExtensionModel.pluginDescriptors.size
    if (n == 0) {
      return
    }

    val result = HashMap<String, ExtensionPointImpl<*>>(precomputedExtensionModel.extensionPointTotalCount)
    for (i in 0 until n) {
      createExtensionPoints(points = precomputedExtensionModel.extensionPoints[i],
                            componentManager = this,
                            result = result,
                            pluginDescriptor = precomputedExtensionModel.pluginDescriptors[i])
    }

    val immutableExtensionPoints = java.util.Map.copyOf(result)
    extensionArea.extensionPoints = immutableExtensionPoints

    for ((name, pairs) in precomputedExtensionModel.nameToExtensions) {
      val point = immutableExtensionPoints.get(name) ?: continue
      for ((pluginDescriptor, list) in pairs) {
        if (!list.isEmpty()) {
          point.registerExtensions(list, pluginDescriptor, listenerCallbacks)
        }
      }
    }
  }

  private fun registerComponents(pluginDescriptor: IdeaPluginDescriptor, containerDescriptor: ContainerDescriptor, headless: Boolean) {
    for (descriptor in (containerDescriptor.components ?: return)) {
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
      }
      catch (e: StartupAbortedException) {
        throw e
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: ProcessCanceledException) {
        throw e
      }
      catch (e: PluginException) {
        throw e
      }
      catch (e: Throwable) {
        throw PluginException("Fatal error initializing '$componentClassName'", e, pluginDescriptor.pluginId)
      }
    }
  }

  fun createInitOldComponentsTask(): (() -> Unit)? {
    if (componentAdapters.getImmutableSet().isEmpty()) {
      containerState.compareAndSet(ContainerState.PRE_INIT, ContainerState.COMPONENT_CREATED)
      return null
    }

    return {
      for (componentAdapter in componentAdapters.getImmutableSet()) {
        componentAdapter.getInstance<Any>(this, keyClass = null)
      }
      containerState.compareAndSet(ContainerState.PRE_INIT, ContainerState.COMPONENT_CREATED)
    }
  }

  @Suppress("DuplicatedCode")
  @Deprecated(message = "Use createComponentsNonBlocking")
  protected fun createComponents() {
    LOG.assertTrue(containerState.get() == ContainerState.PRE_INIT)

    val activity = when (val activityNamePrefix = activityNamePrefix()) {
      null -> null
      else -> StartUpMeasurer.startActivity("$activityNamePrefix${StartUpMeasurer.Activities.CREATE_COMPONENTS_SUFFIX}")
    }

    for (componentAdapter in componentAdapters.getImmutableSet()) {
      componentAdapter.getInstance<Any>(this, keyClass = null)
    }

    activity?.end()

    LOG.assertTrue(containerState.compareAndSet(ContainerState.PRE_INIT, ContainerState.COMPONENT_CREATED))
  }

  @Suppress("DuplicatedCode")
  @Internal
  suspend fun createComponentsNonBlocking() {
    LOG.assertTrue(containerState.get() == ContainerState.PRE_INIT)

    val activity = when (val activityNamePrefix = activityNamePrefix()) {
      null -> null
      else -> StartUpMeasurer.startActivity("$activityNamePrefix${StartUpMeasurer.Activities.CREATE_COMPONENTS_SUFFIX}")
    }

    for (componentAdapter in componentAdapters.getImmutableSet()) {
      componentAdapter.getInstanceAsync<Any>(this, keyClass = null)
    }

    activity?.end()

    LOG.assertTrue(containerState.compareAndSet(ContainerState.PRE_INIT, ContainerState.COMPONENT_CREATED))
  }

  @TestOnly
  fun registerComponentImplementation(key: Class<*>, implementation: Class<*>, shouldBeRegistered: Boolean) {
    checkState()
    val oldAdapter = componentKeyToAdapter.remove(key) as MyComponentAdapter?
    if (shouldBeRegistered) {
      LOG.assertTrue(oldAdapter != null)
    }

    val pluginDescriptor = oldAdapter?.pluginDescriptor ?: DefaultPluginDescriptor("test registerComponentImplementation")
    val newAdapter = MyComponentAdapter(componentKey = key,
                                        implementationClassName = implementation.name,
                                        pluginDescriptor = pluginDescriptor,
                                        componentManager = this,
                                        deferred = CompletableDeferred(),
                                        implementationClass = implementation)
    componentKeyToAdapter.put(key, newAdapter)
    if (oldAdapter == null) {
      componentAdapters.add(newAdapter)
    }
    else {
      componentAdapters.replace(oldAdapter, newAdapter)
    }
  }

  @TestOnly
  fun <T : Any> replaceComponentInstance(componentKey: Class<T>, componentImplementation: T, parentDisposable: Disposable?) {
    checkState()
    val oldAdapter = componentKeyToAdapter.get(componentKey) as MyComponentAdapter
    val implClass = componentImplementation::class.java
    val newAdapter = MyComponentAdapter(componentKey = componentKey,
                                        implementationClassName = implClass.name,
                                        pluginDescriptor = oldAdapter.pluginDescriptor,
                                        componentManager = this,
                                        deferred = CompletableDeferred(value = componentImplementation),
                                        implementationClass = implClass)
    componentKeyToAdapter.put(componentKey, newAdapter)
    componentAdapters.replace(oldAdapter, newAdapter)
    if (parentDisposable != null) {
      Disposer.register(parentDisposable) {
        @Suppress("DEPRECATION")
        if (componentImplementation is Disposable && !Disposer.isDisposed(componentImplementation)) {
          Disposer.dispose(componentImplementation)
        }
        componentKeyToAdapter.put(componentKey, oldAdapter)
        componentAdapters.replace(newAdapter, oldAdapter)
      }
    }
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

    val adapter = MyComponentAdapter(interfaceClass, implementationClassName, pluginDescriptor, this, CompletableDeferred(), null)
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
      // Empty serviceImplementation means we want unregistering service.

      // empty serviceImplementation means we want unregistering service
      val implementation = when {
        descriptor.testServiceImplementation != null && app.isUnitTestMode -> descriptor.testServiceImplementation
        descriptor.headlessImplementation != null && app.isHeadlessEnvironment -> descriptor.headlessImplementation
        else -> descriptor.serviceImplementation
      }

      val key = descriptor.serviceInterface ?: implementation
      if (descriptor.overrides && componentKeyToAdapter.remove(key) == null) {
        throw PluginException("Service $key doesn't override anything", pluginDescriptor.pluginId)
      }

      if (implementation != null) {
        val componentAdapter = ServiceComponentAdapter(descriptor, pluginDescriptor, this)
        val existingAdapter = componentKeyToAdapter.putIfAbsent(key, componentAdapter)
        if (existingAdapter != null) {
          throw PluginException("Key $key duplicated; existingAdapter: $existingAdapter; " +
                                "descriptor=${getServiceImplementation(descriptor, this)}, " +
                                " app=$app, current plugin=${pluginDescriptor.pluginId}", pluginDescriptor.pluginId)
        }
      }
    }
  }

  internal fun initializeComponent(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId?) {
    if (serviceDescriptor == null || !isPreInitialized(component)) {
      if (LoadingState.CONFIGURATION_STORE_INITIALIZED.isOccurred) {
        componentStore.initComponent(component, serviceDescriptor, pluginId)
      }
      else {
        check(component !is PersistentStateComponent<*> || getApplication()!!.isUnitTestMode)
      }
    }
  }

  protected open fun isPreInitialized(component: Any): Boolean {
    return component is PathMacroManager || component is IComponentStore || component is MessageBusFactory
  }

  protected abstract fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor

  @Deprecated("Deprecated in interface")
  final override fun <T : Any> getComponent(key: Class<T>): T? {
    assertComponentsSupported()
    checkState()

    val adapter = getComponentAdapter(key)
    if (adapter == null) {
      checkCanceledIfNotInClassInit()
      if (containerState.get() == ContainerState.DISPOSE_COMPLETED) {
        throwAlreadyDisposedError(key.name, this)
      }
      return null
    }

    if (adapter is ServiceComponentAdapter) {
      LOG.error("$key it is a service, use getService instead of getComponent")
    }

    if (adapter is BaseComponentAdapter) {
      if (parent != null && adapter.componentManager !== this) {
        LOG.error("getComponent must be called on appropriate container (current: $this, expected: ${adapter.componentManager})")
      }

      if (containerState.get() == ContainerState.DISPOSE_COMPLETED) {
        adapter.throwAlreadyDisposedError(this)
      }
      return adapter.getInstance(adapter.componentManager, key)
    }
    else {
      return null
    }
  }

  @RequiresBlockingContext
  final override fun <T : Any> getService(serviceClass: Class<T>): T? {
    return doGetService(serviceClass, true) ?: return postGetService(serviceClass, createIfNeeded = true)
  }

  final override suspend fun <T : Any> getServiceAsync(keyClass: Class<T>): T {
    val result = getServiceAsyncIfDefined(keyClass)
    if (result == null && isLightServiceSupported && isLightService(keyClass)) {
      return getOrCreateLightServiceAdapter(keyClass).getInstanceAsync(componentManager = this, keyClass = keyClass)
    }
    return result ?: throw RuntimeException("service is not defined for $keyClass")
  }

  suspend fun <T : Any> getServiceAsyncIfDefined(keyClass: Class<T>): T? {
    val key = keyClass.name
    val adapter = componentKeyToAdapter.get(key) ?: return null
    check(adapter is BaseComponentAdapter) { "$adapter is not a service (key=$key)" }
    return adapter.getInstanceAsync(componentManager = this, keyClass = keyClass)
  }

  protected open fun <T : Any> postGetService(serviceClass: Class<T>, createIfNeeded: Boolean): T? = null

  final override fun <T : Any> getServiceIfCreated(serviceClass: Class<T>): T? {
    return doGetService(serviceClass, createIfNeeded = false) ?: postGetService(serviceClass, createIfNeeded = false)
  }

  protected open fun <T : Any> doGetService(serviceClass: Class<T>, createIfNeeded: Boolean): T? {
    val key = serviceClass.name
    val adapter = componentKeyToAdapter.get(key)
    if (adapter is BaseComponentAdapter) {
      if (createIfNeeded && containerState.get() == ContainerState.DISPOSE_COMPLETED) {
        throwAlreadyDisposedError(adapter.toString(), this)
      }
      return adapter.getInstance(componentManager = this, keyClass = serviceClass, createIfNeeded = createIfNeeded)
    }

    if (isLightServiceSupported && isLightService(serviceClass)) {
      if (createIfNeeded) {
        return getOrCreateLightServiceAdapter(serviceClass)
          .getInstance(componentManager = this, keyClass = serviceClass, createIfNeeded = true)!!
      }
      else {
        return null
      }
    }

    checkCanceledIfNotInClassInit()

    // if the container is fully disposed, all adapters may be removed
    if (containerState.get() == ContainerState.DISPOSE_COMPLETED) {
      if (!createIfNeeded) {
        return null
      }
      throwAlreadyDisposedError(serviceClass.name, this)
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

    @Suppress("DEPRECATION")
    val result = getComponent(serviceClass) ?: return null
    PluginException.logPluginError(LOG,
      "$key requested as a service, but it is a component - " +
      "convert it to a service or change call to " +
      if (parent == null) "ApplicationManager.getApplication().getComponent()" else "project.getComponent()",
      null, serviceClass)
    return result
  }

  private fun <T : Any> getOrCreateLightServiceAdapter(serviceClass: Class<T>): BaseComponentAdapter {
    val adapter = componentKeyToAdapter.computeIfAbsent(serviceClass.name) {
      val classLoader = serviceClass.classLoader
      LightServiceComponentAdapter(
        serviceClass = serviceClass,
        pluginDescriptor = if (classLoader is PluginAwareClassLoader) classLoader.pluginDescriptor else fakeCorePluginDescriptor,
        componentManager = this,
      )
    } as BaseComponentAdapter
    return adapter
  }

  @Synchronized
  private fun getOrCreateMessageBusUnderLock(): MessageBus {
    var messageBus = this.messageBus
    if (messageBus != null) {
      return messageBus
    }

    @Suppress("RetrievingService")
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

    val adapter = MyComponentAdapter(key, implementation.name, pluginDescriptor, this, CompletableDeferred(), implementation)
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
  fun registerService(
    serviceInterface: Class<*>,
    implementation: Class<*>,
    pluginDescriptor: PluginDescriptor,
    override: Boolean,
  ) {
    checkState()

    val descriptor = ServiceDescriptor(serviceInterface.name, implementation.name, null, null, false,
                                       null, PreloadMode.FALSE, null, null)
    val adapter = ServiceComponentAdapter(descriptor, pluginDescriptor, this, implementation)
    if (override) {
      overrideAdapter(adapter, pluginDescriptor)
    }
    else {
      registerAdapter(adapter, pluginDescriptor)
    }
  }

  /**
   * Use only if approved by core team.
   */
  fun <T : Any> registerServiceInstance(serviceInterface: Class<T>, instance: T, pluginDescriptor: PluginDescriptor) {
    val serviceKey = serviceInterface.name
    checkState()

    val descriptor = ServiceDescriptor(serviceKey, instance.javaClass.name, null, null, false,
                                       null, PreloadMode.FALSE, null, null)
    componentKeyToAdapter.put(serviceKey, ServiceComponentAdapter(descriptor = descriptor,
                                                                  pluginDescriptor = pluginDescriptor,
                                                                  componentManager = this,
                                                                  implementationClass = instance.javaClass,
                                                                  deferred = CompletableDeferred(value = instance)))
  }

  @Suppress("DuplicatedCode")
  @TestOnly
  fun <T : Any> replaceServiceInstance(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable) {
    checkState()
    if (isLightService(serviceInterface)) {
      val classLoader = serviceInterface.classLoader
      val adapter = LightServiceComponentAdapter(
        serviceClass = serviceInterface,
        pluginDescriptor = if (classLoader is PluginAwareClassLoader) classLoader.pluginDescriptor else fakeCorePluginDescriptor,
        componentManager = this,
        deferred = CompletableDeferred(value = instance)
      )
      val key = adapter.componentKey
      componentKeyToAdapter.put(key, adapter)
      Disposer.register(parentDisposable) {
        componentKeyToAdapter.remove(key)
      }
    }
    else {
      val key = serviceInterface.name
      val oldAdapter = componentKeyToAdapter.get(key) as ServiceComponentAdapter
      val newAdapter = ServiceComponentAdapter(descriptor = oldAdapter.descriptor,
                                               pluginDescriptor = oldAdapter.pluginDescriptor,
                                               componentManager = this,
                                               implementationClass = oldAdapter.getImplementationClass(),
                                               deferred = CompletableDeferred(value = instance))
      componentKeyToAdapter.put(key, newAdapter)
      @Suppress("DuplicatedCode")
      Disposer.register(parentDisposable) {
        @Suppress("DEPRECATION")
        if (instance is Disposable && !Disposer.isDisposed(instance)) {
          Disposer.dispose(instance)
        }
        componentKeyToAdapter.put(key, oldAdapter)
      }
    }
  }

  @TestOnly
  fun unregisterService(serviceInterface: Class<*>) {
    val key = serviceInterface.name
    when (val adapter = componentKeyToAdapter.remove(key)) {
      null -> error("Trying to unregister $key service which is not registered")
      !is ServiceComponentAdapter -> error("$key service should be registered as a service, but was ${adapter::class.java}")
    }
  }

  @Suppress("DuplicatedCode")
  fun <T : Any> replaceRegularServiceInstance(serviceInterface: Class<T>, instance: T) {
    checkState()

    val key = serviceInterface.name
    val oldAdapter = componentKeyToAdapter.get(key) as ServiceComponentAdapter
    val newAdapter = ServiceComponentAdapter(descriptor = oldAdapter.descriptor,
                                             pluginDescriptor = oldAdapter.pluginDescriptor,
                                             componentManager = this,
                                             implementationClass = oldAdapter.getImplementationClass(),
                                             deferred = CompletableDeferred(value = instance))
    componentKeyToAdapter.put(key, newAdapter)

    (oldAdapter.getInitializedInstance() as? Disposable)?.let(Disposer::dispose)
  }

  final override fun <T : Any> loadClass(className: String, pluginDescriptor: PluginDescriptor): Class<T> {
    @Suppress("UNCHECKED_CAST")
    return doLoadClass(className, pluginDescriptor) as Class<T>
  }

  final override fun <T : Any> instantiateClass(aClass: Class<T>, pluginId: PluginId): T {
    checkCanceledIfNotInClassInit()

    return resetThreadContext().use {
      doInstantiateClass(aClass, pluginId)
    }
  }

  protected open fun <T : Any> findConstructorAndInstantiateClass(lookup: MethodHandles.Lookup, aClass: Class<T>): T {
    @Suppress("UNCHECKED_CAST")
    return (lookup.findConstructorOrNull(aClass, emptyConstructorMethodType)?.invoke()
            ?: lookup.findConstructorOrNull(aClass, coroutineScopeMethodType)?.invoke(instanceCoroutineScope(aClass))
            ?: lookup.findConstructorOrNull(aClass, applicationMethodType)?.invoke(this)
            ?: throw RuntimeException("Cannot find suitable constructor, " +
                                      "expected (), (CoroutineScope), (Application), or (Application, CoroutineScope)")) as T
  }

  private fun <T : Any> doInstantiateClass(aClass: Class<T>, pluginId: PluginId): T {
    try {
      return findConstructorAndInstantiateClass(MethodHandles.privateLookupIn(aClass, methodLookup), aClass)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      if (e is ControlFlowException || e is PluginException) {
        throw e
      }
      throw PluginException("Cannot create class ${aClass.name} (classloader=${aClass.classLoader})", e, pluginId)
    }
  }

  final override fun <T : Any> instantiateClassWithConstructorInjection(aClass: Class<T>, key: Any, pluginId: PluginId): T {
    return resetThreadContext().use {
      instantiateUsingPicoContainer(aClass = aClass, requestorKey = key, pluginId = pluginId, componentManager = this)
    }
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

    /**
     * FIXME: possible race with concurrent service construction:
     *  1. com.intellij.serviceContainer.BaseComponentAdapter.getInstance @ checkContainerIsActive
     *  2. com.intellij.ide.plugins.DynamicPlugins.unloadPluginWithoutProgress @ forbidGettingServices & unload
     *  3. com.intellij.serviceContainer.BaseComponentAdapter.getInstance @ deferred.join
     */

    if (!services.isEmpty()) {
      val store = componentStore
      for (service in services) {
        val serviceInterface = getServiceInterface(service, this)
        val adapter = (componentKeyToAdapter.remove(serviceInterface) ?: continue) as BaseComponentAdapter
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
        if (adapter.pluginId == pluginId) {
          adapter.getInitializedInstance()?.let { instance ->
            if (instance is Disposable) {
              Disposer.dispose(instance)
            }
            store.unloadComponent(instance)
          }
          iterator.remove()
        }
      }
    }
  }

  open fun activityNamePrefix(): String? = null

  fun preloadServices(modules: List<IdeaPluginDescriptorImpl>,
                      activityPrefix: String,
                      syncScope: CoroutineScope,
                      onlyIfAwait: Boolean = false,
                      asyncScope: CoroutineScope) {
    // we want to group async preloaded services (parent trace), but `CoroutineScope()` requires explicit completion,
    // so, we collect all services and then use launch
    val asyncServices = mutableListOf<ServiceDescriptor>()
    for (plugin in modules) {
      for (service in getContainerDescriptor(plugin).services) {
        if (!isServiceSuitable(service) || (service.os != null && !isSuitableForOs(service.os))) {
          continue
        }

        val scope: CoroutineScope = when (service.preload) {
          PreloadMode.TRUE -> if (onlyIfAwait) null else asyncScope
          PreloadMode.NOT_HEADLESS -> if (onlyIfAwait || getApplication()!!.isHeadlessEnvironment) null else asyncScope
          PreloadMode.NOT_LIGHT_EDIT -> if (onlyIfAwait || isLightEdit()) null else asyncScope
          PreloadMode.AWAIT -> syncScope
          PreloadMode.FALSE -> null
          else -> throw IllegalStateException("Unknown preload mode ${service.preload}")
        } ?: continue

        if (isServicePreloadingCancelled) {
          return
        }

        if (plugin.pluginId != PluginManagerCore.CORE_ID) {
          val impl = getServiceImplementation(service, this)
          if (!servicePreloadingAllowListForNonCorePlugin.contains(impl)) {
            val message = "`preload=true` must be used only for core services (service=$impl, plugin=${plugin.pluginId})"
            if (service.preload == PreloadMode.AWAIT) {
              LOG.error(PluginException(message, plugin.pluginId))
            }
            else {
              LOG.warn(message)
            }
          }
        }

        if (scope === asyncScope) {
          asyncServices.add(service)
        }
        else {
          val serviceInterface = getServiceInterface(service, this)
          scope.launch(CoroutineName("$serviceInterface preloading")) {
            preloadService(service, serviceInterface)
          }
        }
      }
    }

    if (asyncServices.isNotEmpty()) {
      asyncScope.launch(CoroutineName("${activityPrefix}service preloading (async)")) {
        for (service in asyncServices) {
          val serviceInterface = getServiceInterface(service, this@ComponentManagerImpl)
          launch(CoroutineName("$serviceInterface preloading")) a@{
            if (isServicePreloadingCancelled || isDisposed) {
              return@a
            }

            try {
              preloadService(service, serviceInterface)
            }
            catch (e: CancellationException) {
              throw e
            }
            catch (e: Throwable) {
              if (!isServicePreloadingCancelled && !isDisposed) {
                val adapter = componentKeyToAdapter.get(serviceInterface) as ServiceComponentAdapter?
                LOG.error(PluginException(e, adapter?.pluginId))
              }
            }
          }
        }
      }
    }

    postPreloadServices(modules = modules, activityPrefix = activityPrefix, syncScope = syncScope, onlyIfAwait = onlyIfAwait)
  }

  protected open fun postPreloadServices(modules: List<IdeaPluginDescriptorImpl>,
                                         activityPrefix: String,
                                         syncScope: CoroutineScope,
                                         onlyIfAwait: Boolean) {
  }

  protected open suspend fun preloadService(service: ServiceDescriptor, serviceInterface: String) {
    val adapter = componentKeyToAdapter.get(serviceInterface) as ServiceComponentAdapter? ?: return
    adapter.getInstanceAsync<Any>(componentManager = this, keyClass = null)
  }

  override fun isDisposed(): Boolean {
    return containerState.get() >= ContainerState.DISPOSE_IN_PROGRESS
  }

  final override fun beforeTreeDispose() {
    stopServicePreloading()

    ThreadingAssertions.assertWriteIntentReadAccess()

    if (!(containerState.compareAndSet(ContainerState.COMPONENT_CREATED, ContainerState.DISPOSE_IN_PROGRESS) ||
          containerState.compareAndSet(ContainerState.PRE_INIT, ContainerState.DISPOSE_IN_PROGRESS))) {
      // disposed in a recommended way using ProjectManager
      return
    }

    // disposed directly using Disposer.dispose()
    // we don't care that state DISPOSE_IN_PROGRESS is already set, and exceptions because of that are possible -
    // use `ProjectManager` to close and dispose the project
    startDispose()
  }

  fun startDispose() {
    stopServicePreloading()

    val messageBus = messageBus
    runSuppressing(
      { Disposer.disposeChildren(this) { true } },
      // There is a chance that someone will try to connect to the message bus and will get NPE because of disposed connection disposable,
      // because the container state is not yet set to DISPOSE_IN_PROGRESS.
      // So, 1) dispose connection children 2) set state DISPOSE_IN_PROGRESS 3) dispose connection
      { messageBus?.disposeConnectionChildren() },
      { containerState.set(ContainerState.DISPOSE_IN_PROGRESS) },
      { messageBus?.disposeConnection() }
    )
  }

  override fun dispose() {
    if (!containerState.compareAndSet(ContainerState.DISPOSE_IN_PROGRESS, ContainerState.DISPOSED)) {
      throw IllegalStateException("Expected current state is DISPOSE_IN_PROGRESS, but actual state is ${containerState.get()} ($this)")
    }

    coroutineScope.cancel("ComponentManagerImpl.dispose is called")

    // dispose components and services
    Disposer.dispose(serviceParentDisposable)

    // release references to the service instances
    componentKeyToAdapter.clear()
    componentAdapters.clear()

    messageBus?.let {
      // Must be after disposing `serviceParentDisposable`, because message bus disposes child buses, so we must dispose all services first.
      // For example, service ModuleManagerImpl disposes modules; each module, in turn, disposes module's message bus (child bus of application).
      Disposer.dispose(it)
      this.messageBus = null
    }

    if (!containerState.compareAndSet(ContainerState.DISPOSED, ContainerState.DISPOSE_COMPLETED)) {
      throw IllegalStateException("Expected current state is DISPOSED, but actual state is ${containerState.get()} ($this)")
    }
  }

  open fun stopServicePreloading() {
    isServicePreloadingCancelled = true
  }

  @Deprecated("Deprecated in Java")
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

  fun <T : Any> getServiceByClassName(serviceClassName: String): T? {
    checkState()
    val adapter = componentKeyToAdapter.get(serviceClassName) as ServiceComponentAdapter?
    return adapter?.getInstance(this, keyClass = null)
  }

  fun getServiceImplementation(key: Class<*>): Class<*>? {
    checkState()
    return (componentKeyToAdapter.get(key.name) as? ServiceComponentAdapter?)?.componentImplementation
  }

  open fun isServiceSuitable(descriptor: ServiceDescriptor): Boolean = descriptor.client == null

  protected open fun isComponentSuitable(componentConfig: ComponentConfig): Boolean {
    val options = componentConfig.options ?: return true
    return !java.lang.Boolean.parseBoolean(options.get("internal")) || ApplicationManager.getApplication().isInternal
  }

  final override fun getDisposed(): Condition<*> = Condition<Any?> { isDisposed }

  fun instances(createIfNeeded: Boolean = false, filter: ((implClass: Class<*>) -> Boolean)? = null): Sequence<Any> {
    return componentKeyToAdapter.values.asSequence().mapNotNull { adapter ->
      if (adapter is BaseComponentAdapter) {
        if (filter == null || (filter(getImplClassSafe(adapter) ?: return@mapNotNull null))) {
          adapter.getInstance<Any>(this, null, createIfNeeded = createIfNeeded)
        }
        else {
          null
        }
      }
      else {
        null
      }
    }
  }

  fun processAllImplementationClasses(processor: (componentClass: Class<*>, plugin: PluginDescriptor?) -> Unit) {
    for (adapter in componentKeyToAdapter.values) {
      if (adapter is ServiceComponentAdapter) {
        processor(getImplClassSafe(adapter) ?: continue, adapter.pluginDescriptor)
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

  private fun getImplClassSafe(adapter: BaseComponentAdapter): Class<*>? {
    try {
      return adapter.getImplementationClass()
    }
    catch (e: PluginException) {
      // well, the component is registered, but the required jar is not added to the classpath (community edition or junior IDE)
      if (e.cause is ClassNotFoundException) {
        LOG.warn(e.message)
      }
      else {
        LOG.warn(e)
      }
      return null
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Throwable) {
      LOG.warn(e)
      return null
    }
  }

  internal fun getComponentAdapter(keyClass: Class<*>): ComponentAdapter? {
    assertComponentsSupported()
    val adapter = componentKeyToAdapter.get(keyClass) ?: componentKeyToAdapter.get(keyClass.name)
    return if (adapter == null && parent != null) parent.getComponentAdapter(keyClass) else adapter
  }

  fun unregisterComponent(componentKey: Class<*>): ComponentAdapter? {
    assertComponentsSupported()

    val adapter = componentKeyToAdapter.remove(componentKey) ?: return null
    componentAdapters.remove(adapter as MyComponentAdapter)
    return adapter
  }

  @TestOnly
  fun registerComponentInstance(key: Class<*>, instance: Any) {
    check(getApplication()!!.isUnitTestMode)
    assertComponentsSupported()

    val implClass = instance::class.java
    val newAdapter = MyComponentAdapter(componentKey = key,
                                        implementationClassName = implClass.name,
                                        pluginDescriptor = fakeCorePluginDescriptor,
                                        componentManager = this,
                                        deferred = CompletableDeferred(value = instance),
                                        implementationClass = implClass)

    if (componentKeyToAdapter.putIfAbsent(newAdapter.componentKey, newAdapter) != null) {
      throw IllegalStateException("Key ${newAdapter.componentKey} duplicated")
    }
    componentAdapters.add(newAdapter)
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

  fun <T : Any> processInitializedComponents(aClass: Class<T>, processor: (T) -> Unit) {
    // we must use instances only from our adapter (could be service or something else).
    for (adapter in componentAdapters.getImmutableSet()) {
      val component = adapter.getInitializedInstance()
      if (component != null && aClass.isAssignableFrom(component.javaClass)) {
        @Suppress("UNCHECKED_CAST")
        processor(component as T)
      }
    }
  }

  fun <T : Any> collectInitializedComponents(aClass: Class<T>): List<T> {
    // we must use instances only from our adapter (could be service or something else).
    val result = mutableListOf<T>()
    for (adapter in componentAdapters.getImmutableSet()) {
      val component = adapter.getInitializedInstance()
      if (component != null && aClass.isAssignableFrom(component.javaClass)) {
        @Suppress("UNCHECKED_CAST")
        result.add(component as T)
      }
    }
    return result
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

  /**
   * Key: plugin coroutine scope.
   * Value: intersection of this container scope and plugin coroutine scope.
   */
  private val pluginScopes = AtomicReference<PersistentMap<CoroutineScope, CoroutineScope>>(persistentHashMapOf())

  fun instanceCoroutineScope(pluginClass: Class<*>): CoroutineScope {
    val pluginClassloader = pluginClass.classLoader
    val intersectionScope = if (pluginClassloader is PluginAwareClassLoader) {
      val pluginScope = pluginClassloader.pluginCoroutineScope
      val parentScope = parent?.intersectionCoroutineScope(pluginScope) // for consistency
                        ?: pluginScope
      intersectionCoroutineScope(parentScope)
    }
    else {
      // non-unloadable
      getCoroutineScope()
    }
    // The parent scope should become canceled only when the container is disposed, or the plugin is unloaded.
    // Leaking the parent scope might lead to premature cancellation.
    // Fool proofing: a fresh child scope is created per instance to avoid leaking the parent to clients.
    return intersectionScope.namedChildScope(pluginClass.name)
  }

  // to run post-start-up activities - to not create scope for each class and do not keep it alive
  fun pluginCoroutineScope(pluginClassloader: ClassLoader): CoroutineScope {
    val intersectionScope = if (pluginClassloader is PluginAwareClassLoader) {
      val pluginScope = pluginClassloader.pluginCoroutineScope
      val parentScope = parent?.intersectionCoroutineScope(pluginScope) // for consistency
                        ?: pluginScope
      intersectionCoroutineScope(parentScope)
    }
    else {
      // non-unloadable
      getCoroutineScope()
    }
    return intersectionScope
  }

  private fun intersectionCoroutineScope(pluginScope: CoroutineScope): CoroutineScope {
    var scopes = pluginScopes.get()
    scopes.get(pluginScope)?.let {
      return it
    }

    val containerScope = getCoroutineScope()
    val intersectionName = "(${debugString(short = true)} x ${pluginScope.coroutineContext[CoroutineName]?.name})"
    val intersectionScope = containerScope.namedChildScope(intersectionName).also {
      it.attachAsChildTo(pluginScope)
    }
    while (true) {
      val newScopes = scopes.put(pluginScope, intersectionScope)
      val witness = pluginScopes.compareAndExchange(scopes, newScopes)
      if (witness === scopes) {
        intersectionScope.coroutineContext.job.invokeOnCompletion {
          removePluginScope(pluginScope)
        }
        // published successfully
        return intersectionScope
      }
      witness.get(pluginScope)?.let {
        // another thread published the scope for given plugin
        // => uses the value from another thread, and cancels the unpublished scope
        intersectionScope.cancel()
        return it
      }
      // try to publish again
      scopes = witness
    }
  }

  private fun removePluginScope(pluginScope: CoroutineScope) {
    pluginScopes.updateAndGet { scopes ->
      scopes.remove(pluginScope)
    }
  }
}

/**
 * A copy-on-write linked hash set.
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

  fun remove(element: T) {
    synchronized(lock) { copySyncSetIfExposedAsImmutable().remove(element) }
  }

  fun replace(old: T, new: T) {
    synchronized(lock) {
      val set = copySyncSetIfExposedAsImmutable()
      set.remove(old)
      set.add(new)
    }
  }

  private fun copySyncSetIfExposedAsImmutable(): LinkedHashSet<T> {
    if (immutableSet != null) {
      immutableSet = null
      synchronizedSet = LinkedHashSet(synchronizedSet)
    }
    return synchronizedSet
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

private inline fun executeRegisterTask(mainPluginDescriptor: IdeaPluginDescriptorImpl,
                                       crossinline task: (IdeaPluginDescriptorImpl) -> Unit) {
  task(mainPluginDescriptor)
  executeRegisterTaskForOldContent(mainPluginDescriptor, task)
}

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "SpellCheckingInspection")
private val servicePreloadingAllowListForNonCorePlugin = java.util.Set.of(
  "com.android.tools.adtui.webp.WebpMetadata\$WebpMetadataRegistrar",
  "com.intellij.completion.ml.experiment.ClientExperimentStatus",
  "com.intellij.compiler.server.BuildManager",
  "com.intellij.openapi.module.WebModuleTypeRegistrar",
  "com.intellij.tasks.config.PasswordConversionEnforcer",
  "com.intellij.ide.RecentProjectsManagerBase",
  "org.jetbrains.android.AndroidPlugin",
  "com.intellij.remoteDev.tests.impl.DistributedTestHost",
  "com.intellij.configurationScript.inspection.ExternallyConfigurableProjectInspectionProfileManager",
  // use lazy listener
  "com.intellij.packaging.impl.artifacts.workspacemodel.ArtifactManagerBridge",
  "com.intellij.compiler.CompilerConfigurationImpl",
  "com.intellij.compiler.backwardRefs.CompilerReferenceServiceImpl",
  "org.jetbrains.kotlin.idea.search.refIndex.KotlinCompilerReferenceIndexService",
  "org.jetbrains.idea.maven.project.MavenProjectsManagerEx",
  // use lazy listener
  "org.jetbrains.idea.maven.navigator.MavenProjectsNavigator",
  "org.jetbrains.idea.maven.tasks.MavenShortcutsManager",
  "com.jetbrains.rd.platform.codeWithMe.toolbar.CodeWithMeToolbarUpdater",
  "com.jetbrains.rdserver.portForwarding.cwm.CodeWithMeBackendPortForwardingToolWindowManager",
  "com.jetbrains.rdserver.followMe.FollowMeManagerService",
  "com.jetbrains.rdserver.diagnostics.BackendPerformanceHost",
  "com.jetbrains.rdserver.followMe.BackendUserManager",
  "com.jetbrains.rdserver.followMe.BackendUserFocusManager",
  "com.jetbrains.rdserver.projectView.BackendProjectViewSync",
  "com.jetbrains.rdserver.editors.BackendFollowMeEditorsHost",
  "com.jetbrains.rdserver.debugger.BackendFollowMeDebuggerHost",
  "com.jetbrains.rdserver.editors.BackendEditorService",
  "com.jetbrains.rdserver.toolWindow.BackendServerToolWindowManager",
  "com.jetbrains.rdserver.toolbar.CWMHostClosedToolbarNotification",
  "com.jetbrains.rdclient.client.FrontendProjectSessionsManager",
  "com.jetbrains.rider.projectView.workspace.impl.RiderWorkspaceModel",
)