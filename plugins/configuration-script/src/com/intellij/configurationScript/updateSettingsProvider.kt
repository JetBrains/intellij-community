package com.intellij.configurationScript

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.updateSettings.impl.UpdateSettingsProvider
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.ScalarNode

private class MyUpdateSettingsProvider(project: Project) : UpdateSettingsProvider {
  private val data = SynchronizedClearableLazy<PluginsConfiguration?> {
    val node = project.service<ConfigurationFileManager>().getConfigurationNode() ?: return@SynchronizedClearableLazy null
    readPluginsConfiguration(node)
  }

  init {
    project.service<ConfigurationFileManager>().registerClearableLazyValue(data)
  }

  override fun getPluginRepositories(): List<String> {
    return data.value?.repositories ?: emptyList()
  }
}

internal class PluginsConfiguration : BaseState() {
  val repositories by list<String>()
}

internal fun readPluginsConfiguration(rootNode: MappingNode): PluginsConfiguration? {
  // later we can avoid full node graph building, but for now just use simple implementation (problem is that Yaml supports references and merge - proper support of it can be tricky)
  // "load" under the hood uses "compose" - i.e. Yaml itself doesn't use stream API to build object model.
  for (tuple in rootNode.value) {
    val keyNode = tuple.keyNode
    if (keyNode is ScalarNode && keyNode.value == Keys.plugins) {
      val valueNode = tuple.valueNode as? MappingNode ?: continue
      return readObject(PluginsConfiguration::class.java, valueNode) as PluginsConfiguration
    }
  }
  return null
}