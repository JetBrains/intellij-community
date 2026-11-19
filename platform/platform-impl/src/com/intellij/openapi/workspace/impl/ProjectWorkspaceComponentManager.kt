// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.workspace.impl

import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.workspace.ProjectWorkspace
import com.intellij.serviceContainer.ComponentManagerImpl
import com.intellij.serviceContainer.coroutineScopeMethodType
import com.intellij.serviceContainer.emptyConstructorMethodType
import com.intellij.serviceContainer.findConstructorOrNull
import com.intellij.serviceContainer.getComponentManagerImpl
import com.intellij.serviceContainer.newInstanceOrThrow
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodType
import java.lang.reflect.Constructor

private val workspaceMethodType: MethodType = MethodType.methodType(Void.TYPE, ProjectWorkspace::class.java)
private val workspaceAndScopeMethodType: MethodType =
  MethodType.methodType(Void.TYPE, ProjectWorkspace::class.java, CoroutineScope::class.java)

@Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
private val workspaceSupportedSignaturesOfLightServiceConstructors: List<MethodType> = java.util.List.of(
  workspaceMethodType, workspaceAndScopeMethodType, coroutineScopeMethodType, emptyConstructorMethodType,
)

private val LOG = logger<ProjectWorkspaceComponentManager>()

/**
 * The service container for a [ProjectWorkspace]. Its parent is the application container.
 */
@ApiStatus.Internal
class ProjectWorkspaceComponentManager(
  val hostProject: Project,
) : ComponentManagerImpl(ApplicationManager.getApplication().getComponentManagerImpl()) {
  private var workspace: ProjectWorkspace? = null

  override fun <T : Any> findConstructorAndInstantiateClass(constructors: Array<Constructor<*>>, aClass: Class<T>): T {
    @Suppress("UNCHECKED_CAST")
    return (
      constructors.findConstructorOrNull(workspaceMethodType)?.newInstanceOrThrow(workspace)
      ?: constructors.findConstructorOrNull(workspaceAndScopeMethodType)?.newInstanceOrThrow(workspace, instanceCoroutineScope(aClass))
      ?: constructors.findConstructorOrNull(coroutineScopeMethodType)?.newInstanceOrThrow(instanceCoroutineScope(aClass))
      ?: constructors.findConstructorOrNull(emptyConstructorMethodType)?.newInstanceOrThrow()
      ?: throw RuntimeException(
        "Cannot find a suitable constructor, expected (ProjectWorkspace), (ProjectWorkspace, CoroutineScope), (CoroutineScope), or ()"
      )
    ) as T
  }

  override val supportedSignaturesOfLightServiceConstructors: List<MethodType>
    get() = workspaceSupportedSignaturesOfLightServiceConstructors

  /** Registers [workspace] for constructor injection. */
  fun initForWorkspace(workspace: ProjectWorkspace) {
    this.workspace = workspace
    registerServiceInstance(serviceInterface = ProjectWorkspace::class.java, instance = workspace, pluginDescriptor = fakeCorePluginDescriptor)
  }

  /** Call once when [ProjectWorkspaceManagerImpl] starts the workspace. */
  fun registerWorkspaceComponents() {
    registerComponents()
    markContainerAsCreated()
  }

  override fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor {
    return pluginDescriptor.workspaceContainerDescriptor
  }

  /**
   * Workspace services use the state store of [hostProject]. Their state names must be unique across both containers.
   * They can access the store only after initialization and before the host disposes it.
   * The store does not support per-client workspace state or migrate state from an earlier [WorkspaceAsProject].
   *
   * Only the default project implementation that created the workspace saves its state.
   * Workspace services of the default project must finish using the store before disposal.
   */
  override val componentStore: IComponentStore
    get() = hostProject.stateStore

  override fun isServiceSuitable(descriptor: ServiceDescriptor): Boolean {
    if (descriptor.client != null) {
      LOG.error("The workspace service ${descriptor.implementation} is not registered: a workspace service has no `client` attribute")
      return false
    }
    return true
  }

  override fun debugString(short: Boolean): String {
    return if (short) javaClass.simpleName else "${super.debugString(short = false)} (host=$hostProject)"
  }
}
