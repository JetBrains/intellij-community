package com.intellij.configurationScript

import com.intellij.configurationStore.properties.EnumStoredProperty
import com.intellij.execution.configurations.LocatableRunConfigurationOptions
import com.intellij.openapi.components.BaseState

internal fun buildJsonSchema(state: BaseState, builder: JsonObjectBuilder) {
  val properties = state.__getProperties()
  val isLocatableRunConfigurationOptions = state is LocatableRunConfigurationOptions
  // todo object definition
  for (property in properties) {
    if (isLocatableRunConfigurationOptions && property.name == "isNameGenerated") {
      // overkill for now to introduce special annotation for this case
      continue
    }

    builder.map(property.name!!) {
      "type" to property.jsonType.jsonName
      if (property is EnumStoredProperty<*>) {
        describeEnum(property)
      }
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
