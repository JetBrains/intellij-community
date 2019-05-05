package com.intellij.configurationScript.providers

import com.intellij.configurationScript.ConfigurationFileManager
import com.intellij.configurationScript.Keys
import com.intellij.configurationScript.readIntoObject
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.processOpenedProjects
import com.intellij.openapi.updateSettings.impl.UpdateSettingsProvider
import com.intellij.openapi.util.NotNullLazyKey
import com.intellij.util.SmartList
import com.intellij.util.concurrency.SynchronizedClearableLazy
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.ScalarNode

private val dataKey = NotNullLazyKey.create<SynchronizedClearableLazy<PluginsConfiguration?>, Project>("MyUpdateSettingsProvider") { project ->
  val data = SynchronizedClearableLazy {
    val node = ConfigurationFileManager.getInstance(project).getConfigurationNode() ?: return@SynchronizedClearableLazy null
    readPluginsConfiguration(node)
  }
  ConfigurationFileManager.getInstance(project).registerClearableLazyValue(data)
  data
}

private class MyUpdateSettingsProvider : UpdateSettingsProvider {
  override fun getPluginRepositories(): List<String> {
    val result = SmartList<String>()
    processOpenedProjects { project ->
      dataKey.getValue(project).value?.repositories?.let {
        result.addAll(it)
      }
    }
    return result
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
      return readIntoObject(PluginsConfiguration(), valueNode)
    }
  }
  return null
}