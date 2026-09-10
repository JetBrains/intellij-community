// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus

/**
 * Runs the first update check when the IDE is ready for it.
 *
 * In the Light mode, the check waits until the applied plugin set leaves the Light mode.
 */
@ApiStatus.Internal
@Service(Service.Level.APP)
class UpdateCheckFirstRunDeferralRunner(private val scope: CoroutineScope) {
  fun runWhenLifted(action: Runnable) {
    scope.launch {
      awaitNotLightPluginSet()
      action.run()
    }
  }

  // Check the applied plugin set, and not only the mode value: an update check needs the modules of that mode,
  // and `DynamicPlugins.reconfigure` applies them after it moves the mode.
  private suspend fun awaitNotLightPluginSet() {
    val applied = CompletableDeferred<Unit>()
    val connection = ApplicationManager.getApplication().messageBus.connect()
    try {
      connection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun pluginsLoaded() {
          if (!isAppliedPluginSetLight()) {
            applied.complete(Unit)
          }
        }
      })
      if (isAppliedPluginSetLight()) {
        applied.await()
      }
    }
    finally {
      connection.disconnect()
    }
  }

  private fun isAppliedPluginSetLight(): Boolean = PluginManagerCore.getPluginSet().initContext.productMode.isLight

  companion object {
    @JvmStatic
    fun getInstance(): UpdateCheckFirstRunDeferralRunner = service()
  }
}
