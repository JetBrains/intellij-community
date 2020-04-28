// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer

import com.intellij.diagnostic.ActivityCategory
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer

internal class ServiceComponentAdapter(val descriptor: ServiceDescriptor,
                                       pluginDescriptor: PluginDescriptor,
                                       componentManager: ComponentManagerImpl,
                                       implementationClass: Class<*>? = null,
                                       initializedInstance: Any? = null) : BaseComponentAdapter(componentManager, pluginDescriptor, initializedInstance, implementationClass) {
  override val implementationClassName: String
    get() = descriptor.implementation!!

  override fun isImplementationEqualsToInterface() = descriptor.serviceInterface == null || descriptor.serviceInterface == descriptor.implementation

  override fun getComponentKey(): String = descriptor.getInterface()

  override fun getActivityCategory(componentManager: ComponentManagerImpl) = getServiceActivityCategory(componentManager)

  override fun <T : Any> doCreateInstance(componentManager: ComponentManagerImpl, implementationClass: Class<T>, indicator: ProgressIndicator?): T {
    if (LOG.isDebugEnabled) {
      val app = componentManager.getApplication()
      if (app != null && app.isWriteAccessAllowed && !app.isUnitTestMode &&
          PersistentStateComponent::class.java.isAssignableFrom(implementationClass)) {
        LOG.warn(Throwable("Getting service from write-action leads to possible deadlock. Service implementation $implementationClassName"))
      }
    }

    if (indicator == null) {
      return createAndInitialize(componentManager, implementationClass)
    }

    // don't use here computeInNonCancelableSection - it is kotlin and no need of such awkward and stack-trace unfriendly methods
    var instance: T? = null
    ProgressManager.getInstance().executeNonCancelableSection {
      instance = createAndInitialize(componentManager, implementationClass)
    }
    return instance!!
  }

  private fun <T : Any> createAndInitialize(componentManager: ComponentManagerImpl, implementationClass: Class<T>): T {
    val instance = componentManager.instantiateClassWithConstructorInjection(implementationClass, componentKey, pluginId)
    if (instance is Disposable) {
      Disposer.register(componentManager.serviceParentDisposable, instance)
    }
    componentManager.initializeComponent(instance, descriptor, pluginId)
    return instance
  }

  override fun toString() = "ServiceAdapter(descriptor=$descriptor, pluginDescriptor=$pluginDescriptor)"
}

internal fun getServiceActivityCategory(componentManager: ComponentManagerImpl): ActivityCategory {
  val parent = componentManager.picoContainer.parent
  return when {
    parent == null -> ActivityCategory.APP_SERVICE
    parent.parent == null -> ActivityCategory.PROJECT_SERVICE
    else -> ActivityCategory.MODULE_SERVICE
  }
}