// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("PluginsAdvertiser")

package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginNode
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.PlatformUtils.isIdeaUltimate
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

private const val IGNORE_ULTIMATE_EDITION = "ignoreUltimateEdition"

@get:JvmName("getLog")
internal val LOG = Logger.getInstance("#PluginsAdvertiser")

private val propertiesComponent
  get() = PropertiesComponent.getInstance()

var isIgnoreIdeSuggestion: Boolean
  get() = propertiesComponent.isTrueValue(IGNORE_ULTIMATE_EDITION)
  set(value) = propertiesComponent.setValue(IGNORE_ULTIMATE_EDITION, value)

@JvmField
@ApiStatus.ScheduledForRemoval
@Deprecated("Use `notificationGroup` property")
val NOTIFICATION_GROUP = notificationGroup

val notificationGroup: NotificationGroup
  get() = NotificationGroupManager.getInstance().getNotificationGroup("Plugins Suggestion")

@Suppress("DeprecatedCallableAddReplaceWith")
@Deprecated("Use `installAndEnable(Project, Set, Boolean, Runnable)`")
fun installAndEnablePlugins(
  pluginIds: Set<String>,
  onSuccess: Runnable,
) {
  installAndEnable(
    LinkedHashSet(pluginIds.map { PluginId.getId(it) }),
    onSuccess,
  )
}

@Deprecated("Use `installAndEnable(Project, Set, Boolean, Runnable)`")
fun installAndEnable(
  pluginIds: Set<PluginId>,
  onSuccess: Runnable,
) = installAndEnable(null, pluginIds, true, false, null, onSuccess)

@JvmOverloads
fun installAndEnable(
  project: Project?,
  pluginIds: Set<PluginId>,
  showDialog: Boolean = false,
  selectAlInDialog: Boolean = false,
  modalityState: ModalityState? = null,
  onSuccess: Runnable,
) {
  require(!showDialog || modalityState == null) {
    "`modalityState` can be not null only if plugin installation won't show the dialog"
  }
  ProgressManager.getInstance().run(InstallAndEnableTask(project, pluginIds, showDialog, selectAlInDialog, modalityState, onSuccess))
}


internal fun getBundledPluginToInstall(plugins: Collection<PluginData>): List<String> {
  return if (isIdeaUltimate()) {
    emptyList()
  }
  else {
    val descriptorsById = PluginManagerCore.buildPluginIdMap()
    plugins
      .filter { it.isBundled }
      .filterNot { descriptorsById.containsKey(it.pluginId) }
      .map { it.pluginName }
  }
}

/**
 * Loads list of plugins, compatible with a current build, from all configured repositories
 */
@JvmOverloads
internal fun loadPluginsFromCustomRepositories(indicator: ProgressIndicator? = null): List<PluginNode> {
  return RepositoryHelper
    .getPluginHosts()
    .filterNot {
      it == null
      && ApplicationInfoEx.getInstanceEx().usesJetBrainsPluginRepository()
    }.flatMap {
      try {
        RepositoryHelper.loadPlugins(it, null, indicator)
      }
      catch (e: IOException) {
        LOG.info("Couldn't load plugins from $it: $e")
        LOG.debug(e)
        emptyList<PluginNode>()
      }
    }.distinctBy { it.pluginId }
}
