// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.serviceContainer

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.ComponentManagerEx
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Disposer
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
fun ComponentManager.getComponentManagerEx(): ComponentManagerEx {
  return (this as ComponentManagerEx).getMutableComponentContainer() as ComponentManagerEx
}

internal fun checkCanceledIfNotInClassInit() {
  try {
    ProgressManager.checkCanceled()
  }
  catch (e: ProcessCanceledException) {
    // otherwise ExceptionInInitializerError happens and the class is screwed forever
    if (!e.stackTrace.any { it.methodName == "<clinit>" }) {
      throw e
    }
  }
}

internal fun isUnderIndicatorOrJob(): Boolean {
  @Suppress("UsagesOfObsoleteApi")
  return ProgressManager.getInstanceOrNull()?.getProgressIndicator() != null || Cancellation.currentJob() != null
}

@Internal
fun throwAlreadyDisposedError(serviceDescription: String, componentManager: ComponentManagerImpl) {
  throw AlreadyDisposedException("Cannot create $serviceDescription because container is already disposed (container=${componentManager})")
}

internal fun doNotUseConstructorInjectionsMessage(where: String): String {
  return "Please, do not use constructor injection: it slows down initialization and may lead to performance problems ($where). " +
         "See https://plugins.jetbrains.com/docs/intellij/plugin-services.html for details."
}

internal suspend fun initializeComponentOrLightService(component: Any, pluginId: PluginId, componentManager: ComponentManagerImpl) {
  if (component is Disposable) {
    Disposer.register(componentManager.serviceParentDisposable, component)
  }

  @Suppress("DEPRECATION")
  if (component is PersistentStateComponent<*> || component is SettingsSavingComponent || component is com.intellij.openapi.util.JDOMExternalizable) {
    val componentStore = componentManager.componentStore
    check(componentStore.isStoreInitialized || componentManager.getApplication()!!.isUnitTestMode) {
      "You cannot get $component before component store is initialized"
    }

    componentStore.initComponent(component = component, serviceDescriptor = null, pluginId = pluginId)
  }
}