// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community

import com.intellij.ae.database.core.activities.WritableDatabaseBackedCounterUserActivity
import com.intellij.ae.database.core.baseEvents.fus.FusEventCatcher
import com.intellij.ae.database.core.runUpdateEvent
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Plugin is considered to be installed if it wasn't uninstalled in 10 minutes after installation.
 * Plugin updates are ignored
 */
object PluginsInstalledCounterUserActivity : WritableDatabaseBackedCounterUserActivity() {
  override val id: String
    get() = "plugin.installed"


  internal suspend fun writeInstallation(pluginId: String, time: Instant) {
    val installedPlugins = InstalledPluginsPersistentState.getInstanceAsync()
    if (!InstalledPluginsState.getInstance().wasUpdated(PluginId.getId(pluginId)) && installedPlugins.add(pluginId)) {
      submit(1, time)
    }
    else {
      installedPlugins.removeUpdated(pluginId)
    }
  }

  internal suspend fun writeUninstallation(pluginId: String) {
    val installedPlugins = InstalledPluginsPersistentState.getInstanceAsync()
    if (installedPlugins.remove(pluginId)) {
      submit(-1)
    }
  }
}

@Service
@State(name = "aeInstalledPlugins", storages = [(Storage("aeInstalledPlugins.xml", roamingType = RoamingType.DISABLED))])
class InstalledPluginsPersistentState : PersistentStateComponentWithModificationTracker<InstalledPluginsPersistentState.State> {
  companion object {
    fun getInstance() = service<InstalledPluginsPersistentState>()
    suspend fun getInstanceAsync() = serviceAsync<InstalledPluginsPersistentState>()
  }
  data class State(var myPlugins: Map<String, Long> = HashMap())

  private val PLUGIN_INSTALL_TTL_MS = 10 * 60 * 1000

  private val installedPlugins = ConcurrentHashMap<String, Long>()
  private val updatedPlugins = ConcurrentHashMap.newKeySet<String>()
  private val modTracker = AtomicLong(0)

  override fun getState() = State(installedPlugins.filterValues { System.currentTimeMillis() - it < PLUGIN_INSTALL_TTL_MS })

  override fun getStateModificationCount() = modTracker.get()

  override fun loadState(state: State) {
    for ((k, v) in state.myPlugins) {
      installedPlugins[k] = v
    }
  }

  fun add(s: String): Boolean {
    val installedPluginContains = installedPlugins[s]?.let { System.currentTimeMillis() - it < PLUGIN_INSTALL_TTL_MS } == true
    if (installedPluginContains || updatedPlugins.contains(s)) {
      return false
    }
    modTracker.incrementAndGet()
    installedPlugins[s] = System.currentTimeMillis()

    return true
  }

  fun addUpdated(s: String) {
    updatedPlugins.add(s)
  }

  fun removeUpdated(s: String) {
    updatedPlugins.remove(s)
  }

  // plugin will be recorded as removed if it was removed within [HALF_HOUR_MS] milliseconds
  fun remove(s: String): Boolean {
    modTracker.incrementAndGet()
    val currTime = System.currentTimeMillis()
    updatedPlugins.remove(s)

    return installedPlugins.remove(s)?.let { currTime - it < PLUGIN_INSTALL_TTL_MS } == true
  }
}

internal class MyDynamicPluginListener : DynamicPluginListener {
  // we don't want to treat theme updates as new installations, workaround for IDEA-342821
  override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    if (isUpdate) {
      InstalledPluginsPersistentState.getInstance().addUpdated(pluginDescriptor.pluginId.idString)
    }
  }
}

internal class PluginInstalledFusListener : FusEventCatcher(), FusEventCatcher.Factory {
  override fun getInstance() = this

  override fun define() = definition("plugin.installed") {
    event("plugin.manager", "plugin.installation.finished")
  }

  override suspend fun onEvent(fields: Map<String, Any>, eventTime: Instant) {
    val pluginId = fields["plugin"] as? String ?: return
    logger.info("Plugin installed: ${pluginId}.")

    FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(PluginsInstalledCounterUserActivity) {
      it.writeInstallation(pluginId, eventTime)
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
    logger.info("Plugin uninstalled: ${pluginId}.")

    FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(PluginsInstalledCounterUserActivity) {
      it.writeUninstallation(pluginId)
    }
  }
}

private val logger = logger<PluginsInstalledCounterUserActivity>()