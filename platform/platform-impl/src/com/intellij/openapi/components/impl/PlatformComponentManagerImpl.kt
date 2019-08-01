// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components.impl

import com.intellij.diagnostic.LoadingPhase
import com.intellij.ide.plugins.ContainerDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.extensions.PluginId
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.messages.ListenerDescriptor
import com.intellij.util.messages.MessageBusFactory
import com.intellij.util.messages.impl.MessageBusImpl
import java.util.concurrent.ConcurrentMap

abstract class PlatformComponentManagerImpl : ComponentManagerImpl {
  private var handlingInitComponentError = false

  private val componentStore: IComponentStore
    get() = this.stateStore

  protected constructor(parent: ComponentManager?) : super(parent)

  protected constructor(parent: ComponentManager?, name: String) : super(parent, name)

  protected open fun registerComponents(plugins: List<IdeaPluginDescriptor>) {
    val app = ApplicationManager.getApplication()
    val headless = app == null || app.isHeadlessEnvironment

    var map: ConcurrentMap<String, MutableList<ListenerDescriptor>>? = null
    val isHeadlessMode = app.isHeadlessEnvironment
    val isUnitTestMode = app.isUnitTestMode

    var componentConfigCount = 0
    for (plugin in plugins) {
      val containerDescriptor = getContainerDescriptor(plugin as IdeaPluginDescriptorImpl)

      for (config in containerDescriptor.components) {
        if (!config.prepareClasses(headless)) {
          continue
        }

        if (isComponentSuitable(config)) {
          registerComponents(config, plugin)
          componentConfigCount++
        }
      }

      ServiceManagerImpl.registerServices(containerDescriptor.services, plugin, this)

      val listeners = containerDescriptor.listeners
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
    }

    myComponentConfigCount = componentConfigCount

    // app - phase must be set before getMessageBus()
    if (picoContainer.parent == null) {
      LoadingPhase.setCurrentPhase(LoadingPhase.COMPONENT_REGISTERED)
    }

    // ensure that messageBus is created, regardless of lazy listeners map state
    val messageBus = messageBus as MessageBusImpl
    if (map != null) {
      messageBus.setLazyListeners(map)
    }
  }

  override fun handleInitComponentError(t: Throwable, componentClassName: String, pluginId: PluginId) {
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

  override fun initializeComponent(component: Any, serviceDescriptor: ServiceDescriptor?) {
    if (serviceDescriptor == null || !(component is PathMacroManager || component is IComponentStore || component is MessageBusFactory)) {
      LoadingPhase.assertAtLeast(LoadingPhase.CONFIGURATION_STORE_INITIALIZED)
      componentStore.initComponent(component, serviceDescriptor)
    }
  }

  protected abstract fun getContainerDescriptor(pluginDescriptor: IdeaPluginDescriptorImpl): ContainerDescriptor
}
