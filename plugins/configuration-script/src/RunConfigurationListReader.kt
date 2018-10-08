package com.intellij.configurationScript

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.components.BaseState
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
    var keyToType: MutableMap<String, ConfigurationType>? = null
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

      // compute keyToType only if need
      if (keyToType == null) {
        keyToType = THashMap<String, ConfigurationType>()
        processConfigurationTypes { configurationType, propertyName, _ ->
          keyToType.put(propertyName.toString(), configurationType)
        }
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
    val optionsClass = factory.optionsClass ?: RunConfigurationOptions::class.java
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
    val instance = ReflectionUtil.newInstance(optionsClass)
    if (instance is RunConfigurationOptions) {
      // very important - set BEFORE read to ensure that user can set any value for isAllowRunningInParallel and it will be not overridden by us later
      instance.isAllowRunningInParallel = factory.singletonPolicy.isAllowRunningInParallel
    }
    processor(factory, readObject(instance, node))
  }
}