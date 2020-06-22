package com.intellij.configurationScript.providers

import com.intellij.configurationScript.ConfigurationFileManager
import com.intellij.configurationScript.Keys
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.impl.RunConfigurationTemplateProvider
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.SynchronizedClearableLazy
import com.intellij.util.containers.CollectionFactory
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.ScalarNode

private class FactoryEntry(state: Any) {
  var state: Any? = state
  var settings: RunnerAndConfigurationSettingsImpl? = null
}

private class MyRunConfigurationTemplateProvider(private val project: Project) : RunConfigurationTemplateProvider {
  private val map = SynchronizedClearableLazy<Map<ConfigurationFactory, FactoryEntry>> {
    val node = project.service<ConfigurationFileManager>().getConfigurationNode()
               ?: return@SynchronizedClearableLazy emptyMap()
    val map = CollectionFactory.createMap<ConfigurationFactory, FactoryEntry>()
    readRunConfigurations(node, isTemplatesOnly = true) { factory, state ->
      map.put(factory, FactoryEntry(state))
    }
    map
  }

  init {
    project.service<ConfigurationFileManager>().registerClearableLazyValue(map)
  }

  override fun getRunConfigurationTemplate(factory: ConfigurationFactory, runManager: RunManagerImpl): RunnerAndConfigurationSettingsImpl? {
    val item = map.value.get(factory) ?: return null
    synchronized(item) {
      var settings = item.settings
      if (settings != null) {
        return settings
      }

      val configuration = factory.createTemplateConfiguration(runManager.project, runManager)
      // see readRunConfiguration about how do we set isAllowRunningInParallel
      if (configuration is PersistentStateComponent<*>) {
        @Suppress("UNCHECKED_CAST")
        (configuration as PersistentStateComponent<Any>).loadState(item.state!!)
      }
      else {
        (configuration as RunConfigurationBase<*>).setOptionsFromConfigurationFile(item.state as BaseState)
      }
      settings = RunnerAndConfigurationSettingsImpl(runManager, configuration, isTemplate = true)
      item.state = null
      item.settings = settings
      return settings
    }
  }
}

internal fun readRunConfigurations(rootNode: MappingNode, isTemplatesOnly: Boolean, processor: (factory: ConfigurationFactory, state: Any) -> Unit) {
  // later we can avoid full node graph building, but for now just use simple implementation (problem is that Yaml supports references and merge - proper support of it can be tricky)
  // "load" under the hood uses "compose" - i.e. Yaml itself doesn't use stream API to build object model.
  val dataReader = RunConfigurationListReader(processor)
  for (tuple in rootNode.value) {
    val keyNode = tuple.keyNode
    if (keyNode is ScalarNode && keyNode.value == Keys.runConfigurations) {
      val valueNode = tuple.valueNode as? MappingNode ?: continue
      dataReader.read(valueNode, isTemplatesOnly)
    }
  }
}