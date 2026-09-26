package com.intellij.configurationScript

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.ScalarProperty
import com.intellij.openapi.components.StoredProperty
import com.intellij.serialization.stateProperties.CollectionStoredProperty
import com.intellij.serialization.stateProperties.MapStoredProperty
import com.intellij.serialization.stateProperties.ObjectStateStoredPropertyBase
import com.intellij.util.ReflectionUtil
import org.jetbrains.annotations.ApiStatus
import org.snakeyaml.engine.v2.nodes.MappingNode
import org.snakeyaml.engine.v2.nodes.NodeTuple
import org.snakeyaml.engine.v2.nodes.ScalarNode
import org.snakeyaml.engine.v2.nodes.SequenceNode

@ApiStatus.Internal
fun <T : BaseState> readIntoObject(instance: T, nodes: List<NodeTuple>, affectedPropertyConsumer: ((StoredProperty<Any>) -> Unit)? = null): T {
  val properties = instance.__getProperties()
  val itemTypeInfoProvider = ItemTypeInfoProvider(instance.javaClass)
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
        if (key == property.name) {
          if (property is MapStoredProperty<*, *>) {
            readMap(property, valueNode)
            affectedPropertyConsumer?.invoke(property)
            break
          }
          if (property is ObjectStateStoredPropertyBase<*>) {
            readIntoObject(property.getValue(instance as BaseState) as BaseState, valueNode.value, affectedPropertyConsumer)
          }
        }
      }
    }
    else if (valueNode is SequenceNode) {
      for (property in properties) {
        if (property is CollectionStoredProperty<*, *> && key == property.name) {
          readCollection(property, itemTypeInfoProvider, valueNode)
          affectedPropertyConsumer?.invoke(property)
          break
        }
      }
    }
  }
  return instance
}

private fun readCollection(property: CollectionStoredProperty<*, *>, itemTypeInfoProvider: ItemTypeInfoProvider, valueNode: SequenceNode) {
  @Suppress("UNCHECKED_CAST")
  val collection = (property as CollectionStoredProperty<Any, MutableCollection<Any>>).__getValue()
  collection.clear()
  if (valueNode.value.isEmpty()) {
    return
  }

  val itemType by lazy { itemTypeInfoProvider.getListItemType(property.name!!, logAsErrorIfPropertyNotFound = false) }

  for (itemNode in valueNode.value) {
    if (itemNode is ScalarNode) {
      collection.add(itemNode.value ?: continue)
    }
    else if (itemNode is MappingNode) {
      // object
      val itemInstance = ReflectionUtil.newInstance(itemType ?: continue, false)
      readIntoObject(itemInstance, itemNode.value)
      collection.add(itemInstance)
    }
  }
}

private fun readMap(property: MapStoredProperty<*, *>, valueNode: MappingNode) {
  @Suppress("UNCHECKED_CAST")
  val map = (property as MapStoredProperty<String, String>).__getValue()
  map.clear()
  if (valueNode.value.isNotEmpty()) {
    for (tuple in valueNode.value) {
      val key = (tuple.keyNode as? ScalarNode)?.value ?: continue
      val value = (tuple.valueNode as? ScalarNode)?.value ?: continue
      map.put(key, value)
    }
  }
}