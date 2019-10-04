package com.intellij.configurationScript.schemaGenerators

import com.intellij.configurationStore.Property
import com.intellij.openapi.components.BaseState
import com.intellij.serialization.stateProperties.CollectionStoredProperty
import com.intellij.serialization.stateProperties.EnumStoredProperty
import com.intellij.serialization.stateProperties.MapStoredProperty
import gnu.trove.THashMap
import org.jetbrains.io.JsonObjectBuilder
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.memberProperties

internal fun buildJsonSchema(state: BaseState, builder: JsonObjectBuilder, customFilter: ((name: String) -> Boolean)? = null) {
  val properties = state.__getProperties()
  val memberProperties = state::class.memberProperties
  var propertyToAnnotation: MutableMap<String, Property>? = null
  for (property in memberProperties) {
    val annotation = property.findAnnotation<Property>()
    if (annotation != null) {
      if (propertyToAnnotation == null) {
        propertyToAnnotation = THashMap()
      }
      propertyToAnnotation.put(property.name, annotation)
    }
  }

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
          map("items") {
            "type" to "string"
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
