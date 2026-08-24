// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.pluginManager.backend.rpc

import com.intellij.ide.plugins.PluginLoadingProblemsNotificationData
import com.intellij.ide.plugins.PluginLoadingProblemsNotificationHandler
import com.intellij.platform.pluginManager.shared.rpc.PluginLoadingProblemsNotificationHandlerApi

internal class BackendPluginProblemsNotificationReporterApi : PluginLoadingProblemsNotificationHandlerApi {

  override suspend fun getPluginLoadingProblemsForNotification(): PluginLoadingProblemsNotificationData {
    val allPluginErrors = mutableSetOf<String>()
    val allPluginNamesToEnable = mutableSetOf<String>()
    val allPluginNamesToDisable = mutableSetOf<String>()

    handlers().forEach { handler ->
      val errors = handler.getPluginLoadingProblemsForNotification()
      allPluginErrors.addAll(errors.pluginLoadingProblemsForNotification)
      allPluginNamesToEnable.addAll(errors.pluginNamesToEnable)
      allPluginNamesToDisable.addAll(errors.pluginNamesToDisable)
    }

    return PluginLoadingProblemsNotificationData(
      pluginLoadingProblemsForNotification = allPluginErrors,
      pluginNamesToEnable = allPluginNamesToEnable,
      pluginNamesToDisable = allPluginNamesToDisable
    )
  }

  override suspend fun onEnableClickInNotification() {
    handlers().forEach { it.onEnableClickInNotification() }
  }

  override suspend fun onDisableClickInNotification() {
    handlers().forEach { it.onDisableClickInNotification() }
  }

  private fun handlers() = PluginLoadingProblemsNotificationHandler.getInstances()
}
