// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ae.database.counters.community

import com.intellij.ae.database.core.activities.WritableDatabaseBackedCounterUserActivity
import com.intellij.ae.database.core.baseEvents.fus.FusEventCatcher
import com.intellij.ae.database.core.runUpdateEvent
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.openapi.extensions.PluginId
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Plugin is considered to be installed if it wasn't uninstalled in 2 minutes after installation.
 * Plugin updates are ignored
 */
object PluginsInstalledCounterUserActivity : WritableDatabaseBackedCounterUserActivity() {
  override val id: String
    get() = "plugin.installed"

  private val installedPlugins = ConcurrentHashMap.newKeySet<String>()

  internal suspend fun writeInstallation(pluginId: String, time: Instant) {
    if (!InstalledPluginsState.getInstance().wasUpdated(PluginId.getId(pluginId)) && !installedPlugins.contains(pluginId)) {
      installedPlugins.add(pluginId)
      submit(1, time)
    }
  }

  internal suspend fun writeUninstallation(pluginId: String) {
    if (installedPlugins.remove(pluginId)) {
      submit(-1)
    }
  }

  internal fun writeUpdate(id: String) {
    installedPlugins.add(id)
  }
}

internal class MyDynamicPluginListener : DynamicPluginListener {
  // we don't want to treat theme updates as new installations, workaround for IDEA-342821
  override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
    if (isUpdate) {
      PluginsInstalledCounterUserActivity.writeUpdate(pluginDescriptor.pluginId.idString)
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

    FeatureUsageDatabaseCountersScopeProvider.getScope().runUpdateEvent(PluginsInstalledCounterUserActivity) {
      it.writeUninstallation(pluginId)
    }
  }
}