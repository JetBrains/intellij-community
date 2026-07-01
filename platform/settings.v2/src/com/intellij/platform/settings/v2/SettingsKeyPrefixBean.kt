// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2

// @spec ../../../../../../spec/setting-keys.spec.md

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.extensions.RequiredElement
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal

/**
 * Declares the prefix the keys of one plugin take on disk, instead of its plugin id. A plugin states it once:
 *
 * ```xml
 * <extensions defaultExtensionNs="com.intellij">
 *   <settings.v2.keyPrefix prefix="kotlin"/>
 * </extensions>
 * ```
 *
 * A declared prefix lets two plugins hold one identity for one setting, which a feature split over a frontend
 * plugin and a backend plugin needs. See [SettingsKey.identity].
 */
@Experimental
class SettingsKeyPrefixBean : PluginAware {
  /** Holds the prefix, without a trailing dot. */
  @Attribute("prefix")
  @RequiredElement
  var prefix: String? = null

  /** Holds the plugin that declared this prefix. The platform sets it. */
  var pluginDescriptor: PluginDescriptor? = null
    private set

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  companion object {
    @Internal
    val EP_NAME: ExtensionPointName<SettingsKeyPrefixBean> = ExtensionPointName("com.intellij.settings.v2.keyPrefix")

    /**
     * Returns the prefix that [pluginId] declared.
     *
     * @param pluginId the plugin that owns a key, or `null` for a platform-core key
     * @return the declared prefix, or `null` when the plugin declared none or the application does not exist yet
     */
    internal fun declaredPrefix(pluginId: PluginId?): String? {
      if (pluginId == null) return null
      // A key is often declared in a static initializer, which can run before the application exists.
      if (ApplicationManager.getApplication() == null) return null
      return EP_NAME.extensionList.firstOrNull { it.pluginDescriptor?.pluginId == pluginId }?.prefix
    }
  }
}
