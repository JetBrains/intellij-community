// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.InstalledPluginsState
import com.intellij.ide.plugins.marketplace.PluginUpdateActivity
import com.intellij.notification.NotificationGroup
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface UpdateCheckerFacade {
  companion object {
    const val MACHINE_ID_DISABLED_PROPERTY: String = "machine.id.disabled"

    @JvmStatic
    fun getInstance(): UpdateCheckerFacade = service()
  }

  val disabledToUpdate: Set<PluginId>

  fun updateAndShowResult()

  fun updateAndShowResult(project: Project?)

  fun getNotificationGroup(): NotificationGroup

  fun getNotificationGroupForPluginUpdateResults(): NotificationGroup

  fun getNotificationGroupForIdeUpdateResults(): NotificationGroup

  fun loadProductData(indicator: ProgressIndicator?): Product?

  fun updateDescriptorsForInstalledPlugins(state: InstalledPluginsState)

  /**
   * When [buildNumber] is null, returns new versions of plugins compatible with the current IDE version,
   * otherwise, returns versions compatible with the specified build.
   */
  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  @IntellijInternalApi
  @ApiStatus.Internal
  @Deprecated("Use [getPluginUpdates] instead", ReplaceWith("getPluginUpdates(pluginId, buildNumber, indicator)"))
  fun getInternalPluginUpdates(
    buildNumber: BuildNumber? = null,
    indicator: ProgressIndicator? = null,
    updateablePluginsMap: MutableMap<PluginId, IdeaPluginDescriptor?>? = null,
    activity: PluginUpdateActivity = PluginUpdateActivity.AVAILABLE_VERSIONS
  ): InternalPluginResults

  fun saveDisabledToUpdatePlugins()

  fun ignorePlugins(descriptors: List<IdeaPluginDescriptor>)
}