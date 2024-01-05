// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community

import com.intellij.ae.database.core.activities.WritableDatabaseBackedCounterUserActivity
import com.intellij.ae.database.core.baseEvents.fus.FusEventCatcher
import com.intellij.ae.database.core.dbs.counter.CounterUserActivityDatabase
import com.intellij.ae.database.core.runUpdateEvent
import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

// todo record plugins during shutdown that were not recorded

/**
 * Plugin is considered to be installed if it wasn't uninstalled in 2 minutes after installation.
 * Plugin updates are ignored
 */
object PluginsInstalledCounterUserActivity : WritableDatabaseBackedCounterUserActivity() {
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
    PluginInstalledUserActivityService.getInstanceAsync().pluginInstalled(pluginId, eventTime)
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

  private val mutex = Mutex()
  private val installedPluginsIds = mutableSetOf<String>()
  private val pluginsToReport = mutableMapOf<String, Instant>()

  init {
    CounterUserActivityDatabase.getInstance().executeBeforeConnectionClosed {
      cs.runUpdateEvent(PluginsInstalledCounterUserActivity) {
        mutex.withLock {
          for ((pluginId, installedAt) in pluginsToReport) {
            it.writeInstallation(pluginId, installedAt)
          }
          pluginsToReport.clear()
        }
      }
    }
  }

  fun addAllPlugins() {
    installedPluginsIds.addAll(PluginManager.getPlugins().asSequence()
                                 .map { it.pluginId.idString }.toMutableSet())
  }

  suspend fun pluginInstalled(id: String, installedAt: Instant) {
    // if a plugin is already installed and is installed again, it means it's an update, and we need to skip it
    mutex.withLock {
      if (!pluginsToReport.containsKey(id) && !installedPluginsIds.contains(id)) {
        pluginsToReport[id] = installedAt
        installedPluginsIds.add(id)
      }
    }
  }

  suspend fun pluginUninstalled(id: String) {
    mutex.withLock {
      installedPluginsIds.remove(id)
    }
    pluginsToReport.remove(id)
  }
}

internal class PluginInstalledUserActivityServiceInitializer : AppLifecycleListener {
  override fun appFrameCreated(commandLineArgs: MutableList<String>) {
    PluginInstalledUserActivityService.getInstance().addAllPlugins()
  }
}