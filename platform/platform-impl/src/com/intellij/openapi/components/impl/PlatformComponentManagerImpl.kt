// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl

import com.intellij.diagnostic.LoadingPhase
import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.messages.MessageBusFactory

abstract class PlatformComponentManagerImpl : ComponentManagerImpl {
  private var handlingInitComponentError = false

  private val componentStore: IComponentStore
    get() = this.stateStore

  protected constructor(parent: ComponentManager?) : super(parent)

  protected constructor(parent: ComponentManager?, name: String) : super(parent, name)

  override fun handleInitComponentError(t: Throwable, componentClassName: String, pluginId: PluginId) {
    if (!handlingInitComponentError) {
      handlingInitComponentError = true
      try {
        PluginManager.handleComponentError(t, componentClassName, pluginId)
      }
      finally {
        handlingInitComponentError = false
      }
    }
  }

  override fun initializeComponent(component: Any, serviceDescriptor: ServiceDescriptor?) {
    if (serviceDescriptor == null || !(component is PathMacroManager || component is IComponentStore || component is MessageBusFactory)) {
      LoadingPhase.assertAtLeast(LoadingPhase.CONFIGURATION_STORE_INITIALIZED)
      componentStore.initComponent(component, serviceDescriptor)
    }
  }

  override fun registerServices(pluginDescriptor: IdeaPluginDescriptor) {
    ServiceManagerImpl.registerServices(getContainerDescriptor(pluginDescriptor as IdeaPluginDescriptorImpl).services, pluginDescriptor,
                                        this)
  }

  protected abstract fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor
}
