package com.intellij.settingsSync.core.plugins

import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginEnabler
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.PluginId

abstract class AbstractPluginManagerProxy : PluginManagerProxy {
  private val _essentialPlugins: Lazy<Set<PluginId>> = lazy {
    getEffectiveEssentialPlugins()
  }
  private val essentialPlugins: Set<PluginId>
    get() = _essentialPlugins.value

  override fun isEssential(pluginId: PluginId): Boolean {
    return essentialPlugins.contains(pluginId)
  }

  protected abstract val pluginEnabler: PluginEnabler
  protected abstract fun isDescriptorEssential(pluginId: PluginId): Boolean

  override fun enablePlugins(plugins: Set<PluginId>) : Boolean {
    if (plugins.isEmpty()) {
      return true
    }
    return pluginEnabler.enableById(plugins)
  }

  override fun disablePlugins(plugins: Set<PluginId>) : Boolean {
    if (plugins.isEmpty()) {
      return true
    }
    val essentials2disable = plugins.filter { isEssential(it) }.toSet()
    if (essentials2disable.isNotEmpty()) {
      LOG.info("Won't disable essential (or effectively essential) plugins: $essentials2disable")
    }
    val nonessentialPlugins = plugins - essentials2disable
    return pluginEnabler.disableById(nonessentialPlugins)
  }

  private fun getEffectiveEssentialPlugins(): Set<PluginId> {
    val essentialPlugins = hashSetOf<PluginId>()
    // populate ids
    val filteredPlugins = getPlugins().filter { isDescriptorEssential(it.pluginId) }
    for (pluginDescriptor in filteredPlugins) {
      addAllDependenciesRecursively(essentialPlugins, pluginDescriptor, 1)
    }
    return essentialPlugins
  }

  private fun addAllDependenciesRecursively(essentialPlugins: MutableSet<PluginId>, descriptor: IdeaPluginDescriptor, depth: Int) {
    if (depth > 6) {
      LOG.warn("getAllDependenciesRecursively is called with depth > 6")
      return
    }

    // descriptor.pluginId can differ from pluginId
    // "com.intellij.modules.platform" -> "com.intellij"
    if (!essentialPlugins.add(descriptor.pluginId)) { // we've already processed that plugin, return
      return
    }
    for (dependency in descriptor.dependencies) {
      if (!dependency.isOptional) { // com.intellij.modules.ultimate depends on itself
        val depPlugin = findPlugin(dependency.pluginId) ?: continue
        addAllDependenciesRecursively(essentialPlugins, depPlugin, depth + 1)
      }
    }
  }

  companion object {
    private val LOG = logger<AbstractPluginManagerProxy>()
  }
}