// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.configurationStore.StoreUtil.saveSettings
import com.intellij.diagnostic.ActivityCategory
import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationManagerEx
import com.intellij.openapi.client.ClientAwareComponentManager
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.components.ComponentConfig
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionsArea
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.DefaultProjectImpl
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.util.messages.MessageBus
import com.intellij.util.namedChildScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.SystemIndependent
import org.jetbrains.annotations.TestOnly
import kotlin.coroutines.EmptyCoroutineContext

internal class DefaultProject : UserDataHolderBase(), Project {
  private val myDelegate: DefaultProjectTimed = object : DefaultProjectTimed(this) {
    public override fun compute(): Project {
      LOG.assertTrue(!ApplicationManager.getApplication().isDisposed(), "Application is being disposed!")
      val project = DefaultProjectImpl(this@DefaultProject)
      val componentStoreFactory = ApplicationManager.getApplication().getService(
        ProjectStoreFactory::class.java)
      project.registerServiceInstance(IComponentStore::class.java, componentStoreFactory.createDefaultProjectStore(project),
                                      ComponentManagerImpl.fakeCorePluginDescriptor)

      // mark myDelegate as not disposed if someone cluelessly did Disposer.dispose(getDefaultProject())
      Disposer.register(this@DefaultProject, this)
      return project
    }

    public override fun init(project: Project) {
      (project as DefaultProjectImpl).init()
    }
  }

  override fun getActualComponentManager(): ComponentManager {
    return delegate
  }

  override fun <T> instantiateClass(aClass: Class<T>, pluginId: PluginId): T {
    return delegate.instantiateClass(aClass, pluginId)
  }

  override fun <T> instantiateClass(className: String, pluginDescriptor: PluginDescriptor): T {
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

  @Throws(ClassNotFoundException::class)
  override fun <T> loadClass(className: String, pluginDescriptor: PluginDescriptor): Class<T> {
    return delegate.loadClass(className, pluginDescriptor)
  }

  override fun logError(error: Throwable, pluginId: PluginId) {
    delegate.logError(error, pluginId)
  }

  override fun createError(error: Throwable, pluginId: PluginId): RuntimeException {
    return delegate.createError(error, pluginId)
  }

  override fun hasComponent(interfaceClass: Class<*>): Boolean {
    return delegate.hasComponent(interfaceClass)
  }

  // make default project facade equal to any other default project facade
  // to enable Map<Project, T>
  override fun equals(o: Any?): Boolean {
    return o is Project && o.isDefault
  }

  override fun hashCode(): Int {
    return DefaultProjectImpl.DEFAULT_HASH_CODE
  }

  override fun toString(): String {
    return "Project" + (if (isDisposed) " (Disposed)" else "") + DefaultProjectImpl.TEMPLATE_PROJECT_NAME
  }

  override fun dispose() {
    if (!ApplicationManager.getApplication().isDisposed()) {
      throw IllegalStateException("Must not dispose default project")
    }
    Disposer.dispose(myDelegate)
  }

  @TestOnly
  fun disposeDefaultProjectAndCleanupComponentsForDynamicPluginTests() {
    ApplicationManager.getApplication().runWriteAction(
      Runnable { Disposer.dispose(myDelegate) })
  }

  private val delegate: Project
    private get() = myDelegate.get()
  val isCached: Boolean
    get() = myDelegate.isCached

  // delegates
  override fun getName(): String {
    return DefaultProjectImpl.TEMPLATE_PROJECT_NAME
  }

  @Deprecated("")
  override fun getBaseDir(): VirtualFile {
    return null
  }

  override fun getBasePath(): @SystemIndependent String? {
    return null
  }

  override fun getProjectFile(): VirtualFile? {
    return null
  }

  override fun getProjectFilePath(): @SystemIndependent String? {
    return null
  }

  override fun getWorkspaceFile(): VirtualFile? {
    return null
  }

  override fun getLocationHash(): String {
    return name
  }

  override fun save() {
    delegate.save()
  }

  override fun isOpen(): Boolean {
    return false
  }

  override fun isInitialized(): Boolean {
    return true
  }

  override fun isDefault(): Boolean {
    return true
  }

  override fun getCoroutineScope(): CoroutineScope {
    return ApplicationManager.getApplication().getCoroutineScope()
  }

  @Deprecated("")
  override fun getComponent(name: String): BaseComponent? {
    return delegate.getComponent(name)
  }

  override fun getActivityCategory(isExtension: Boolean): ActivityCategory {
    return if (isExtension) ActivityCategory.PROJECT_EXTENSION else ActivityCategory.PROJECT_SERVICE
  }

  override fun <T> getService(serviceClass: Class<T>): T {
    return delegate.getService(serviceClass)
  }

  override fun <T> getServiceIfCreated(serviceClass: Class<T>): T? {
    return delegate.getServiceIfCreated(serviceClass)
  }

  override fun <T> getComponent(interfaceClass: Class<T>): T {
    return delegate.getComponent(interfaceClass)
  }

  override fun isInjectionForExtensionSupported(): Boolean {
    return true
  }

  override fun getExtensionArea(): ExtensionsArea {
    return delegate.getExtensionArea()
  }

  override fun getMessageBus(): MessageBus {
    return delegate.getMessageBus()
  }

  override fun isDisposed(): Boolean {
    return ApplicationManager.getApplication().isDisposed()
  }

  override fun getDisposed(): Condition<*> {
    return ApplicationManager.getApplication().getDisposed()
  }

  companion object {
    private val LOG = Logger.getInstance(DefaultProject::class.java)
  }
}

internal class DefaultProjectImpl(private val actualContainerInstance: Project) : ClientAwareComponentManager(
  ApplicationManager.getApplication() as ComponentManagerImpl,
  (ApplicationManager.getApplication() as ComponentManagerImpl).getCoroutineScope().namedChildScope("DefaultProjectImpl",
                                                                                                    EmptyCoroutineContext, true),
  false), Project {
  override fun isParentLazyListenersIgnored(): Boolean {
    return true
  }

  override fun isDefault(): Boolean {
    return true
  }

  override fun isInitialized(): Boolean {
    return true // no startup activities, never opened
  }

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
    createComponents()
    Disposer.register(actualContainerInstance, this)
  }

  override fun toString(): String {
    return "Project" + (if (isDisposed()) " (Disposed)" else "") + TEMPLATE_PROJECT_NAME
  }

  override fun equals(o: Any?): Boolean {
    return o is Project && o.isDefault
  }

  override fun hashCode(): Int {
    return DEFAULT_HASH_CODE
  }

  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor {
    return pluginDescriptor.projectContainerDescriptor
  }

  override fun getName(): String {
    return TEMPLATE_PROJECT_NAME
  }

  override fun getBaseDir(): VirtualFile {
    return null
  }

  override fun getBasePath(): @SystemIndependent String? {
    return null
  }

  override fun getProjectFile(): VirtualFile? {
    return null
  }

  override fun getProjectFilePath(): @SystemIndependent String? {
    return null
  }

  override fun getWorkspaceFile(): VirtualFile? {
    return null
  }

  override fun getLocationHash(): String {
    return Integer.toHexString(TEMPLATE_PROJECT_NAME.hashCode())
  }

  override fun save() {
    LOG.error("Do not call save for default project")
    if (ApplicationManagerEx.getApplicationEx().isSaveAllowed()) {
      // no need to save
      saveSettings(this, false)
    }
  }

  override fun isOpen(): Boolean {
    return false
  }

  companion object {
    private val LOG = Logger.getInstance(DefaultProjectImpl::class.java)
    const val TEMPLATE_PROJECT_NAME: String = "Default (Template) Project"

    // chosen by fair dice roll. guaranteed to be random. see https://xkcd.com/221/ for details.
    const val DEFAULT_HASH_CODE: Int = 4
  }
}
