// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community.events

import com.intellij.ae.database.activities.WritableDatabaseBackedCounterUserActivity
import com.intellij.ae.database.baseEvents.fus.FusEventCatcher
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.time.Duration.Companion.minutes

// todo record plugins during shutdown that were not recorded

/**
 * Plugin is considered to be installed if it wasn't uninstalled in 2 minutes after installation.
 * Plugin updates are ignored
 */
object PluginsInstalledUserActivity : WritableDatabaseBackedCounterUserActivity() {
  override val id: String
    get() = "plugin.installed"

  internal suspend fun writeInstallation(pluginId: String, time: Instant) {
    // todo: record plugin id, but not important for now
    submit(1, time)
  }
}

internal class PluginInstalledFusListener : FusEventCatcher(), FusEventCatcher.Factory {
  override fun getInstance() = this

  override fun define() = definition("plugin.installed") {
    event("plugin.manager", "plugin.installation.finished")
  }

  override suspend fun onEvent(fields: Map<String, Any>, eventTime: Instant) {
    val pluginId = fields["plugin"] as? String ?: return
    PluginInstalledUserActivityService.getInstanceAsync().pluginInstalledDoIfNotUninstalled(pluginId) {
      PluginsInstalledUserActivity.writeInstallation(it, eventTime)
    }
  }
}

internal class PluginUninstalledFusListener : FusEventCatcher(), FusEventCatcher.Factory {
  override fun getInstance() = this

  override fun define() = definition("plugin.uninstalled") {
    event("plugin.manager", "plugin.was.removed")
  }

  override suspend fun onEvent(fields: Map<String, Any>, eventTime: Instant) {
    val pluginId = fields["plugin"] as? String ?: return
    PluginInstalledUserActivityService.getInstanceAsync().pluginUninstalled(pluginId)
  }
}

@Service
internal class PluginInstalledUserActivityService(private val cs: CoroutineScope) {
  companion object {
    fun getInstance() = service<PluginInstalledUserActivityService>()
    suspend fun getInstanceAsync() = serviceAsync<PluginInstalledUserActivityService>()
  }

  private val installedPluginsIds = mutableSetOf<String>()

  fun addAllPlugins() {
    installedPluginsIds.addAll(PluginManager.getPlugins().asSequence()
                                 .map { it.pluginId.idString }.toMutableSet())
  }

  /**
   * Performs action if plugin was installed and wasn't uninstalled in two minutes
   */
  fun pluginInstalledDoIfNotUninstalled(id: String, action: suspend CoroutineScope.(String) -> Unit) {
    if (!installedPluginsIds.add(id)) {
      return
    }

    cs.launch {
      delay(2.minutes)
      if (!installedPluginsIds.contains(id)) {
        return@launch
      }
      action(id)
    }
  }

  fun pluginUninstalled(id: String) {
    installedPluginsIds.remove(id)
  }
}

internal class PluginInstalledUserActivityServiceInitializer : AppLifecycleListener {
  override fun appFrameCreated(commandLineArgs: MutableList<String>) {
    PluginInstalledUserActivityService.getInstance().addAllPlugins()
  }
}