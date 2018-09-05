package com.intellij.configurationScript

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.impl.RUN_CONFIGURATION_TEMPLATE_PROVIDER_EP
import com.intellij.execution.impl.RunConfigurationTemplateProvider
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
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

internal class IntellijConfigurationAppInitializer : ApplicationInitializedListener {
  override fun componentsInitialized() {
    ApplicationManager.getApplication().messageBus.connect().subscribe(RunManagerListener.TOPIC, object: RunManagerListener {
      override fun stateLoaded(runManager: RunManager, isFirstLoadState: Boolean) {
        if (!isFirstLoadState || !Registry.`is`("run.manager.use.intellij.config.file", false)) {
          return
        }

        val project = (runManager as? RunManagerImpl)?.project ?: return
        // todo listen file changes
        val file = findConfigurationFile(project) ?: return
        val inputStream = file.inputStreamIfExists() ?: return
        val map = THashMap<ConfigurationFactory, FactoryEntry>()
        inputStream.use {
          parseConfigurationFile(it.bufferedReader()) { factory, state ->
            map.put(factory, FactoryEntry(state))
          }
        }
        RUN_CONFIGURATION_TEMPLATE_PROVIDER_EP.findExtension(MyRunConfigurationTemplateProvider::class.java, project)!!.setTemplates(map)
      }
    })
  }
}

private class FactoryEntry(state: Any) {
  var state: Any? = state
  var settings: RunnerAndConfigurationSettingsImpl? = null
}

private class MyRunConfigurationTemplateProvider : RunConfigurationTemplateProvider {
  @Volatile
  private var map: Map<ConfigurationFactory, FactoryEntry>? = null

  override fun getRunConfigurationTemplate(factory: ConfigurationFactory, runManager: RunManagerImpl): RunnerAndConfigurationSettingsImpl? {
    val item = map?.get(factory) ?: return null
    synchronized(item) {
      var settings = item.settings
      if (settings != null) {
        return settings
      }

      settings = RunnerAndConfigurationSettingsImpl(runManager, isTemplate = true)
      item.settings = settings
      return settings
    }
  }

  internal fun setTemplates(value: Map<ConfigurationFactory, FactoryEntry>) {
    map = value
  }
}

// we cannot use the same approach as we generate JSON scheme because we should load option classes only in a lazy manner
// that's why we don't use snakeyaml TypeDescription approach to load
internal fun parseConfigurationFile(reader: Reader, processor: (factory: ConfigurationFactory, state: Any) -> Unit) {
  val yaml = Yaml(SafeConstructor())
  // later we can avoid full node graph building, but for now just use simple implementation (problem is that Yaml supports references and merge - proper support of it can be tricky)
  // "load" under the hood uses "compose" - i.e. Yaml itself doesn't use stream API to build object model.
  val rootNode = yaml.compose(reader) as? MappingNode ?: return
  val dataReader = RunConfigurationListReader(processor)
  for (tuple in rootNode.value) {
    val keyNode = tuple.keyNode
    if (keyNode is ScalarNode && keyNode.value == Keys.runConfigurations) {
      val rcTypeGroupNode = tuple.valueNode as? MappingNode ?: continue
      dataReader.read(rcTypeGroupNode)
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

internal const val IDE_FILE = "intellij.yaml"
internal const val IDE_FILE_VARIANT_2 = "intellij.yml"