// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "LeakingThis", "ReplaceJavaStaticMethodWithKotlinAnalog")
@file:Internal
@file:OptIn(IntellijInternalApi::class)

package com.intellij.serviceContainer

import com.intellij.codeWithMe.ClientIdContextElement
import com.intellij.codeWithMe.ClientIdContextElementPrecursor
import com.intellij.concurrency.currentTemporaryThreadContextOrNull
import com.intellij.concurrency.resetThreadContext
import com.intellij.concurrency.withThreadLocal
import com.intellij.configurationStore.ProjectIdManager
import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.diagnostic.ActivityCategory
import com.intellij.diagnostic.LoadingState
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.ide.plugins.*
import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.idea.AppMode.isLightEdit
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.components.*
import com.intellij.openapi.components.ServiceDescriptor.PreloadMode
import com.intellij.openapi.components.impl.stores.ComponentStoreOwner
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.ControlFlowException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.extensions.*
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.extensions.impl.createExtensionPoints
import com.intellij.openapi.module.Module
import com.intellij.openapi.progress.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.*
import com.intellij.platform.instanceContainer.internal.*
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.UList
import com.intellij.util.messages.MessageBus
import com.intellij.util.messages.MessageBusFactory
import com.intellij.util.messages.MessageBusOwner
import com.intellij.util.messages.Topic
import com.intellij.util.messages.impl.*
import com.intellij.util.runSuppressing
import kotlinx.coroutines.*
import kotlinx.coroutines.internal.intellij.IntellijCoroutines
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.picocontainer.ComponentAdapter
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.streams.asSequence

internal val LOG by lazy(LazyThreadSafetyMode.PUBLICATION) {
  logger<ComponentManagerImpl>()
}

private val methodLookup = MethodHandles.lookup()

@JvmField
@Internal
val emptyConstructorMethodType: MethodType = MethodType.methodType(Void.TYPE)

@JvmField
@Internal
val coroutineScopeMethodType: MethodType = MethodType.methodType(Void.TYPE, CoroutineScope::class.java)

private val applicationMethodType = MethodType.methodType(Void.TYPE, Application::class.java)
private val applicationAndScopeMethodType = MethodType.methodType(Void.TYPE, Application::class.java, CoroutineScope::class.java)
private val componentManagerMethodType = MethodType.methodType(Void.TYPE, ComponentManager::class.java)

@Internal
fun MethodHandles.Lookup.findConstructorOrNull(clazz: Class<*>, type: MethodType): MethodHandle? {
  return try {
    findConstructor(clazz, type)
  }
  catch (_: NoSuchMethodException) {
    return null
  }
  catch (_: IllegalAccessException) {
    return null
  }
}

@Internal
abstract class ComponentManagerImpl(
  internal val parent: ComponentManagerImpl?,
  parentScope: CoroutineScope,
  additionalContext: CoroutineContext,
) : ComponentManager, Disposable.Parent, MessageBusOwner, UserDataHolderBase(), ComponentManagerEx, ComponentStoreOwner {
  protected enum class ContainerState {
    PRE_INIT, COMPONENT_CREATED, DISPOSE_IN_PROGRESS, DISPOSED, DISPOSE_COMPLETED
  }

  protected constructor(parentScope: CoroutineScope) : this(
    parent = null,
    parentScope,
    additionalContext = EmptyCoroutineContext,
  )

  protected constructor(parent: ComponentManagerImpl) : this(
    parent,
    parentScope = parent.getCoroutineScope(),
    additionalContext = EmptyCoroutineContext,
  )

  companion object {
    @Internal
    @JvmField
    val fakeCorePluginDescriptor: DefaultPluginDescriptor = DefaultPluginDescriptor(PluginManagerCore.CORE_ID, null)

    @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
    @Internal
    @JvmField
    val badWorkspaceComponents: Set<String> = java.util.Set.of(
      "jetbrains.buildServer.codeInspection.InspectionPassRegistrar",
      "jetbrains.buildServer.testStatus.TestStatusPassRegistrar",
      "jetbrains.buildServer.customBuild.lang.gutterActions.CustomBuildParametersGutterActionsHighlightingPassRegistrar",
    )

    // not as a file level function to avoid scope cluttering
    @Internal
    suspend fun createAllServices2(
      componentManager: ComponentManagerImpl,
      requireEdt: Set<String>,
      requireReadAction: Set<String>,
    ) {
      // componentManager.serviceContainer.preloadAllInstances()
      val holders = componentManager.serviceContainer.instanceHolders()
      for (holder in holders) {
        try {
          when (val instanceClassName = holder.instanceClassName()) {
            in requireEdt -> withContext(Dispatchers.EDT) {
              holder.getInstanceInCallerContext(keyClass = null)
            }
            in requireReadAction -> readActionBlocking {
              holder.getOrCreateInstanceBlocking(debugString = instanceClassName, keyClass = null)
            }
            else -> holder.getInstanceInCallerContext(keyClass = null)
          }
        }
        catch (@Suppress("IncorrectCancellationExceptionHandling") ce: CancellationException) {
          currentCoroutineContext().ensureActive()
          @Suppress("IncorrectCancellationExceptionHandling")
          LOG.error("Cannot create $holder", ce)
        }
        catch (t: Throwable) {
          LOG.error("Cannot create $holder", t)
        }
      }
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private val scopeHolder = ScopeHolder(
    parentScope = parentScope,
    additionalContext = (additionalContext + this.asContextElement()).let { context ->
      val clientIdContextElement = context[ClientIdContextElement.Key]
      return@let if (clientIdContextElement == null) context + ClientIdContextElementPrecursor
      else context
    },
    containerName = debugString(short = true),
  )

  open val supportedSignaturesOfLightServiceConstructors: List<MethodType> = java.util.List.of(
    emptyConstructorMethodType,
    coroutineScopeMethodType,
    applicationMethodType,
    applicationAndScopeMethodType,
    componentManagerMethodType,
  )

  @Suppress("LeakingThis")
  private val serviceContainer = InstanceContainerImpl(
    scopeHolder = scopeHolder,
    containerName = "${debugString(true)} services",
    dynamicInstanceSupport = if (isLightServiceSupported) LightServiceInstanceSupport(
      componentManager = this,
      onDynamicInstanceRegistration = ::registerDynamicInstanceForUnloading
    )
    else null,
    ordered = false,
  )

  private val componentContainer = InstanceContainerImpl(
    scopeHolder = scopeHolder,
    containerName = "${debugString(true)} components",
    dynamicInstanceSupport = null,
    ordered = true,
  )

  private val pluginServicesStore = PluginServicesStore()

  private fun registerDynamicInstanceForUnloading(instanceHolder: InstanceHolder) {
    val pluginDescriptor = (instanceHolder.instanceClass().classLoader as? PluginAwareClassLoader)?.pluginDescriptor
    if (pluginDescriptor is IdeaPluginDescriptor) {
      pluginServicesStore.addDynamicService(pluginDescriptor, instanceHolder)
    }
  }

  @Suppress("LeakingThis")
  @JvmField
  internal val dependencyResolver = ComponentManagerResolver(this)

  @JvmField
  protected val containerState: AtomicReference<ContainerState> = AtomicReference(ContainerState.PRE_INIT)

  protected val containerStateName: String
    get() = containerState.get().name

  private val extensionArea = ExtensionsAreaImpl(this)

  private var messageBus: MessageBusImpl? = null

  @Volatile
  private var isServicePreloadingCancelled = false

  override fun debugString(): String = debugString(short = true)

  protected open fun debugString(short: Boolean = false): String {
    return "${if (short) javaClass.simpleName else javaClass.name}@${System.identityHashCode(this)}"
  }

  @JvmField
  internal val serviceParentDisposable: Disposable = Disposer.newDisposable("services of ${debugString()}")

  protected open val isLightServiceSupported: Boolean
    get() = parent?.parent == null

  protected open val isMessageBusSupported: Boolean = parent?.parent == null
  protected open val isComponentSupported: Boolean = true

  // FIXME this is effectively no-op right now
  @Volatile
  @JvmField
  internal var componentContainerIsReadonly: String? = null

  @Suppress("UsagesOfObsoleteApi")
  final override fun getCoroutineScope(): CoroutineScope {
    if (parent?.parent == null) {
      return scopeHolder.containerScope
    }
    else {
      throw RuntimeException("Module doesn't have coroutineScope")
    }
  }

  override val componentStore: IComponentStore
    get() {
      return getService(IComponentStore::class.java) ?: error("Cannot get service: ${IComponentStore::class.java.name}")
    }

  internal fun getComponentInstance(componentKey: Any): Any? {
    assertComponentsSupported()
    val holder = ignoreDisposal {
      when (componentKey) {
        is String -> serviceContainer.getInstanceHolder(keyClassName = componentKey)
        is Class<*> -> componentContainer.getInstanceHolder(keyClass = componentKey)
                       ?: serviceContainer.getInstanceHolder(keyClass = componentKey)
        else -> null
      }
    }
    holder ?: return parent?.getComponentInstance(componentKey)
    return holder.getOrCreateInstanceBlocking(componentKey.toString(), keyClass = null)
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
    if (!isMessageBusSupported) {
      LOG.error("Do not use module level message bus")
    }
    if (messageBus == null) {
      return getOrCreateMessageBusUnderLock()
    }
    return messageBus
  }

  final override fun getExtensionArea(): ExtensionsAreaImpl = extensionArea

  fun registerComponents() {
    registerComponents(modules = PluginManagerCore.getPluginSet().getEnabledModules(), app = getApplication())
  }

  open fun registerComponents(modules: List<IdeaPluginDescriptorImpl>,
                              app: Application?,
                              precomputedExtensionModel: PrecomputedExtensionModel? = null,
                              listenerCallbacks: MutableList<in Runnable>? = null) {
    val activityNamePrefix = activityNamePrefix()

    var listenersByTopicName: ConcurrentMap<String, MutableList<PluginListenerDescriptor>>? = null
    val isHeadless = app == null || app.isHeadlessEnvironment
    val isUnitTestMode = app?.isUnitTestMode ?: false

    var activity = activityNamePrefix?.let { StartUpMeasurer.startActivity("${it}service and ep registration") }

    // register services before registering extensions because plugins can access services in their extensions,
    // which can be invoked right away if the plugin is loaded dynamically
    val extensionPoints = if (precomputedExtensionModel == null) HashMap(extensionArea.nameToPointMap) else null
    for (rootModule in modules) {
      executeRegisterTask(rootModule) { module ->
        val containerDescriptor = getContainerDescriptor(module)
        registerServices(containerDescriptor.services, module)
        registerComponents(pluginDescriptor = module, containerDescriptor = containerDescriptor, headless = isHeadless)

        if (listenersByTopicName == null) {
          listenersByTopicName = ConcurrentHashMap()
        }
        for (listener in containerDescriptor.listeners) {
          if ((isUnitTestMode && !listener.activeInTestMode) || (isHeadless && !listener.activeInHeadlessMode)) {
            continue
          }
          if (listener.os != null && !listener.os!!.isSuitableForOs()) {
            continue
          }
          listenersByTopicName.computeIfAbsent(listener.topicClassName) { ArrayList() }
            .add(PluginListenerDescriptor(listener, module))
        }

        if (extensionPoints != null && containerDescriptor.extensionPoints.isNotEmpty()) {
          createExtensionPoints(points = containerDescriptor.extensionPoints,
                                componentManager = this,
                                result = extensionPoints,
                                pluginDescriptor = module)
        }
      }
    }

    if (activity != null) {
      activity = activity.endAndStart("${activityNamePrefix}extension registration")
    }

    if (precomputedExtensionModel == null) {
      extensionArea.reset(extensionPoints!!)

      for (rootModule in modules) {
        executeRegisterTask(rootModule) { module ->
          module.registerExtensions(nameToPoint = extensionPoints,
                                    listenerCallbacks = listenerCallbacks)
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
      listenersByTopicName?.let {
        (messageBus as MessageBusEx).setLazyListeners(it)
      }
    }
  }

  private fun registerExtensionPointsAndExtensionByPrecomputedModel(precomputedExtensionModel: PrecomputedExtensionModel,
                                                                    listenerCallbacks: MutableList<in Runnable>?) {
    val extensionArea = extensionArea
    if (precomputedExtensionModel.extensionPoints.isEmpty()) {
      return
    }

    val result = HashMap<String, ExtensionPointImpl<*>>()
    for ((pluginDescriptor, points) in precomputedExtensionModel.extensionPoints) {
      createExtensionPoints(points = points, componentManager = this, result = result, pluginDescriptor = pluginDescriptor)
    }

    assert(extensionArea.nameToPointMap.isEmpty())
    extensionArea.reset(result)

    for ((name, item) in precomputedExtensionModel.nameToExtensions) {
      val point = result.get(name) ?: continue
      for ((pluginDescriptor, extensions) in item) {
        point.registerExtensions(descriptors = extensions, pluginDescriptor = pluginDescriptor, listenerCallbacks = listenerCallbacks)
      }
    }
  }

  private fun registerComponents(pluginDescriptor: IdeaPluginDescriptor, containerDescriptor: ContainerDescriptor, headless: Boolean) {
    try {
      registerComponents2Inner(pluginDescriptor, containerDescriptor, headless)
    }
    catch (pce: CancellationException) {
      ProgressManager.checkCanceled()
      throw PluginException(pce, pluginDescriptor.pluginId)
    }
    catch (t: Throwable) {
      throw PluginException(t, pluginDescriptor.pluginId)
    }
  }

  private fun registerComponents2Inner(pluginDescriptor: IdeaPluginDescriptor,
                                       containerDescriptor: ContainerDescriptor,
                                       headless: Boolean) {
    val components = containerDescriptor.components
    if (components.isEmpty()) {
      return
    }

    val pluginClassLoader = pluginDescriptor.pluginClassLoader
    val registrationScope = if (pluginClassLoader is PluginAwareClassLoader) pluginClassLoader.pluginCoroutineScope else null
    val registrar = componentContainer.startRegistration(registrationScope)
    for (descriptor in components) {
      if (descriptor.os != null && !descriptor.os.isSuitableForOs()) {
        continue
      }
      if (!isComponentSuitable(descriptor)) {
        continue
      }
      val implementationClassName = if (headless && descriptor.headlessImplementationClass != null) {
        if (descriptor.headlessImplementationClass.isEmpty()) {
          continue
        }
        descriptor.headlessImplementationClass
      }
      else {
        descriptor.implementationClass
      }
      val keyClassName = descriptor.interfaceClass
                         ?: descriptor.implementationClass!!
      val keyClass = pluginDescriptor.classLoader.loadClass(keyClassName)
      registrar.registerInitializer(
        keyClassName = keyClassName,
        ComponentDescriptorInstanceInitializer(
          this,
          pluginDescriptor,
          keyClass,
          implementationClassName
        ),
        override = descriptor.overrides,
      )
    }
    registrar.complete()
  }

  fun createInitOldComponentsTask(): (suspend () -> Unit)? {
    if (componentContainer.instanceHolders().isEmpty()) {
      containerState.compareAndSet(ContainerState.PRE_INIT, ContainerState.COMPONENT_CREATED)
      return null
    }
    return {
      componentContainer.preloadAllInstances()
      containerState.compareAndSet(ContainerState.PRE_INIT, ContainerState.COMPONENT_CREATED)
    }
  }

  @Suppress("DuplicatedCode")
  @Deprecated(message = "Use createComponentsNonBlocking")
  protected open fun createComponents() {
    LOG.assertTrue(containerState.get() == ContainerState.PRE_INIT)

    val activity = when (val activityNamePrefix = activityNamePrefix()) {
      null -> null
      else -> StartUpMeasurer.startActivity("$activityNamePrefix${StartUpMeasurer.Activities.CREATE_COMPONENTS_SUFFIX}")
    }

    runBlockingInitialization {
      componentContainer.preloadAllInstances()
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

    componentContainer.preloadAllInstances()

    activity?.end()

    LOG.assertTrue(containerState.compareAndSet(ContainerState.PRE_INIT, ContainerState.COMPONENT_CREATED))
  }

  @TestOnly
  override fun <T : Any> replaceComponentInstance(componentKey: Class<T>, componentImplementation: T, parentDisposable: Disposable?) {
    val unregisterHandle = componentContainer.replaceInstance(
      keyClass = componentKey,
      instance = componentImplementation,
    )
    if (parentDisposable != null) {
      Disposer.register(parentDisposable) {
        @Suppress("DEPRECATION")
        if (componentImplementation is Disposable && !Disposer.isDisposed(componentImplementation)) {
          Disposer.dispose(componentImplementation)
        }
        unregisterHandle.unregister()
      }
    }
  }

  open fun getApplication(): Application? {
    return if (parent == null || this is Application) this as Application else parent.getApplication()
  }

  protected fun registerServices(services: List<ServiceDescriptor>, pluginDescriptor: IdeaPluginDescriptor) {
    LOG.trace { "${pluginDescriptor.pluginId} - registering services" }
    try {
      registerServices2Inner(services, pluginDescriptor)
    }
    catch (pce: CancellationException) {
      ProgressManager.checkCanceled()
      throw PluginException(pce, pluginDescriptor.pluginId)
    }
    catch (t: Throwable) {
      throw PluginException(t, pluginDescriptor.pluginId)
    }
    finally {
      LOG.trace { "${pluginDescriptor.pluginId} - end registering services" }
    }
  }

  private fun registerServices2Inner(services: List<ServiceDescriptor>, pluginDescriptor: IdeaPluginDescriptor) {
    if (services.isEmpty()) {
      return
    }
    val pluginClassLoader = pluginDescriptor.pluginClassLoader
    val registrationScope = if (pluginClassLoader is PluginAwareClassLoader) {
      pluginClassLoader.pluginCoroutineScope
    }
    else {
      null
    }
    val keyClassNames = ArrayList<String>()
    val registrar = serviceContainer.startRegistration(registrationScope)
    val app = getApplication()!!
    for (descriptor in services) {
      if (!isServiceSuitable(descriptor) || (descriptor.os != null && !descriptor.os.isSuitableForOs())) {
        continue
      }

      // Allow to re-define service implementations in plugins.
      // Empty serviceImplementation means we want unregistering service.
      val implementation = when {
        descriptor.testServiceImplementation != null && app.isUnitTestMode -> descriptor.testServiceImplementation
        descriptor.headlessImplementation != null && app.isHeadlessEnvironment -> descriptor.headlessImplementation
        else -> descriptor.serviceImplementation
      }

      val key = descriptor.serviceInterface
                ?: implementation
      if (descriptor.overrides) {
        registrar.overrideInitializer(
          keyClassName = key,
          initializer = if (implementation == null) {
            null
          }
          else {
            keyClassNames.add(key)
            ServiceDescriptorInstanceInitializer(
              keyClassName = key,
              instanceClassName = implementation,
              componentManager = this,
              pluginDescriptor,
              serviceDescriptor = descriptor,
            )
          }
        )
      }
      else {
        keyClassNames.add(key)
        registrar.registerInitializer(
          keyClassName = key,
          initializer = ServiceDescriptorInstanceInitializer(
            keyClassName = key,
            instanceClassName = checkNotNull(implementation),
            componentManager = this,
            pluginDescriptor,
            serviceDescriptor = descriptor,
          ),
        )
      }
    }
    val handle: UnregisterHandle? = registrar.complete()
    if (handle != null) {
      pluginServicesStore.putServicesUnregisterHandle(pluginDescriptor, handle)
    }
  }

  internal fun initializeService(component: Any, serviceDescriptor: ServiceDescriptor?, pluginId: PluginId) {
    @Suppress("DEPRECATION")
    if ((serviceDescriptor == null || !isPreInitialized(component)) &&
        (component is PersistentStateComponent<*> ||
         component is SettingsSavingComponent ||
         component is JDOMExternalizable)) {
      check(canBeInitOutOfOrder(component) || componentStore.isStoreInitialized || getApplication()!!.isUnitTestMode) {
        "You cannot get $component before component store is initialized"
      }

      componentStore.initComponent(component = component, serviceDescriptor = serviceDescriptor, pluginId = pluginId)
    }
  }

  protected open fun isPreInitialized(service: Any): Boolean {
    return service is PathMacroManager || service is IComponentStore || service is MessageBusFactory
  }

  private fun canBeInitOutOfOrder(service: Any): Boolean {
    return service is ProjectIdManager
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

    when (adapter) {
      is HolderAdapter -> {
        // TODO asserts
        val holder = adapter.holder
        @Suppress("UNCHECKED_CAST")
        return holder.getOrCreateInstanceBlocking(key.name, key) as T
      }
      else -> {
        return null
      }
    }
  }

  final override fun <T : Any> getService(serviceClass: Class<T>): T? {
    return doGetService(serviceClass, true) ?: return postGetService(serviceClass, createIfNeeded = true)
  }

  final override suspend fun <T : Any> getServiceAsync(keyClass: Class<T>): T {
    return serviceContainer.instance(keyClass)
  }

  override suspend fun <T : Any> getServiceAsyncIfDefined(keyClass: Class<T>): T? {
    val holder = serviceContainer.getInstanceHolder(keyClass) ?: return null
    @Suppress("UNCHECKED_CAST")
    return holder.getInstance(keyClass) as T
  }

  protected open fun <T : Any> postGetService(serviceClass: Class<T>, createIfNeeded: Boolean): T? = null

  final override fun <T : Any> getServiceIfCreated(serviceClass: Class<T>): T? {
    return doGetService(serviceClass, createIfNeeded = false) ?: postGetService(serviceClass, createIfNeeded = false)
  }

  protected open fun <T : Any> doGetService(serviceClass: Class<T>, createIfNeeded: Boolean): T? {
    val key = serviceClass.name
    val holder = try {
      serviceContainer.getInstanceHolder(serviceClass, createIfNeeded)
    }
    catch (cde: ContainerDisposedException) {
      if (createIfNeeded) {
        throwAlreadyDisposedIfNotUnderIndicatorOrJob(cause = cde)
        throw ProcessCanceledException(cde)
      }
      else {
        return null
      }
    }
    @Suppress("UNCHECKED_CAST")
    if (holder != null) {
      if (!createIfNeeded) {
        return try {
          holder.tryGetInstance() as T?
        }
        catch (_: CancellationException) {
          // container scope might be canceled => holder might hold CE
          return null
        }
      }
      rethrowCEasPCE {
        // fast path
        holder.tryGetInstance()?.let {
          return it as T
        }
      }
      if (containerState.get() >= ContainerState.DISPOSE_IN_PROGRESS) {
        // TODO log when an instance is initialized in startDispose before DISPOSE_IN_PROGRESS is set (additional state is needed)
        // TODO make this an error
        LOG.warn(IllegalStateException("${holder.instanceClassName()} is initialized during dispose"))
      }
      @Suppress("UNCHECKED_CAST")
      return holder.getOrCreateInstanceBlocking(debugString = serviceClass.name, keyClass = serviceClass) as T
    }
    if (parent != null) {
      val result = parent.doGetService(serviceClass, createIfNeeded)
      if (result != null) {
        fun decodeContainerName(container: ComponentManager) = when {
          container is Application -> "application"
          container is Project -> "project"
          container is Module -> "module"
          (container.javaClass.name == "com.intellij.openapi.module.impl.ModuleComponentManager") -> "module"
          else -> container.javaClass.name
        }
        LOG.error("$key is registered as ${decodeContainerName(parent)} service, but requested as ${decodeContainerName(this)} one")
        return result
      }
    }

    if (isLightServiceSupported && !serviceClass.isInterface && !Modifier.isFinal(serviceClass.modifiers) &&
        serviceClass.isAnnotationPresent(Service::class.java)) {
      throw PluginException.createByClass("Light service class $serviceClass must be final", null, serviceClass)
    }

    @Suppress("DEPRECATION")
    val result = getComponent(serviceClass) ?: return null
    LOG.error(PluginException.createByClass(
      "$key requested as a service, but it is a component - " +
      "convert it to a service or change call to " +
      if (parent == null) "ApplicationManager.getApplication().getComponent()" else "project.getComponent()",
      null, serviceClass
    ))
    return result
  }

  private class StartUpMessageDeliveryListener(private val messageBus: MessageBusImpl, private val logMessageBusDeliveryFunction: (Topic<*>, String, Any, Long) -> Unit): MessageDeliveryListener {
    override fun messageDelivered(topic: Topic<*>, messageName: String, handler: Any, durationNanos: Long) {
      if (!StartUpMeasurer.isMeasuringPluginStartupCosts()) {
        messageBus.removeMessageDeliveryListener(this)
        return
      }
      logMessageBusDeliveryFunction(topic, messageName, handler, durationNanos)
    }
  }

  @Synchronized
  private fun getOrCreateMessageBusUnderLock(): MessageBus {
    var messageBus = this.messageBus
    if (messageBus != null) {
      return messageBus
    }

    @Suppress("RetrievingService", "SimplifiableServiceRetrieving")
    messageBus = getApplication()!!.getService(MessageBusFactory::class.java).createMessageBus(this, parent?.messageBus) as MessageBusImpl
    if (StartUpMeasurer.isMeasuringPluginStartupCosts()) {
      messageBus.addMessageDeliveryListener(StartUpMessageDeliveryListener(messageBus, ::logMessageBusDelivery))
    }

    registerServiceInstance(MessageBus::class.java, messageBus, fakeCorePluginDescriptor)
    this.messageBus = messageBus
    return messageBus
  }

  protected open fun logMessageBusDelivery(topic: Topic<*>, messageName: String, handler: Any, duration: Long) {
    val loader = handler.javaClass.classLoader
    val pluginId = PluginUtil.getPluginId(loader).idString
    StartUpMeasurer.addPluginCost(pluginId, "MessageBus", duration)
  }

  /**
   * Use only if approved by core team.
   */
  override fun registerService(
    serviceInterface: Class<*>,
    implementation: Class<*>,
    pluginDescriptor: PluginDescriptor,
    override: Boolean,
    clientKind: ClientKind?
  ) {
    val descriptor = ServiceDescriptor(serviceInterface.name, implementation.name, null, null, false,
                                       null, PreloadMode.FALSE, clientKind, null)
    serviceContainer.registerInitializer(
      keyClass = serviceInterface,
      initializer = ServiceClassInstanceInitializer(
        componentManager = this,
        instanceClass = implementation,
        pluginId = pluginDescriptor.pluginId,
        serviceDescriptor = descriptor,
      ),
      override = override
    )
  }

  /**
   * Use only if approved by core team.
   */
  override fun <T : Any> registerServiceInstance(serviceInterface: Class<T>,
                                        instance: T,
                                        @Suppress("UNUSED_PARAMETER") pluginDescriptor: PluginDescriptor) {
    serviceContainer.replaceInstance(serviceInterface, instance)
  }

  @Suppress("DuplicatedCode")
  @TestOnly
  override fun <T : Any> replaceServiceInstance(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable) {
    // TODO this loses info that the instance is a dynamic service
    val unregisterHandle = serviceContainer.replaceInstance(keyClass = serviceInterface, instance = instance)
    Disposer.register(parentDisposable) {
      try {
        @Suppress("DEPRECATION")
        if (instance is Disposable && !Disposer.isDisposed(instance)) {
          Disposer.dispose(instance)
        }
      }
      finally {
        try {
          unregisterHandle.unregister()
        }
        catch (t: Throwable) {
          // The container might be already disposed during fixture disposal
          // => but [parentDisposable] is [UsefulTestCase.getTestRootDisposable] which might be disposed after the fixture.
          //
          // This indicates a problem with scoping.
          // The [parentDisposable] should be disposed on the same level as the code which replaces the service.
          // If the service is registered in a [setUp] method before a test,
          // then the [parentDisposable] should be disposed in [tearDown] right after the test.
          // In other words, it's generally incorrect to use [UsefulTestCase.getTestRootDisposable]
          // as a [parentDisposable] for the replacement service.
          LOG.warn("Error unregistering ${serviceInterface.name} -> ${instance.javaClass.name}", t)
        }
      }
    }
  }

  @TestOnly
  override fun unregisterService(serviceInterface: Class<*>) {
    val key = serviceInterface.name
    if (serviceContainer.unregister(keyClassName = key) == null) {
      error("Trying to unregister $key service which is not registered")
    }
  }

  @Suppress("DuplicatedCode")
  override fun <T : Any> replaceRegularServiceInstance(serviceInterface: Class<T>, instance: T) {
    val previousInstance = serviceContainer
      .replaceInstanceForever(serviceInterface, instance)
      ?.tryGetInstance()
    if (previousInstance is Disposable) {
      Disposer.dispose(previousInstance)
    }
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
      return instantiateClass(doLoadClass(className, pluginDescriptor, checkCoreSubModules = true) as Class<T>, pluginId)
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

  final override fun createListener(descriptor: PluginListenerDescriptor): Any {
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
      is ProcessCanceledException, is ExtensionNotApplicableException, is PluginException -> effectiveError
      else -> PluginException(effectiveError, pluginId)
    }
  }

  final override fun createError(message: String, pluginId: PluginId): PluginException = PluginException(message, pluginId)

  final override fun createError(message: String,
                                 error: Throwable?,
                                 pluginId: PluginId,
                                 attachments: MutableMap<String, String>?): RuntimeException {
    return PluginException(message, error, pluginId, attachments?.map { Attachment(it.key, it.value) } ?: java.util.List.of())
  }

  override fun unloadServices(module: IdeaPluginDescriptor, services: List<ServiceDescriptor>) {
    val debugString = debugString(true)
    // IJPL-157548 Component container also retains requested `keyClass` instances because it's the same `InstanceContainerImpl`.
    componentContainer.cleanCache()
    // IJPL-157548 If `serviceIfCreated` is used, no dynamic instance is registered,
    // and the fact that there is no instance is kept in the cache by the `keyClass`.
    //
    // An alternative approach would be to put `cleanCache` inside `UnregisterHandle`, but it would make the handle non-null.
    // Another more robust approach is to integrate `PluginServicesStore` into the `InstanceContainer`.
    //
    // If `handle` returns non-empty `holders` or `dynamicInstances` is not empty, the cache was cleared already.
    // For simplicity’s sake, we clean the cache once every time.
    serviceContainer.cleanCache()
    val handle = pluginServicesStore.removeServicesUnregisterHandle(module)
    val dynamicInstances = pluginServicesStore.removeDynamicServices(module)
    if (handle == null && dynamicInstances.isEmpty()) {
      LOG.trace { "$debugString : nothing to unload ${module.pluginId}:${module.descriptorPath}" }
      return
    }
    val holders = handle?.unregister() ?: emptyMap()
    if (holders.isEmpty() && dynamicInstances.isEmpty()) {
      // warn because the handle should not be in the map in the first place
      LOG.warn("$debugString : nothing unloaded for ${module.pluginId}:${module.descriptorPath}")
      return
    }
    for (holder in dynamicInstances) {
      serviceContainer.unregister(holder.instanceClassName(), unregisterDynamic = true)
    }
    val store = componentStore
    for (holder in holders.values + dynamicInstances) {
      val instance = holder.tryGetInstance()
                     ?: continue // TODO race! this will skip instances which were requested, but not yet completed initialization
      if (instance is Disposable) {
        Disposer.dispose(instance)
      }
      store.unloadComponent(instance)
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
        if (!isServiceSuitable(service) || (service.os != null && !service.os.isSuitableForOs())) {
          continue
        }

        val scope = when (service.preload) {
          PreloadMode.TRUE -> if (onlyIfAwait) null else asyncScope
          PreloadMode.NOT_HEADLESS -> if (onlyIfAwait || getApplication()!!.isHeadlessEnvironment) null else asyncScope
          PreloadMode.NOT_LIGHT_EDIT -> if (onlyIfAwait || isLightEdit()) null else asyncScope
          PreloadMode.AWAIT -> syncScope
          PreloadMode.FALSE -> null
          else -> throw IllegalStateException("Unknown preload mode ${service.preload}")
        }
        scope ?: continue

        if (isServicePreloadingCancelled) {
          return
        }

        if (plugin.pluginId != PluginManagerCore.CORE_ID) {
          val impl = getServiceImplementation(service, this)
          val message = "`preload=${service.preload.name}` must be used only for core services (service=$impl, plugin=${plugin.pluginId})"
          val isKnown = servicePreloadingAllowListForNonCorePlugin.contains(impl)
          if (service.preload == PreloadMode.AWAIT && !isKnown) {
            LOG.error(PluginException(message, plugin.pluginId))
          }
          else if (!isKnown || !impl.startsWith("com.intellij.")) {
            val application = ApplicationManager.getApplication()
            if (application == null || application.isUnitTestMode || application.isInternal) {
              // logged only during development, let's not spam users
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
                // instanceHolder will throw PluginException if needed
                LOG.error(e)
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
    serviceContainer.getInstanceHolder(keyClassName = serviceInterface)
      ?.takeIf(InstanceHolder::isStatic)
      ?.getInstance(keyClass = null)
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

    scopeHolder.containerScope.cancel("ComponentManagerImpl.dispose is called")

    // dispose components and services
    Disposer.dispose(serviceParentDisposable)

    // release references to the service instances
    serviceContainer.dispose()
    componentContainer.dispose()

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

  override fun stopServicePreloading() {
    isServicePreloadingCancelled = true
  }

  @Deprecated("Deprecated in Java")
  @Suppress("DEPRECATION")
  final override fun getComponent(name: String): BaseComponent? {
    for (instance in componentContainer.initializedInstances()) {
      if (instance is BaseComponent && name == instance.componentName) {
        return instance
      }
    }
    return null
  }

  override fun <T : Any> getServiceByClassName(serviceClassName: String): T? {
    @Suppress("UNCHECKED_CAST")
    return checkState { serviceContainer.getInstanceHolder(keyClassName = serviceClassName) }
      ?.takeIf(InstanceHolder::isStatic)
      ?.getOrCreateInstanceBlocking(serviceClassName, keyClass = null) as T?
  }

  override fun getServiceImplementation(key: Class<*>): Class<*>? {
    return checkState { serviceContainer.getInstanceHolder(keyClass = key) }
      ?.takeIf(InstanceHolder::isStatic)
      ?.instanceClass()
  }

  override fun isServiceSuitable(descriptor: ServiceDescriptor): Boolean = descriptor.client == null

  protected open fun isComponentSuitable(componentConfig: ComponentConfig): Boolean {
    val options = componentConfig.options ?: return true
    return !java.lang.Boolean.parseBoolean(options.get("internal")) || ApplicationManager.getApplication().isInternal
  }

  final override fun getDisposed(): Condition<*> = Condition<Any?> { isDisposed }

  override fun instances(createIfNeeded: Boolean, filter: ((implClass: Class<*>) -> Boolean)?): Sequence<Any> {
    return (componentContainer.instanceHolders().asSequence() + serviceContainer.instanceHolders()).mapNotNull { holder ->
      try {
        if (filter == null) {
          holder.getInstanceBlocking(debugString = holder.instanceClassName(), keyClass = null, createIfNeeded = createIfNeeded)
        }
        else {
          val instanceClass = holder.instanceClass()
          if (filter(instanceClass)) {
            holder.getInstanceBlocking(debugString = instanceClass.name, keyClass = null, createIfNeeded = createIfNeeded)
          }
          else {
            null
          }
        }
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.warn(e)
        null
      }
    }
  }

  override fun processAllImplementationClasses(processor: (componentClass: Class<*>, plugin: PluginDescriptor?) -> Unit) {
    processAllHolders { _, componentClass, plugin ->
      processor(componentClass, plugin)
    }
  }

  override fun processAllHolders(processor: (keyClass: String, componentClass: Class<*>, plugin: PluginDescriptor?) -> Unit) {
    fun process(key: String, holder: InstanceHolder) {
      val clazz = try {
        holder.instanceClass()
      }
      catch (e: ClassNotFoundException) {
        LOG.warn(e)
        return
      }
      try {
        val descriptor = (clazz.classLoader as? PluginAwareClassLoader)?.pluginDescriptor
                         ?: fakeCorePluginDescriptor
        processor(key, clazz, descriptor)
      }
      catch (pce: ProcessCanceledException) {
        throw pce
      }
      catch (t: Throwable) {
        LOG.error(t)
      }
    }
    for ((key, holder) in serviceContainer.instanceHoldersAndKeys()) {
      process(key, holder)
    }
    for ((key, holder) in componentContainer.instanceHoldersAndKeys()) {
      process(key, holder)
    }
  }

  internal fun getInstanceHolder(keyClass: Class<*>): InstanceHolder? {
    return componentContainer.getInstanceHolder(keyClass)
           ?: serviceContainer.getInstanceHolder(keyClass)
           ?: parent?.getInstanceHolder(keyClass)
  }

  internal fun getComponentAdapter(keyClass: Class<*>): ComponentAdapter? {
    assertComponentsSupported()
    return ignoreDisposal {
      componentContainer.getInstanceHolder(keyClass)?.let { HolderAdapter(keyClass, it) }
      ?: serviceContainer.getInstanceHolder(keyClass)?.let { HolderAdapter(keyClass.name, it) }
    } ?: parent?.getComponentAdapter(keyClass)
  }

  override fun unregisterComponent(componentKey: Class<*>): ComponentAdapter? {
    assertComponentsSupported()
    return componentContainer.unregister(componentKey.name)?.let { holder ->
      HolderAdapter(key = componentKey, holder)
    }
  }

  @TestOnly
  override fun registerComponentInstance(key: Class<*>, instance: Any) {
    check(getApplication()!!.isUnitTestMode)
    assertComponentsSupported()
    @Suppress("UNCHECKED_CAST")
    componentContainer.registerInstance(key as Class<Any>, instance)
  }

  private fun assertComponentsSupported() {
    if (!isComponentSupported) {
      error("components aren't support")
    }
  }

  // project level extension requires Project as a constructor argument, so, for now, constructor injection is disabled only for app level
  final override fun isInjectionForExtensionSupported(): Boolean = parent != null

  private fun getHolderOfType(componentType: Class<*>): InstanceHolder? {
    for (holder in componentContainer.instanceHolders()) {
      val instanceClass = holder.instanceClass()
      if (componentType === instanceClass || componentType.isAssignableFrom(instanceClass)) {
        return holder
      }
    }
    return parent?.getHolderOfType(componentType)
  }

  internal fun getComponentAdapterOfType(componentType: Class<*>): ComponentAdapter? {
    ignoreDisposal {
      componentContainer.getInstanceHolder(keyClass = componentType)
    }?.let {
      return HolderAdapter(key = componentType, holder = it)
    }
    for (holder in componentContainer.instanceHolders()) {
      val instanceClass = holder.instanceClass()
      if (componentType === instanceClass || componentType.isAssignableFrom(instanceClass)) {
        return HolderAdapter(key = componentType, holder = holder)
      }
    }
    return null
  }

  override fun <T : Any> collectInitializedComponents(aClass: Class<T>): List<T> {
    val result = ArrayList<T>()
    for (instance in componentContainer.initializedInstances()) {
      if (aClass.isAssignableFrom(instance.javaClass)) {
        @Suppress("UNCHECKED_CAST")
        (result.add(instance as T))
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
    val holder = ignoreDisposal {
      componentContainer.getInstanceHolder(keyClass = componentKey)
      ?: serviceContainer.getInstanceHolder(keyClass = componentKey)
    }
    return holder != null || parent?.hasComponent(componentKey) == true
  }

  override fun instanceCoroutineScope(pluginClass: Class<*>): CoroutineScope {
    val intersectionScope = pluginCoroutineScope(pluginClass.classLoader)
    // The parent scope should become canceled only when the container is disposed, or the plugin is unloaded.
    // Leaking the parent scope might lead to premature cancellation.
    // Fool proofing: a fresh child scope is created per instance to avoid leaking the parent to clients.
    return intersectionScope.childScope(pluginClass.name)
  }

  // to run post-start-up activities - to not create scope for each class and do not keep it alive
  override fun pluginCoroutineScope(pluginClassloader: ClassLoader): CoroutineScope {
    val intersectionScope = if (pluginClassloader is PluginAwareClassLoader) {
      val pluginScope = pluginClassloader.pluginCoroutineScope
      // for consistency
      val parentScope = parent?.scopeHolder?.intersectScope(pluginScope) ?: pluginScope
      scopeHolder.intersectScope(parentScope)
    }
    else {
      // non-unloadable
      scopeHolder.containerScope
    }
    return intersectionScope
  }
}

private class PluginServicesStore {
  private val regularServices = ConcurrentHashMap<IdeaPluginDescriptor, UnregisterHandle>()
  private val dynamicServices = ConcurrentHashMap<IdeaPluginDescriptor, UList<InstanceHolder>>()

  fun putServicesUnregisterHandle(descriptor: IdeaPluginDescriptor, handle: UnregisterHandle) {
    val prev = regularServices.put(descriptor, handle)
    assert(prev == null) {
      "plugin ${descriptor.name}:${descriptor.descriptorPath} was not unloaded before subsequent loading"
    }
  }

  fun removeServicesUnregisterHandle(descriptor: IdeaPluginDescriptor): UnregisterHandle? = regularServices.remove(descriptor)

  fun addDynamicService(descriptor: IdeaPluginDescriptor, holder: InstanceHolder) {
    dynamicServices.compute(descriptor) { _, instances ->
      (instances ?: UList()).add(holder)
    }
  }

  fun removeDynamicServices(descriptor: IdeaPluginDescriptor): List<InstanceHolder> {
    return dynamicServices.remove(descriptor)?.toList() ?: java.util.List.of()
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

internal fun doLoadClass(name: String, pluginDescriptor: PluginDescriptor, checkCoreSubModules: Boolean = false): Class<*> {
  // maybe null in unit tests
  val classLoader = pluginDescriptor.pluginClassLoader ?: ComponentManagerImpl::class.java.classLoader
  if (classLoader is PluginAwareClassLoader) {
    return classLoader.tryLoadingClass(name, true) ?: throw ClassNotFoundException("$name $classLoader")
  }
  else {
    try {
      return classLoader.loadClass(name)
    }
    catch (e: ClassNotFoundException) {
      if (checkCoreSubModules && pluginDescriptor.pluginId == PluginManagerCore.CORE_ID && pluginDescriptor is IdeaPluginDescriptorImpl) {
        for (module in pluginDescriptor.content.modules) {
          val subDescriptor = module.requireDescriptor()
          if (subDescriptor.packagePrefix == null && !module.name.startsWith("intellij.libraries.")) {
            val pluginClassLoader = subDescriptor.classLoader as? PluginAwareClassLoader ?: continue
            pluginClassLoader.loadClassInsideSelf(name)?.let {
              assert(it.isAnnotationPresent(InternalIgnoreDependencyViolation::class.java))
              return it
            }
          }
        }
      }

      throw e
    }
  }
}

private inline fun executeRegisterTask(mainPluginDescriptor: IdeaPluginDescriptorImpl,
                                       crossinline task: (IdeaPluginDescriptorImpl) -> Unit) {
  task(mainPluginDescriptor)
  executeRegisterTaskForOldContent(mainPluginDescriptor, task)
}

// Ask Core team approve before changing this set
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
  "com.jetbrains.rider.protocol.RiderProtocolProjectSessionsManager",
  "com.jetbrains.rider.projectView.workspace.impl.RiderWorkspaceModel",
)

private fun InstanceHolder.getInstanceBlocking(debugString: String, keyClass: Class<*>?, createIfNeeded: Boolean): Any? {
  if (createIfNeeded) {
    return getOrCreateInstanceBlocking(debugString = debugString, keyClass = keyClass)
  }
  else {
    try {
      return tryGetInstance()
    }
    catch (_: CancellationException) {
      return null
    }
  }
}

internal fun InstanceHolder.getOrCreateInstanceBlocking(debugString: String, keyClass: Class<*>?): Any {
  // container scope might be canceled
  // => holder is initialized with CE
  // => caller should get PCE
  rethrowCEasPCE {
    val instance = tryGetInstance()
    if (instance != null) {
      return instance
    }
  }

  if (!Cancellation.isInNonCancelableSection() && !checkOutsideClassInitializer(debugString)) {
    Cancellation.withNonCancelableSection().use {
      return doGetOrCreateInstanceBlocking(keyClass)
    }
  }
  return doGetOrCreateInstanceBlocking(keyClass)
}

private fun InstanceHolder.doGetOrCreateInstanceBlocking(keyClass: Class<*>?): Any {
  try {
    return runBlockingInitialization {
      getInstanceInCallerContext(keyClass)
    }
  }
  catch (e: ProcessCanceledException) {
    throwAlreadyDisposedIfNotUnderIndicatorOrJob(cause = e)
    throw e
  }
}

/**
 * @return `true` if called outside a class initializer, `false` if called inside a class initializer
 */
private fun checkOutsideClassInitializer(debugString: String): Boolean {
  val className = isInsideClassInitializer() ?: return true
  if (logAccessInsideClinit.get()) {
    dontLogAccessInClinit().use {
      val message = "$className <clinit> requests $debugString instance. " +
                    "Class initialization must not depend on services. " +
                    "Consider using instance of the service on-demand instead."
      if (!ApplicationManager.getApplication().isUnitTestMode) {
        LOG.error(message)
      }
      else {
        // TODO make this an error IJPL-156676
        LOG.warn(message)
      }
    }
  }
  return false
}

private fun isInsideClassInitializer(): String? {
  return StackWalker.getInstance().walk { frames ->
    frames.asSequence().firstNotNullOfOrNull { frame ->
      if (frame.methodName == "<clinit>") {
        frame.className
      }
      else {
        null
      }
    }
  }
}

private val logAccessInsideClinit = ThreadLocal.withInitial { true }

private fun dontLogAccessInClinit(): AccessToken {
  // Logger itself also loads services, which results in SOE:
  // at com.intellij.serviceContainer.ComponentManagerImpl.getService
  // at com.intellij.ide.plugins.PluginUtil.getInstance(PluginUtil.java:13)
  // at com.intellij.diagnostic.DefaultIdeaErrorLogger.canHandle(DefaultIdeaErrorLogger.java:39)
  // at com.intellij.diagnostic.DialogAppender.queueAppend(DialogAppender.kt:76)
  // at com.intellij.diagnostic.DialogAppender.publish(DialogAppender.kt:48)
  // at java.logging/java.util.logging.Logger.log(Logger.java:983)
  // ...
  // at com.intellij.openapi.diagnostic.Logger.error(Logger.java:376)
  // at com.intellij.serviceContainer.ComponentManagerImplKt.getOrCreateInstanceBlocking(ComponentManagerImpl.kt:1557)
  // at com.intellij.serviceContainer.ComponentManagerImpl.doGetService(ComponentManagerImpl.kt:744)
  // at com.intellij.serviceContainer.ComponentManagerImpl.getService(ComponentManagerImpl.kt:688)
  return withThreadLocal(logAccessInsideClinit) { false }
}

/**
 * Should be used everywhere [ComponentManagerImpl.checkState] is used.
 */
private inline fun <X> checkState(x: () -> X): X {
  try {
    return x()
  }
  catch (e: ContainerDisposedException) {
    ProgressManager.checkCanceled()
    throw e
  }
}

/**
 * Used everywhere the adapter was requested from the `ComponentManagerImpl.componentKeyToAdapter`
 * but [ComponentManagerImpl.checkState] is not used.
 */
private inline fun <X> ignoreDisposal(x: () -> X): X? {
  return try {
    x()
  }
  catch (_: ContainerDisposedException) {
    null
  }
}

private inline fun <X> rethrowCEasPCE(action: () -> X): X {
  try {
    return action()
  }
  catch (e: ProcessCanceledException) {
    throw e
  }
  catch (e: CancellationException) {
    throwAlreadyDisposedIfNotUnderIndicatorOrJob(e)
    throw CeProcessCanceledException(e)
  }
}

private fun throwAlreadyDisposedIfNotUnderIndicatorOrJob(cause: Throwable) {
  if (!isUnderIndicatorOrJob()) {
    // in useInstanceContainer=false AlreadyDisposedException was thrown instead
    throw AlreadyDisposedException("Container is already disposed").initCause(cause)
  }
}

private fun <X> runBlockingInitialization(action: suspend CoroutineScope.() -> X): X {
  return prepareThreadContext { ctx -> // reset thread context
    val (lockPermitContext, cleanup) = getLockPermitContext(ctx, false)
    try {
      val contextForInitializer =
        (ctx.contextModality()?.asContextElement() ?: EmptyCoroutineContext) + // leak modality state into initialization coroutine
        (ctx[Job] ?: EmptyCoroutineContext) + // bind to caller Job
        lockPermitContext + // capture whether the caller holds the read lock
        (currentTemporaryThreadContextOrNull() ?: EmptyCoroutineContext) + // propagate modality state/CurrentlyInitializingInstance
        NestedBlockingEventLoop(Thread.currentThread()) // avoid processing events from outer runBlocking (if any)
      @OptIn(InternalCoroutinesApi::class)
      IntellijCoroutines.runBlockingWithParallelismCompensation(contextForInitializer, action)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: CancellationException) {
      throw CeProcessCanceledException(e)
    }
    finally {
      cleanup.finish()
    }
  }
}

@Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE", "CANNOT_OVERRIDE_INVISIBLE_MEMBER", "ERROR_SUPPRESSION")
private class NestedBlockingEventLoop(override val thread: Thread) : EventLoopImplBase() {
  override fun shouldBeProcessedFromContext(): Boolean = true
}

@Internal
fun ComponentManager.getComponentManagerImpl(): ComponentManagerImpl {
  return (this as ComponentManagerEx).getMutableComponentContainer() as ComponentManagerImpl
}
