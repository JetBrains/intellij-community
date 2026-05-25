// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.ide.plugins.PluginInitializationErrorHandler
import com.intellij.ide.plugins.PluginInitializationErrors
import com.intellij.platform.pluginManager.shared.rpc.PluginErrorReporterApi

internal class BackendPluginErrorReporterApi() : PluginErrorReporterApi {

  override suspend fun getPluginInitializationErrors(): PluginInitializationErrors {
    val allPluginErrors = mutableSetOf<String>()
    val allPluginNamesToEnable = mutableSetOf<String>()
    val allPluginNamesToDisable = mutableSetOf<String>()

    handlers().forEach { handler ->
      val errors = handler.getPluginInitializationErrors()
      allPluginErrors.addAll(errors.pluginErrors)
      allPluginNamesToEnable.addAll(errors.pluginNamesToEnable)
      allPluginNamesToDisable.addAll(errors.pluginNamesToDisable)
    }

    return PluginInitializationErrors(
      pluginErrors = allPluginErrors,
      pluginNamesToEnable = allPluginNamesToEnable,
      pluginNamesToDisable = allPluginNamesToDisable
    )
  }

  override suspend fun enableDeferredPlugins() {
    handlers().forEach { it.enableDeferredPlugins() }
  }

  override suspend fun disableDeferredPlugins() {
    handlers().forEach { it.disableDeferredPlugins() }
  }

  private fun handlers() = PluginInitializationErrorHandler.getInstances()
}
