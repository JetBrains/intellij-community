package com.intellij.configurationScript

import com.intellij.configurationStore.properties.MapStoredProperty
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.ScalarProperty
import com.intellij.openapi.components.StoredProperty
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.util.ReflectionUtil
import gnu.trove.THashMap
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode

internal class RunConfigurationListReader(private val processor: (factory: ConfigurationFactory, state: Any) -> Unit) {
  // rc grouped by type
  fun read(parentNode: MappingNode, isTemplatesOnly: Boolean) {
    val keyToType = THashMap<String, ConfigurationType>()
    processConfigurationTypes { configurationType, propertyName, _ ->
      keyToType.put(propertyName.toString(), configurationType)
    }

    for (tuple in parentNode.value) {
      val keyNode = tuple.keyNode
      if (keyNode !is ScalarNode) {
        LOG.warn("Unexpected keyNode type: ${keyNode.nodeId}")
        continue
      }

      if (keyNode.value == Keys.templates) {
        if (isTemplatesOnly) {
          read(tuple.valueNode as? MappingNode ?: continue, false)
        }
        continue
      }

      val configurationType = keyToType.get(keyNode.value)
      if (configurationType == null) {
        LOG.warn("Unknown run configuration type: ${keyNode.value}")
        continue
      }

      val factories = configurationType.configurationFactories
      if (factories.isEmpty()) {
        continue
      }

      val valueNode = tuple.valueNode

      if (factories.size > 1) {
        if (valueNode !is MappingNode) {
          LOG.warn("Unexpected valueNode type: ${valueNode.nodeId}")
          continue
        }

        readFactoryGroup(valueNode, configurationType)
      }
      else {
        readRunConfigurationGroup(tuple.valueNode, factories.first())
      }
    }
  }

  // rc grouped by factory (nested group) if more than one factory
  private fun readFactoryGroup(parentNode: MappingNode, type: ConfigurationType) {
    for (tuple in parentNode.value) {
      val keyNode = tuple.keyNode
      if (keyNode !is ScalarNode) {
        LOG.warn("Unexpected keyNode type: ${keyNode.nodeId}")
        continue
      }

      val factoryKey = keyNode.value
      val factory = type.configurationFactories.find { factory -> factoryKey == rcFactoryIdToPropertyName(factory) }
      if (factory == null) {
        LOG.warn("Unknown run configuration factory: ${keyNode.value}")
        continue
      }

      readRunConfigurationGroup(tuple.valueNode, factory)
    }
  }

  private fun readRunConfigurationGroup(node: Node, factory: ConfigurationFactory) {
    val optionsClass = factory.optionsClass
    if (optionsClass == null) {
      LOG.debug { "Configuration factory \"${factory.name}\" is not described because options class not defined" }
      return
    }

    if (node is MappingNode) {
      // direct child
      LOG.runAndLogException {
        readRunConfiguration(optionsClass, node, factory)
      }
    }
    else if (node is SequenceNode) {
      // array of child
      for (itemNode in node.value) {
        @Suppress("IfThenToSafeAccess")
        if (itemNode is MappingNode) {
          readRunConfiguration(optionsClass, itemNode, factory)
        }
      }
    }
  }

  private fun readRunConfiguration(optionsClass: Class<out BaseState>, node: MappingNode, factory: ConfigurationFactory) {
    val state = ReflectionUtil.newInstance(optionsClass)
    val properties = state.__getProperties()
    for (tuple in node.value) {
      val valueNode = tuple.valueNode
      val key = (tuple.keyNode as ScalarNode).value
      if (valueNode is ScalarNode) {
        for (property in properties) {
          if (property is ScalarProperty && property.jsonType.isScalar && key == property.name) {
            property.parseAndSetValue(valueNode.value)
            break
          }
        }
      }
      else if (valueNode is MappingNode) {
        for (property in properties) {
          if (property is MapStoredProperty<*, *> && key == property.name) {
            readMap(property, valueNode)
            break
          }
        }
      }
    }
    processor(factory, state)
  }
}

private fun readMap(property: StoredProperty<Any>, valueNode: MappingNode) {
  @Suppress("UNCHECKED_CAST")
  val map = (property as MapStoredProperty<String, String>).__getValue()
  map.clear()
  if (!valueNode.value.isEmpty()) {
    for (tuple in valueNode.value) {
      val key = (tuple.keyNode as? ScalarNode)?.value ?: continue
      val value = (tuple.valueNode as? ScalarNode)?.value ?: continue
      map.put(key, value)
    }
  }
}