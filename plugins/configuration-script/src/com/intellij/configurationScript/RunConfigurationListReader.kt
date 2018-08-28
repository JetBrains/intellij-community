package com.intellij.configurationScript

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.ScalarProperty
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
  fun read(parentNode: MappingNode) {
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
        readRc(optionsClass, node, factory)
      }
    }
    else if (node is SequenceNode) {
      // array of child
      for (itemNode in node.value) {
        if (itemNode is MappingNode) {
          readRc(optionsClass, itemNode, factory)
        }
      }
    }
  }

  private fun readRc(optionsClass: Class<out BaseState>, node: MappingNode, factory: ConfigurationFactory) {
    val state = ReflectionUtil.newInstance(optionsClass)
    val properties = state.getProperties()
    for (tuple in node.value) {
      val valueNode = tuple.valueNode
      val key = (tuple.keyNode as ScalarNode).value
      if (valueNode is ScalarNode) {
        for (property in properties) {
          if (property is ScalarProperty && property.jsonType.isScalar && key == property.name) {
            property.parseAndSetValue(valueNode.value)
          }
        }
      }
    }
    processor(factory, state)
  }
}