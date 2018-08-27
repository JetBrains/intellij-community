package com.intellij.configurationScript

import com.intellij.execution.RunManager
import com.intellij.execution.RunManagerListener
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.impl.RunManagerImpl
import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.project.stateStore
import com.intellij.util.io.exists
import com.intellij.util.io.inputStreamIfExists
import gnu.trove.THashMap
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
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
        inputStream.use {
          parseConfigurationFile(it.bufferedReader())
        }
      }
    })
  }
}

// we cannot use the same approach as we generate JSON scheme because we should load option classes only in a lazy manner
// that's why we don't use snakeyaml TypeDescription approach to load
internal fun parseConfigurationFile(reader: Reader): Any? {
  val constructor = MyConstructor()
  val yaml = Yaml(constructor)
  // later we can avoid full node graph building, but for now just use simple implementation (problem is that Yaml supports references and merge - proper support of it can be tricky)
  // "load" under the hood uses "compose" - i.e. Yaml itself doesn't use stream API to build object model.
  val rootNode = yaml.compose(reader) as? MappingNode ?: return null
  for (node in rootNode.value) {
    val keyNode = node.keyNode
    if (keyNode is ScalarNode && keyNode.value == Keys.runConfigurations) {
      val rcTypeGroupNode = node.valueNode as? MappingNode ?: continue
      readRunConfigurationsNode(rcTypeGroupNode)
      val document = constructor.doConstructDocument(node.valueNode)
      return document
    }
  }
  return rootNode
}

private fun readRunConfigurationsNode(parentNode: MappingNode) {
  val keyToType = THashMap<String, ConfigurationType>()
  processConfigurationTypes { configurationType, propertyName, _ ->
    keyToType.put(propertyName.toString(), configurationType)
  }
  for (node in parentNode.value) {
    val keyNode = node.keyNode as? ScalarNode ?: continue
    //
  }
}

private class MyConstructor : SafeConstructor() {
  override fun newInstance(node: Node): Any {
    return super.newInstance(node)
  }

  fun doConstructDocument(node: Node): Any? {
    return constructDocument(node)
  }

  override fun createDefaultMap(initSize: Int): MutableMap<Any, Any> {
    // order not important because actual class instance will be created instead of untyped map
    // THashMap is more efficient than LinkedHashMap
    return THashMap(initSize)
  }
}

private class ConfigurationFileRoot {
  var runConfigurations: List<BaseState>? = null
}

/**
 * not-null doesn't mean that you should not expect NoSuchFileException
 */
private fun findConfigurationFile(project: Project): Path? {
  val projectIdeaDir = Paths.get(project.stateStore.directoryStorePath)
  var file = projectIdeaDir.resolve("intellij.yaml")
  if (!file.exists()) {
    // do not check file exists - on read we in any case should check NoSuchFileException
    file = projectIdeaDir.resolve("intellij.yml")
  }
  return file
}