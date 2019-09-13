// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.diagnostic.ActivityCategory
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.BaseComponent
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer

internal class MyComponentAdapter(private val componentKey: Class<*>,
                                  override val implementationClassName: String,
                                  pluginDescriptor: PluginDescriptor,
                                  componentManager: PlatformComponentManagerImpl,
                                  implementationClass: Class<*>?,
                                  val isWorkspaceComponent: Boolean = false) : BaseComponentAdapter(componentManager, pluginDescriptor, null, implementationClass) {
  override fun getComponentKey() = componentKey

  override fun getActivityCategory(componentManager: PlatformComponentManagerImpl): ActivityCategory? {
    if (componentManager.activityNamePrefix() == null) {
      return null
    }

    val parent = componentManager.picoContainer.parent
    return when {
      parent == null -> ActivityCategory.APP_COMPONENT
      parent.parent == null -> ActivityCategory.PROJECT_COMPONENT
      else -> ActivityCategory.MODULE_COMPONENT
    }
  }

  override fun <T : Any> doCreateInstance(componentManager: PlatformComponentManagerImpl, implementationClass: Class<T>, indicator: ProgressIndicator?): T {
    try {
      val instance = componentManager.instantiateClassWithConstructorInjection(implementationClass, componentKey, pluginId)
      if (instance is Disposable) {
        Disposer.register(componentManager, instance)
      }
      componentManager.registerComponentInstance(instance, indicator)
      componentManager.initializeComponent(instance, null)
      if (instance is BaseComponent) {
        (instance as BaseComponent).initComponent()
      }
      return instance
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (t: Throwable) {
      componentManager.handleInitComponentError(t, getComponentKey().name, pluginId)
      throw t
    }
  }

  override fun toString() = "ComponentAdapter(key=${getComponentKey()}, implementation=${componentImplementation}, plugin=$pluginId)"
}