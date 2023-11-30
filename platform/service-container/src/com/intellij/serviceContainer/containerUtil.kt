// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal
package com.intellij.serviceContainer

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.progress.Cancellation
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import org.jetbrains.annotations.ApiStatus

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

internal fun isGettingServiceAllowedDuringPluginUnloading(descriptor: PluginDescriptor): Boolean {
  return descriptor.isRequireRestart ||
         descriptor.pluginId == PluginManagerCore.CORE_ID || descriptor.pluginId == PluginManagerCore.JAVA_PLUGIN_ID
}

internal fun isUnderIndicatorOrJob(): Boolean {
  return ProgressIndicatorProvider.getGlobalProgressIndicator() != null || Cancellation.currentJob() != null
}

@ApiStatus.Internal
fun throwAlreadyDisposedError(serviceDescription: String, componentManager: ComponentManagerImpl) {
  val error = AlreadyDisposedException("Cannot create $serviceDescription because container is already disposed (container=${componentManager})")
  throw wrapAlreadyDisposedError(error)
}

/**
 * AlreadyDisposedException should cancel current computation -- if computation is cancellable.
 * Method checks if computation is cancellable, and returns ADE wrapped in PCE if true, or return
 * original ADE if computation is not cancellable -- so returned exception is appropriate to be
 * thrown in either context.
 */
@ApiStatus.Internal
fun wrapAlreadyDisposedError(error: AlreadyDisposedException): RuntimeException {
  if (!isUnderIndicatorOrJob()) {
    return error
  }
  else {
    return ProcessCanceledException(error)
  }
}

internal fun doNotUseConstructorInjectionsMessage(where: String): String {
  return "Please, do not use constructor injection: it slows down initialization and may lead to performance problems ($where). " +
         "See https://plugins.jetbrains.com/docs/intellij/plugin-services.html for details."
}