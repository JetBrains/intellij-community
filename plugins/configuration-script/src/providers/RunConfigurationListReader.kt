package com.intellij.configurationScript.providers

import com.intellij.configurationScript.Keys
import com.intellij.configurationScript.LOG
import com.intellij.configurationScript.readIntoObject
import com.intellij.configurationScript.schemaGenerators.processConfigurationTypes
import com.intellij.configurationScript.schemaGenerators.rcFactoryIdToPropertyName
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.CollectionFactory
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.Node
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode

internal class RunConfigurationListReader(private val processor: (factory: ConfigurationFactory, state: Any) -> Unit) {
  // rc grouped by type
  fun read(parentNode: MappingNode, isTemplatesOnly: Boolean) {
    var keyToType: MutableMap<String, ConfigurationType>? = null
    for (tuple in parentNode.value) {
      val keyNode = tuple.keyNode
      if (keyNode !is ScalarNode) {
        LOG.warn("Unexpected keyNode type: $keyNode")
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
        keyToType = CollectionFactory.createMap<String, ConfigurationType>()
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
          LOG.warn("Unexpected valueNode type: ${valueNode}")
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
        LOG.warn("Unexpected keyNode type: ${keyNode}")
        continue
      }

      val factoryKey = keyNode.value
      val factory = type.configurationFactories.find { factory -> factoryKey == rcFactoryIdToPropertyName(
        factory)
      }
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
    processor(factory, readIntoObject(instance, node.value))
  }
}