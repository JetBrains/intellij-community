package com.intellij.configurationScript

import com.intellij.configurationStore.properties.CollectionStoredProperty
import com.intellij.configurationStore.properties.MapStoredProperty
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.ScalarProperty
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode

internal fun readObject(instance: BaseState, node: MappingNode): BaseState {
  val properties = instance.__getProperties()
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
    else if (valueNode is SequenceNode) {
      for (property in properties) {
        if (property is CollectionStoredProperty<*, *> && key == property.name) {
          readCollection(property, valueNode)
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