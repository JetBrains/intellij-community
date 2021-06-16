// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:ApiStatus.Internal
package com.intellij.serviceContainer

import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.Service
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.annotations.ApiStatus
import java.lang.reflect.Modifier

internal fun checkCanceledIfNotInClassInit() {
  try {
    ProgressManager.checkCanceled()
  }
  catch (e: ProcessCanceledException) {
    // otherwise ExceptionInInitializerError happens and the class is screwed forever
    @Suppress("SpellCheckingInspection")
    if (!e.stackTrace.any { it.methodName == "<clinit>" }) {
      throw e
    }
  }
}

inline fun executeRegisterTask(mainPluginDescriptor: IdeaPluginDescriptorImpl,
                               crossinline task: (IdeaPluginDescriptorImpl) -> Unit) {
  task(mainPluginDescriptor)
  executeRegisterTaskForContent(mainPluginDescriptor) {
    task(it)
  }
}

internal fun isGettingServiceAllowedDuringPluginUnloading(descriptor: PluginDescriptor): Boolean {
  return descriptor.isRequireRestart ||
         descriptor.pluginId == PluginManagerCore.CORE_ID || descriptor.pluginId == PluginManagerCore.JAVA_PLUGIN_ID
}

@ApiStatus.Internal
fun throwAlreadyDisposedError(serviceDescription: String, componentManager: ComponentManagerImpl, indicator: ProgressIndicator?) {
  val error = AlreadyDisposedException("Cannot create $serviceDescription because container is already disposed (container=${componentManager})")
  if (indicator == null) {
    throw error
  }
  else {
    throw ProcessCanceledException(error)
  }
}

internal fun isLightService(serviceClass: Class<*>): Boolean {
  return Modifier.isFinal(serviceClass.modifiers) && serviceClass.isAnnotationPresent(Service::class.java)
}