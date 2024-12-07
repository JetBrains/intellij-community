// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("PluginsAdvertiser")

package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.advertiser.PluginData
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.util.PlatformUtils.isIdeaUltimate

private const val IGNORE_ULTIMATE_EDITION = "promo.ignore.suggested.ide"

internal var isIgnoreIdeSuggestion: Boolean
  get() = PropertiesComponent.getInstance().isTrueValue(IGNORE_ULTIMATE_EDITION)
  set(value) = PropertiesComponent.getInstance().setValue(IGNORE_ULTIMATE_EDITION, value)

@Deprecated("Use `getPluginSuggestionNotificationGroup()`")
val notificationGroup: NotificationGroup
  get() = getPluginSuggestionNotificationGroup()

fun getPluginSuggestionNotificationGroup(): NotificationGroup {
  return NotificationGroupManager.getInstance().getNotificationGroup("Plugins Suggestion")
}

@Suppress("DeprecatedCallableAddReplaceWith", "DEPRECATION")
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
): Unit = installAndEnable(null, pluginIds, true, false, null, onSuccess)

@JvmOverloads
fun installAndEnable(
  project: Project?,
  pluginIds: Set<PluginId>,
  showDialog: Boolean = false,
  selectAlInDialog: Boolean = false,
  modalityState: ModalityState? = null,
  onSuccess: Runnable,
) {
  ProgressManager.getInstance().run(getInstallAndEnableTask(project, pluginIds, showDialog, selectAlInDialog, modalityState, onSuccess))
}

@JvmOverloads
fun getInstallAndEnableTask(
  project: Project?,
  pluginIds: Set<PluginId>,
  showDialog: Boolean = false,
  selectAlInDialog: Boolean = false,
  modalityState: ModalityState? = null,
  onSuccess: Runnable,
): InstallAndEnableTask {
  require(!showDialog || modalityState == null) {
    "`modalityState` can be not null only if plugin installation won't show the dialog"
  }
  return InstallAndEnableTask(project, pluginIds, showDialog, selectAlInDialog, modalityState, onSuccess)
}

internal fun getBundledPluginToInstall(
  plugins: Collection<PluginData>,
  descriptorsById: Map<PluginId, IdeaPluginDescriptor> = PluginManagerCore.buildPluginIdMap(),
): List<String> {
  return if (isIdeaUltimate()) {
    emptyList()
  }
  else {
    plugins.filter { it.isBundled }
      .filterNot { descriptorsById.containsKey(it.pluginId) }
      .map { it.pluginName }
  }
}
