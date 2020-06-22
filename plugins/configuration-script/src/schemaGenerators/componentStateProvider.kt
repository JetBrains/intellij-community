package com.intellij.configurationScript.schemaGenerators

import com.intellij.configurationScript.LOG
import com.intellij.configurationScript.SchemaGenerator
import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.ServiceDescriptor
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.io.JsonObjectBuilder

internal class ComponentStateJsonSchemaGenerator : SchemaGenerator {
  private val pathToStateClass: MutableMap<String, Class<out BaseState>> = CollectionFactory.createMap()

  private val objectSchemaGenerator = OptionClassJsonSchemaGenerator("classDefinitions")

  override val definitionNodeKey: CharSequence?
    get() = objectSchemaGenerator.definitionNodeKey

  // schema is generated without project - we cannot rely on created component adapter for services
  override fun generate(rootBuilder: JsonObjectBuilder) {
    for (plugin in PluginManagerCore.getLoadedPlugins()) {
      for (serviceDescriptor in (plugin as IdeaPluginDescriptorImpl).project.services) {
        processServiceDescriptor(serviceDescriptor, plugin)
      }
    }
    doGenerate(rootBuilder, pathToStateClass)
  }

  override fun generateDefinitions() = objectSchemaGenerator.describe()

  internal fun doGenerate(rootBuilder: JsonObjectBuilder, pathToStateClass: Map<String, Class<out BaseState>>) {
    if (pathToStateClass.isEmpty()) {
      return
    }

    val pathToJsonObjectBuilder: MutableMap<String, JsonObjectBuilder> = CollectionFactory.createMap()
    for (path in pathToStateClass.keys.sorted()) {
      val keys = path.split(".")
      val jsonObjectBuilder: JsonObjectBuilder
      LOG.assertTrue(keys.size < 3, "todo")
      if (keys.size == 1) {
        jsonObjectBuilder = rootBuilder
      }
      else {
        jsonObjectBuilder = pathToJsonObjectBuilder.getOrPut(keys.subList(0, keys.size - 1).joinToString(".")) {
          JsonObjectBuilder(StringBuilder(), indentLevel = 3)
        }
      }

      jsonObjectBuilder.map(keys.last()) {
        "type" to "object"
        map("properties") {
          buildJsonSchema(ReflectionUtil.newInstance(pathToStateClass.getValue(path)), this, objectSchemaGenerator)
        }
        "additionalProperties" to false
      }
    }

    for ((key, value) in pathToJsonObjectBuilder) {
      rootBuilder.map(key) {
        "type" to "object"
        rawBuilder("properties", value)
        "additionalProperties" to false
      }
    }
  }

  private fun processServiceDescriptor(serviceDescriptor: ServiceDescriptor, plugin: PluginDescriptor) {
    val schemaKeyPath = serviceDescriptor.configurationSchemaKey ?: return
    LOG.runAndLogException {
      val implClass = Class.forName(serviceDescriptor.implementation, /* initialize = */ false, plugin.pluginClassLoader)
      if (!PersistentStateComponent::class.java.isAssignableFrom(implClass)) {
        return
      }

      // must inherit from BaseState
      @Suppress("UNCHECKED_CAST")
      val stateClass = ComponentSerializationUtil.getStateClass<BaseState>(implClass as Class<out PersistentStateComponent<Any>>)
      val oldValue = pathToStateClass.put(schemaKeyPath, stateClass)
      if (oldValue != null) {
        LOG.error("duplicated configurationSchemaKey $schemaKeyPath in plugin $plugin")
      }
    }
  }
}