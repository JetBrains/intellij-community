package com.intellij.configurationScript

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.impl.RunConfigurationTemplateProvider
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.AtomicClearableLazyValue
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.exists
import com.intellij.util.io.inputStreamIfExists
import gnu.trove.THashMap
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.ScalarNode
import java.io.Reader
import java.nio.file.Path
import java.nio.file.Paths

private class FactoryEntry(state: Any) {
  var state: Any? = state
  var settings: RunnerAndConfigurationSettingsImpl? = null
}

private class MyRunConfigurationTemplateProvider(private val project: Project) : RunConfigurationTemplateProvider {
  private val map = object : AtomicClearableLazyValue<Map<ConfigurationFactory, FactoryEntry>>() {
    override fun compute(): Map<ConfigurationFactory, FactoryEntry> {
      val file = findConfigurationFile(project) ?: return emptyMap()
      val inputStream = file.inputStreamIfExists() ?: return emptyMap()
      val map = THashMap<ConfigurationFactory, FactoryEntry>()
      inputStream.use {
        parseConfigurationFile(it.bufferedReader(), isTemplatesOnly = true) { factory, state ->
          map.put(factory, FactoryEntry(state))
        }
      }
      return map
    }
  }

  init {
    project.service<ConfigurationFileManager>().registerClearableLazyValue(map)
  }

  override fun getRunConfigurationTemplate(factory: ConfigurationFactory, runManager: RunManagerImpl): RunnerAndConfigurationSettingsImpl? {
    if (!Registry.`is`("run.manager.use.intellij.config.file", false)) {
      return null
    }

    val item = map.value.get(factory) ?: return null
    synchronized(item) {
      var settings = item.settings
      if (settings != null) {
        return settings
      }

      val configuration = factory.createTemplateConfiguration(runManager.project, runManager)
      (configuration as RunConfigurationBase).setState(item.state as BaseState)
      settings = RunnerAndConfigurationSettingsImpl(runManager, configuration, isTemplate = true, isSingleton = factory.singletonPolicy.isSingleton)
      item.state = null
      item.settings = settings
      return settings
    }
  }
}

// we cannot use the same approach as we generate JSON scheme because we should load option classes only in a lazy manner
// that's why we don't use snakeyaml TypeDescription approach to load
internal fun parseConfigurationFile(reader: Reader, isTemplatesOnly: Boolean, processor: (factory: ConfigurationFactory, state: Any) -> Unit) {
  val yaml = Yaml(SafeConstructor())
  // later we can avoid full node graph building, but for now just use simple implementation (problem is that Yaml supports references and merge - proper support of it can be tricky)
  // "load" under the hood uses "compose" - i.e. Yaml itself doesn't use stream API to build object model.
  val rootNode = yaml.compose(reader) as? MappingNode ?: return
  val dataReader = RunConfigurationListReader(processor)
  for (tuple in rootNode.value) {
    val keyNode = tuple.keyNode
    if (keyNode is ScalarNode && keyNode.value == Keys.runConfigurations) {
      val rcTypeGroupNode = tuple.valueNode as? MappingNode ?: continue
      dataReader.read(rcTypeGroupNode, isTemplatesOnly)
    }
  }
}

/**
 * not-null doesn't mean that you should not expect NoSuchFileException
 */
private fun findConfigurationFile(project: Project): Path? {
  val projectIdeaDir = Paths.get(project.basePath)
  var file = projectIdeaDir.resolve("intellij.yaml")
  if (!file.exists()) {
    // do not check file exists - on read we in any case should check NoSuchFileException
    file = projectIdeaDir.resolve("intellij.yml")
  }
  return file
}