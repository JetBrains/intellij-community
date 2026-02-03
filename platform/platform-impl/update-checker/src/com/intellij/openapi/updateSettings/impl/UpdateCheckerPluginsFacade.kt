// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.RepositoryHelper
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.IntellijInternalApi
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

/**
 * Detaches [UpdateChecker] from a direct dependency on [PluginManagerCore].
 */
@ApiStatus.Internal
@IntellijInternalApi
interface UpdateCheckerPluginsFacade {
  companion object {
    fun getInstance(): UpdateCheckerPluginsFacade = service()
  }

  fun getPlugin(id: PluginId): IdeaPluginDescriptor?
  fun getInstalledPlugins(): Collection<IdeaPluginDescriptor>
  fun getOnceInstalledIfExists(): Path?

  fun isDisabled(id: PluginId): Boolean

  fun isCompatible(descriptor: IdeaPluginDescriptor, buildNumber: BuildNumber?): Boolean

  fun getPluginHosts(): List<String?>
}

internal class UpdateCheckerPluginsFacadeImpl : UpdateCheckerPluginsFacade {
  override fun getPlugin(id: PluginId): IdeaPluginDescriptor? = PluginManagerCore.findPlugin(id)
  override fun getInstalledPlugins(): Collection<IdeaPluginDescriptor> = PluginManagerCore.plugins.toList()
  override fun getOnceInstalledIfExists(): Path? = PluginManager.getOnceInstalledIfExists()
  override fun isDisabled(id: PluginId): Boolean = PluginManagerCore.isDisabled(id)
  override fun isCompatible(descriptor: IdeaPluginDescriptor, buildNumber: BuildNumber?): Boolean {
    return PluginManagerCore.isCompatible(descriptor, buildNumber)
  }
  override fun getPluginHosts(): List<String?> = RepositoryHelper.getPluginHosts()
}