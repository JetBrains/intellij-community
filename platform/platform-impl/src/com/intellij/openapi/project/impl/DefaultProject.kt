// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.openapi.project.impl

import com.intellij.configurationStore.StoreUtil.saveSettings
import com.intellij.diagnostic.ActivityCategory
import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.client.ClientAwareComponentManager
import com.intellij.openapi.client.ClientKind
import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionsArea
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.project.PROJECT_ID
import com.intellij.platform.project.ProjectId
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.coroutineScopeMethodType
import com.intellij.serviceContainer.emptyConstructorMethodType
import com.intellij.serviceContainer.findConstructorOrNull
import com.intellij.serviceContainer.getComponentManagerImpl
import com.intellij.util.application
import com.intellij.util.messages.MessageBus
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.annotations.TestOnly
import org.picocontainer.ComponentAdapter
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

private val LOG = logger<DefaultProject>()

internal class DefaultProject : UserDataHolderBase(), Project, ComponentManagerEx {
  private val timedProject = object : DefaultProjectTimed(this) {
    public override fun compute(): Project {
      val app = ApplicationManager.getApplication()
      LOG.assertTrue(!app.isDisposed(), "Application is being disposed!")
      val project = DefaultProjectImpl(actualContainerInstance = this@DefaultProject)
      val componentStoreFactory = app.service<ProjectStoreFactory>()
      project.registerServiceInstance(serviceInterface = IComponentStore::class.java,
                                      instance = componentStoreFactory.createDefaultProjectStore(project),
                                      pluginDescriptor = ComponentManagerImpl.fakeCorePluginDescriptor)

      // mark myDelegate as not disposed if someone cluelessly did Disposer.dispose(getDefaultProject())
      Disposer.register(this@DefaultProject, this)
      return project
    }

    public override fun init(project: Project) {
      (project as DefaultProjectImpl).init()
      application.messageBus.syncPublisher(DefaultProjectListener.TOPIC).defaultProjectImplCreated(project)
    }
  }

  init {
    @Suppress("LeakingThis")
    putUserData(PROJECT_ID, ProjectId.create())
  }

  override fun <T> instantiateClass(aClass: Class<T>, pluginId: PluginId): T = delegate.instantiateClass(aClass, pluginId)

  override fun <T : Any> instantiateClass(className: String, pluginDescriptor: PluginDescriptor): T {
    return delegate.instantiateClass(className, pluginDescriptor)
  }

  override fun <T> instantiateClassWithConstructorInjection(aClass: Class<T>, key: Any, pluginId: PluginId): T {
    return delegate.instantiateClassWithConstructorInjection(aClass, key, pluginId)
  }

  override fun createError(message: String, pluginId: PluginId): RuntimeException {
    return delegate.createError(message, pluginId)
  }

  override fun createError(message: @NonNls String,
                           error: Throwable?,
                           pluginId: PluginId,
                           attachments: Map<String, String>?): RuntimeException {
    return delegate.createError(message, null, pluginId, attachments)
  }

  override fun <T> loadClass(className: String, pluginDescriptor: PluginDescriptor): Class<T> {
    return delegate.loadClass(className, pluginDescriptor)
  }

  override fun logError(error: Throwable, pluginId: PluginId) {
    delegate.logError(error, pluginId)
  }

  override fun createError(error: Throwable, pluginId: PluginId) = delegate.createError(error, pluginId)

  override fun hasComponent(interfaceClass: Class<*>): Boolean = delegate.hasComponent(interfaceClass)

  // make default project facade equal to any other default project facade to enable Map<Project, T>
  override fun equals(other: Any?): Boolean = other is Project && other.isDefault

  override fun hashCode(): Int = DEFAULT_HASH_CODE

  override fun toString() = "Project${if (isDisposed) " (Disposed)" else ""}${TEMPLATE_PROJECT_NAME}"

  override fun dispose() {
    if (!ApplicationManager.getApplication().isDisposed()) {
      throw IllegalStateException("Must not dispose default project")
    }
    Disposer.dispose(timedProject)
  }
  
  override fun getMutableComponentContainer(): ComponentManager = 
    delegate.getComponentManagerImpl()

  @TestOnly
  fun disposeDefaultProjectAndCleanupComponentsForDynamicPluginTests() {
    ApplicationManager.getApplication().runWriteAction(Runnable { Disposer.dispose(timedProject) })
  }

  private val delegate: Project
    get() = timedProject.get()

  val isCached: Boolean
    get() = timedProject.isCached

  // delegates
  override fun getName() = TEMPLATE_PROJECT_NAME

  @Suppress("DeprecatedCallableAddReplaceWith")
  @Deprecated("")
  override fun getBaseDir(): VirtualFile? = null

  override fun getBasePath(): @SystemIndependent String? = null

  override fun getProjectFile(): VirtualFile? = null

  override fun getProjectFilePath(): @SystemIndependent String? = null

  override fun getWorkspaceFile(): VirtualFile? = null

  override fun getLocationHash(): String = name

  override fun save() {
    delegate.save()
  }

  override fun isOpen(): Boolean = false

  override fun isInitialized(): Boolean = true

  override fun isDefault(): Boolean = true

  override fun getCoroutineScope(): CoroutineScope = (ApplicationManager.getApplication() as ComponentManagerEx).getCoroutineScope()
  
  override fun instanceCoroutineScope(pluginClass: Class<*>): CoroutineScope = 
    (delegate as ComponentManagerEx).getCoroutineScope()

  override fun unregisterComponent(componentKey: Class<*>): ComponentAdapter? =
    (delegate as ComponentManagerEx).unregisterComponent(componentKey)

  override fun <T : Any> replaceServiceInstance(serviceInterface: Class<T>, instance: T, parentDisposable: Disposable) =
    (delegate as ComponentManagerEx).replaceServiceInstance(serviceInterface, instance, parentDisposable)

  override fun instances(createIfNeeded: Boolean, filter: ((Class<*>) -> Boolean)?): Sequence<Any> =
    (delegate as ComponentManagerEx).instances(createIfNeeded, filter)

  override fun processAllImplementationClasses(processor: (Class<*>, PluginDescriptor?) -> Unit) =
    (delegate as ComponentManagerEx).processAllImplementationClasses(processor)

  override fun registerService(serviceInterface: Class<*>, implementation: Class<*>, pluginDescriptor: PluginDescriptor, override: Boolean, clientKind: ClientKind?) =
    (delegate as ComponentManagerEx).registerService(serviceInterface, implementation, pluginDescriptor, override, clientKind)

  override fun <T : Any> getServiceByClassName(serviceClassName: String): T? =
    (delegate as ComponentManagerEx).getServiceByClassName(serviceClassName)

  override fun unloadServices(module: IdeaPluginDescriptor, services: List<ServiceDescriptor>) =
    (delegate as ComponentManagerEx).unloadServices(module, services)

  override fun processAllHolders(processor: (String, Class<*>, PluginDescriptor?) -> Unit) =
    (delegate as ComponentManagerEx).processAllHolders(processor)

  override fun pluginCoroutineScope(pluginClassloader: ClassLoader): CoroutineScope =
    (delegate as ComponentManagerEx).pluginCoroutineScope(pluginClassloader)

  override fun stopServicePreloading() =
    (delegate as ComponentManagerEx).stopServicePreloading()

  override fun <T : Any> collectInitializedComponents(aClass: Class<T>): List<T> =
    (delegate as ComponentManagerEx).collectInitializedComponents(aClass)

  override fun debugString(): String =
    (delegate as ComponentManagerEx).debugString()

  override fun isServiceSuitable(descriptor: ServiceDescriptor): Boolean =
    (delegate as ComponentManagerEx).isServiceSuitable(descriptor)

  override fun <T : Any> registerServiceInstance(serviceInterface: Class<T>, instance: T, pluginDescriptor: PluginDescriptor) =
    (delegate as ComponentManagerEx).registerServiceInstance(serviceInterface, instance, pluginDescriptor)

  override fun getServiceImplementation(key: Class<*>): Class<*>? =
    (delegate as ComponentManagerEx).getServiceImplementation(key)

  override fun <T : Any> replaceComponentInstance(componentKey: Class<T>, componentImplementation: T, parentDisposable: Disposable?) {
    (delegate as ComponentManagerEx).replaceComponentInstance(componentKey, componentImplementation, parentDisposable)
  }

  override fun registerComponentInstance(key: Class<*>, instance: Any) {
    (delegate as ComponentManagerEx).registerComponentInstance(key, instance)
  }

  override fun unregisterService(serviceInterface: Class<*>) {
    (delegate as ComponentManagerEx).unregisterService(serviceInterface)
  }

  override fun <T : Any> replaceRegularServiceInstance(serviceInterface: Class<T>, instance: T) {
    (delegate as ComponentManagerEx).replaceRegularServiceInstance(serviceInterface, instance)
  }

  @Suppress("DEPRECATION")
  @Deprecated("")
  override fun getComponent(name: String): com.intellij.openapi.components.BaseComponent? = delegate.getComponent(name)

  override fun getActivityCategory(isExtension: Boolean): ActivityCategory {
    return if (isExtension) ActivityCategory.PROJECT_EXTENSION else ActivityCategory.PROJECT_SERVICE
  }

  override fun <T> getService(serviceClass: Class<T>): T {
    return delegate.getService(serviceClass)
  }

  override fun <T> getServiceIfCreated(serviceClass: Class<T>): T? = delegate.getServiceIfCreated(serviceClass)

  override suspend fun <T : Any> getServiceAsync(keyClass: Class<T>): T {
    return (delegate as ComponentManagerEx).getServiceAsync(keyClass)
  }

  @Deprecated("Deprecated in interface")
  override fun <T> getComponent(interfaceClass: Class<T>): T = delegate.getComponent(interfaceClass)

  override fun isInjectionForExtensionSupported(): Boolean = true

  override fun getExtensionArea(): ExtensionsArea = delegate.getExtensionArea()

  override fun getMessageBus(): MessageBus = delegate.getMessageBus()

  override fun isDisposed(): Boolean = ApplicationManager.getApplication().isDisposed()

  override fun getDisposed(): Condition<*> = ApplicationManager.getApplication().getDisposed()
}

private const val TEMPLATE_PROJECT_NAME = "Default (Template) Project"

// chosen by fair dice roll. guaranteed to be random. see https://xkcd.com/221/ for details.
private const val DEFAULT_HASH_CODE = 4

private class DefaultProjectImpl(
  private val actualContainerInstance: Project
) : ClientAwareComponentManager(ApplicationManager.getApplication().getComponentManagerImpl()), Project {
  init {
    @Suppress("LeakingThis")
    putUserData(PROJECT_ID, ProjectId.create())
  }

  override fun <T : Any> findConstructorAndInstantiateClass(lookup: MethodHandles.Lookup, aClass: Class<T>): T {
    @Suppress("UNCHECKED_CAST")
    // see ConfigurableEP - prefer constructor that accepts our instance
    return (lookup.findConstructorOrNull(aClass, projectMethodType)?.invoke(actualContainerInstance)
            ?: lookup.findConstructorOrNull(aClass, emptyConstructorMethodType)?.invoke()
            ?: lookup.findConstructorOrNull(aClass, projectAndScopeMethodType)?.invoke(this, instanceCoroutineScope(aClass))
            ?: lookup.findConstructorOrNull(aClass, coroutineScopeMethodType)?.invoke(instanceCoroutineScope(aClass))
            ?: throw RuntimeException("Cannot find suitable constructor, expected (Project) or ()")) as T
  }

  override val supportedSignaturesOfLightServiceConstructors: List<MethodType> = java.util.List.of(
    projectMethodType,
    emptyConstructorMethodType,
    projectAndScopeMethodType,
    coroutineScopeMethodType,
  )

  override fun dispose() {
    super.dispose()
    // possibly re-enable "the only project" optimization since we have closed the extra project.
    (ProjectManager.getInstance() as ProjectManagerImpl).updateTheOnlyProjectField();
  }

  override fun isParentLazyListenersIgnored(): Boolean = true

  override fun isDefault(): Boolean = true

  // no startup activities, never opened
  override fun isInitialized(): Boolean = true

  override fun activityNamePrefix(): String? {
    // exclude from measurement because default project initialization is not a sequential activity
    // (so, complicates timeline because not applicable)
    // for now we don't measure default project initialization at all, because it takes only ~10 ms
    return null
  }

  override fun isComponentSuitable(componentConfig: ComponentConfig): Boolean {
    return componentConfig.loadForDefaultProject && super.isComponentSuitable(componentConfig)
  }

  fun init() {
    // do not leak internal delegate, use DefaultProject everywhere instead
    registerServiceInstance(Project::class.java, actualContainerInstance, fakeCorePluginDescriptor)
    registerComponents()
    @Suppress("DEPRECATION")
    createComponents()
    Disposer.register(actualContainerInstance, this)
  }

  override fun toString() = "Project${if (isDisposed()) " (Disposed)" else ""}$TEMPLATE_PROJECT_NAME"

  override fun equals(other: Any?): Boolean = other is Project && other.isDefault

  override fun hashCode(): Int = DEFAULT_HASH_CODE

  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor {
    return pluginDescriptor.projectContainerDescriptor
  }

  override fun getName(): String = TEMPLATE_PROJECT_NAME

  @Suppress("OVERRIDE_DEPRECATION")
  override fun getBaseDir(): VirtualFile? = null

  override fun getBasePath(): @SystemIndependent String? = null

  override fun getProjectFile(): VirtualFile? = null

  override fun getProjectFilePath(): @SystemIndependent String? = null

  override fun getWorkspaceFile(): VirtualFile? = null

  override fun getLocationHash(): String = Integer.toHexString(TEMPLATE_PROJECT_NAME.hashCode())

  override fun save() {
    LOG.error("Do not call save for default project")
    if (ApplicationManagerEx.getApplicationEx().isSaveAllowed()) {
      // no need to save
      saveSettings(this, false)
    }
  }

  override fun isOpen(): Boolean = false
}
