// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.diagnostic.ActivityCategory
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import com.intellij.util.io.storage.HeavyProcessLatch
import com.intellij.util.pico.AssignableToComponentAdapter

internal class ServiceComponentAdapter(val descriptor: ServiceDescriptor,
                                       pluginDescriptor: PluginDescriptor,
                                       componentManager: PlatformComponentManagerImpl,
                                       implementationClass: Class<*>? = null,
                                       initializedInstance: Any? = null) : BaseComponentAdapter(componentManager, pluginDescriptor, initializedInstance, implementationClass), AssignableToComponentAdapter {
  override val implementationClassName: String
    get() = descriptor.implementation!!

  override fun getComponentKey(): String = descriptor.getInterface()

  override fun getActivityCategory(componentManager: PlatformComponentManagerImpl) = getServiceActivityCategory(componentManager)

  override fun <T : Any> doCreateInstance(componentManager: PlatformComponentManagerImpl, implementationClass: Class<T>, indicator: ProgressIndicator?): T {
    if (LOG.isDebugEnabled) {
      val app = componentManager.getApplication()
      if (app != null && app.isWriteAccessAllowed && !app.isUnitTestMode &&
          PersistentStateComponent::class.java.isAssignableFrom(implementationClass)) {
        LOG.warn(Throwable("Getting service from write-action leads to possible deadlock. Service implementation ${implementationClassName}"))
      }
    }

    // heavy to prevent storages from flushing and blocking FS
    HeavyProcessLatch.INSTANCE.processStarted(implementationClassName).use {
      if (ProgressManager.getGlobalProgressIndicator() == null) {
        return createAndInitialize(componentManager, implementationClass)
      }
      else {
        var instance: T? = null
        ProgressManager.getInstance().executeNonCancelableSection {
          instance = createAndInitialize(componentManager, implementationClass)
        }
        return instance!!
      }
    }
  }

  private fun <T : Any> createAndInitialize(componentManager: PlatformComponentManagerImpl, implementationClass: Class<T>): T {
    val instance = componentManager.instantiateClassWithConstructorInjection(implementationClass, componentKey, pluginId)
    if (instance is Disposable) {
      Disposer.register(componentManager, instance)
    }
    componentManager.initializeComponent(instance, descriptor)
    return instance
  }

  override fun getAssignableToClassName(): String = descriptor.getInterface()

  override fun toString() = "ServiceAdapter(descriptor=$descriptor, pluginDescriptor=$pluginDescriptor)"
}

internal fun getServiceActivityCategory(componentManager: PlatformComponentManagerImpl): ActivityCategory {
  val parent = componentManager.picoContainer.parent
  return when {
    parent == null -> ActivityCategory.APP_SERVICE
    parent.parent == null -> ActivityCategory.PROJECT_SERVICE
    else -> ActivityCategory.MODULE_SERVICE
  }
}