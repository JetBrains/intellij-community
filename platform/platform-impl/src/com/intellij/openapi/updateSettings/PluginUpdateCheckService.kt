// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.updateSettings.impl.UpdateCheckerFacade
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@Service(Service.Level.APP)
class PluginUpdateCheckService {
  companion object {
    @JvmStatic
    fun getInstance(): PluginUpdateCheckService = service()
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  fun getPluginUpdate(pluginId: PluginId, indicator: ProgressIndicator? = null): PluginUpdateInfo {
    ThreadingAssertions.assertBackgroundThread()
    ThreadingAssertions.assertNoOwnReadAccess()

    val result = UpdateCheckerFacade.getInstance().getPluginUpdates(listOf(pluginId), indicator = indicator)
    val updates = result.pluginUpdates.allEnabled
    if (updates.size > 1) {
      thisLogger().error("There are ${updates.size} plugin updates returned for a single plugin request")
      return PluginUpdateInfo.NoUpdate()
    }

    if (updates.isNotEmpty()) {
      if (result.errors.isNotEmpty()) {
        thisLogger().warn("Ignored failures on update check because an update found: ${result.errors}")
      }

      return PluginUpdateInfo.UpdateAvailable(updates.first())
    }

    if (result.errors.isNotEmpty()) {
      return PluginUpdateInfo.CheckFailed(result.errors)
    }

    return PluginUpdateInfo.NoUpdate()
  }
}