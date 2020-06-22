package com.intellij.configurationScript.schemaGenerators

import com.intellij.configurationScript.ItemTypeInfoProvider
import com.intellij.configurationScript.LOG
import com.intellij.configurationStore.Property
import com.intellij.openapi.components.BaseState
import com.intellij.serialization.stateProperties.CollectionStoredProperty
import com.intellij.serialization.stateProperties.EnumStoredProperty
import com.intellij.serialization.stateProperties.MapStoredProperty
import com.intellij.util.ReflectionUtil
import com.intellij.util.containers.CollectionFactory
import org.jetbrains.io.JsonObjectBuilder
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

internal class OptionClassJsonSchemaGenerator(val definitionNodeKey: String) {
  val definitionPointerPrefix = "#/$definitionNodeKey/"
  private val queue: MutableSet<Class<out BaseState>> = hashSetOf()

  private val definitionBuilder = StringBuilder()
  val definitions = JsonObjectBuilder(definitionBuilder, indentLevel = 1)

  fun describe(): CharSequence {
    if (queue.isEmpty()) {
      return definitionBuilder
    }

    val list: MutableList<Class<out BaseState>> = arrayListOf()
    while (true) {
      if (queue.isEmpty()) {
        return definitionBuilder
      }

      list.clear()
      list.addAll(queue)
      queue.clear()
      list.sortedBy { it.name }

      for (clazz in list) {
        definitions.map(clazz.name.replace('.', '_')) {
          "type" to "object"
          map("properties") {
            val instance = ReflectionUtil.newInstance(clazz)
            buildJsonSchema(instance, this, this@OptionClassJsonSchemaGenerator)
          }
          "additionalProperties" to false
        }
      }
    }
  }

  fun addClass(clazz: Class<out BaseState>): CharSequence {
    queue.add(clazz)
    return clazz.name.replace('.', '_')
  }
}

internal fun buildJsonSchema(state: BaseState,
                             builder: JsonObjectBuilder,
                             subObjectSchemaGenerator: OptionClassJsonSchemaGenerator?,
                             customFilter: ((name: String) -> Boolean)? = null) {
  val properties = state.__getProperties()
  val memberProperties = state::class.memberProperties
  var propertyToAnnotation: MutableMap<String, Property>? = null
  for (property in memberProperties) {
    val annotation = property.findAnnotation<Property>() ?: continue
    if (propertyToAnnotation == null) {
      propertyToAnnotation = CollectionFactory.createMap()
    }
    propertyToAnnotation.put(property.name, annotation)
  }

  val itemTypeInfoProvider = ItemTypeInfoProvider(state.javaClass)

  for (property in properties) {
    val name = property.name!!
    val annotation = propertyToAnnotation?.get(name)
    if (annotation?.ignore == true) {
      continue
    }

    if (customFilter != null && !customFilter(name)) {
      continue
    }

    builder.map(name) {
      "type" to property.jsonType.jsonName

      annotation?.let {
        if (it.description.isNotEmpty()) {
          "description" toUnescaped it.description
        }
      }

      when (property) {
        is EnumStoredProperty<*> -> describeEnum(property)
        is MapStoredProperty<*, *> -> {
          map("additionalProperties") {
            "type" to "string"
          }
        }
        is CollectionStoredProperty<*, *> -> {
          val listType = itemTypeInfoProvider.getListItemType(name, logAsErrorIfPropertyNotFound = true) ?: return@map
          when {
            listType === java.lang.String::class.java -> {
              map("items") {
                "type" to "string"
              }
            }
            subObjectSchemaGenerator == null -> {
              LOG.error("$listType not supported for collection property $name because subObjectSchemaGenerator is not specified")
            }
            else -> {
              map("items") {
                @Suppress("UNCHECKED_CAST")
                definitionReference(subObjectSchemaGenerator.definitionPointerPrefix,
                                    subObjectSchemaGenerator.addClass(listType))
              }
            }
          }
        }
      }

      // todo object definition
    }
  }
}

private fun JsonObjectBuilder.describeEnum(property: EnumStoredProperty<*>) {
  rawArray("enum") { stringBuilder ->
    val enumConstants = property.clazz.enumConstants
    for (enum in enumConstants) {
      stringBuilder.append('"').append(enum.toString().toLowerCase()).append('"')
      if (enum !== enumConstants.last()) {
        stringBuilder.append(',')
      }
    }
  }
}
