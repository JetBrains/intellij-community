@file:OptIn(IntellijInternalApi::class)

package com.intellij.settingsSync.core.plugins

import com.intellij.ide.plugins.*
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.IntellijInternalApi

internal class CorePluginManagerProxy : AbstractPluginManagerProxy() {

  override val pluginEnabler: PluginEnabler
    get() = PluginEnabler.getInstance()

  override fun isDescriptorEssential(pluginId: PluginId): Boolean {
    return (ApplicationInfo.getInstance()).isEssentialPlugin(pluginId)
  }

  override fun getPlugins() = PluginManagerCore.plugins

  override fun addPluginStateChangedListener(listener: PluginEnableStateChangedListener, parentDisposable: Disposable) {
    DynamicPluginEnabler.addPluginStateChangedListener(listener)
    Disposer.register(parentDisposable, Disposable {
      DynamicPluginEnabler.removePluginStateChangedListener(listener)
    })
  }

  override fun getDisabledPluginIds(): Set<PluginId> {
    return DisabledPluginsState.getDisabledIds()
  }

  override fun findPlugin(pluginId: PluginId) = PluginManagerCore.findPlugin(pluginId)

  override fun createInstaller(notifyErrors: Boolean): SettingsSyncPluginInstaller {
    return SettingsSyncPluginInstallerImpl(notifyErrors)
  }

  override fun isIncompatible(plugin: IdeaPluginDescriptor) = PluginManagerCore.isIncompatible(plugin)
}