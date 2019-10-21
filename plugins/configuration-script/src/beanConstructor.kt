package com.intellij.configurationScript

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.ScalarProperty
import com.intellij.openapi.components.StoredProperty
import com.intellij.serialization.stateProperties.CollectionStoredProperty
import com.intellij.serialization.stateProperties.MapStoredProperty
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode

internal fun <T : BaseState> readIntoObject(instance: T, node: MappingNode, affectedPropertyConsumer: ((StoredProperty<Any>) -> Unit)? = null): T {
  return readIntoObject(instance, node.value, affectedPropertyConsumer)
}

internal fun <T : BaseState> readIntoObject(instance: T, nodes: List<NodeTuple>, affectedPropertyConsumer: ((StoredProperty<Any>) -> Unit)? = null): T {
  val properties = instance.__getProperties()
  for (tuple in nodes) {
    val valueNode = tuple.valueNode
    val key = (tuple.keyNode as ScalarNode).value
    if (valueNode is ScalarNode) {
      for (property in properties) {
        if (property is ScalarProperty && property.jsonType.isScalar && key == property.name) {
          property.parseAndSetValue(valueNode.value)
          affectedPropertyConsumer?.invoke(property)
          break
        }
      }
    }
    else if (valueNode is MappingNode) {
      for (property in properties) {
        if (property is MapStoredProperty<*, *> && key == property.name) {
          readMap(property, valueNode)
          affectedPropertyConsumer?.invoke(property)
          break
        }
      }
    }
    else if (valueNode is SequenceNode) {
      for (property in properties) {
        if (property is CollectionStoredProperty<*, *> && key == property.name) {
          readCollection(property, valueNode)
          affectedPropertyConsumer?.invoke(property)
          break
        }
      }
    }
  }
  return instance
}

private fun readCollection(property: CollectionStoredProperty<*, *>, valueNode: SequenceNode) {
  @Suppress("UNCHECKED_CAST")
  val collection = (property as CollectionStoredProperty<String, MutableList<String>>).__getValue()
  collection.clear()
  if (!valueNode.value.isEmpty()) {
    for (itemNode in valueNode.value) {
      val itemValue = (itemNode as? ScalarNode)?.value ?: continue
      collection.add(itemValue)
    }
  }
}

private fun readMap(property: MapStoredProperty<*, *>, valueNode: MappingNode) {
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