// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings

import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.ide.plugins.PluginPermissionRequest
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * A plugin management operation that the user allows.
 *
 * [com.intellij.ide.plugins.PluginPermissionService.withPermission] gives this object to the action of the requesting plugin.
 * The object is valid for one call only.
 */
@ApiStatus.Experimental
fun interface PluginManagementAction {
  /**
   * Starts the operation.
   *
   * The function returns before the operation completes.
   * The operation can ask the user for more input or for an IDE restart.
   *
   * You can call this function only once.
   * To do the operation again, send a new request.
   *
   * @throws com.intellij.ide.plugins.PluginPermissionNotGrantedException when you call this function a second time.
   */
  fun apply()
}

/**
 * Asks the user to allow the installation of the plugin [pluginId].
 *
 * @param message the reason for the request that the user sees in the permission dialog.
 */
@ApiStatus.Experimental
class InstallPluginRequest(
  val pluginId: PluginId,
  val message: @Nls String,
) : PluginPermissionRequest<PluginManagementAction>

/**
 * Asks the user to allow the requesting plugin to disable the plugin [pluginId].
 *
 * @param message the reason for the request that the user sees in the permission dialog.
 */
@ApiStatus.Experimental
class DisablePluginRequest(
  val pluginId: PluginId,
  val message: @Nls String,
) : PluginPermissionRequest<PluginManagementAction>

/**
 * Asks the user to allow the requesting plugin to enable the installed plugin [pluginId].
 *
 * @param message the reason for the request that the user sees in the permission dialog.
 */
@ApiStatus.Experimental
class EnablePluginRequest(
  val pluginId: PluginId,
  val message: @Nls String,
) : PluginPermissionRequest<PluginManagementAction>

/**
 * Gives access to the descriptors of the IDE plugins and to their class loaders.
 *
 * [com.intellij.ide.plugins.PluginPermissionService.withPermission] gives this object to the action of the requesting plugin.
 * The object is valid for one call only.
 */
@ApiStatus.Experimental
fun interface ReadPluginDescriptorsAction {
  /**
   * Returns the descriptors of all installed plugins, which includes the disabled plugins.
   *
   * Only a loaded plugin has a [PluginDescriptor.getPluginClassLoader].
   *
   * You can call this function only once.
   * To read the set of plugins again, send a new request.
   *
   * @throws com.intellij.ide.plugins.PluginPermissionNotGrantedException when you call this function a second time.
   */
  fun getPluginDescriptors(): List<PluginDescriptor>
}

/**
 * Asks the user to allow the requesting plugin to read the set of IDE plugins and to use their class loaders.
 *
 * A plugin must use this request when it needs to examine other plugins.
 * The class loaders let the requesting plugin load and run the code of other plugins.
 * Thus, the user must trust the requesting plugin.
 *
 * @param message the reason for the request that the user sees in the permission dialog.
 */
@ApiStatus.Experimental
class AccessPluginClassLoadersRequest(
  val message: @Nls String,
) : PluginPermissionRequest<ReadPluginDescriptorsAction>