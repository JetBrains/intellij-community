package com.intellij.configurationScript

import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.openapi.components.buildJsonSchema
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ReflectionUtil

@Suppress("JsonStandardCompliance")
private const val ref = "\$ref"

internal inline fun processConfigurationTypes(processor: (configurationType: ConfigurationType, propertyName: CharSequence) -> Unit) {
  val configurationTypes = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
  for (configurationType in configurationTypes) {
    val propertyName = rcTypeIdToPropertyName(configurationType) ?: continue
    processor(configurationType, propertyName)
  }
}

internal class RunConfigurationJsonSchemaGenerator(private val definitions: JsonObjectBuilder) {
  fun generate(properties: JsonObjectBuilder) {
    processConfigurationTypes { type, typePropertyName ->
      val definitionId = "${typePropertyName}RC"
      val factories = type.configurationFactories
      if (factories.isEmpty()) {
        LOG.error("Configuration type \"${type.displayName}\" is not valid: factory list is empty")
      }

      val isMultiFactory = factories.size > 1
      addPropertyForConfigurationType(properties, typePropertyName, isMultiFactory, type, definitionId)

      if (isMultiFactory) {
        definitions.map(definitionId) {
          "type" to "object"

          if (!StringUtil.equals(typePropertyName, type.configurationTypeDescription)) {
            "description" toUnescaped type.configurationTypeDescription
          }

          map("properties") {
            for (factory in factories) {
              val factoryPropertyName = rcFactoryIdToPropertyName(factory) ?: continue
              val factoryDefinitionId = "${factoryPropertyName}RCF"
              addPropertyForFactory(factoryPropertyName, factory, factoryDefinitionId)
              describeFactory(factory, factoryDefinitionId)
            }
          }
        }
      }
      else {
        describeFactory(factories.first(), definitionId)
      }
    }
  }

  private fun addPropertyForConfigurationType(properties: JsonObjectBuilder, typePropertyName: CharSequence, isMultiFactory: Boolean, type: ConfigurationType, definitionId: String) {
    val description = type.configurationTypeDescription
    properties.map(typePropertyName) {
      if (isMultiFactory) {
        "type" toRaw """["array", "object"]"""
      }
      else {
        "type" to "object"
      }

      if (!StringUtil.equals(typePropertyName, description)) {
        "description" toUnescaped description
      }

      if (isMultiFactory) {
        map("items") {
          rawScalar(ref) {
            append("#/definitions/")
            append(definitionId)
          }
        }
      }

      rawScalar(ref) {
        append("#/definitions/")
        append(definitionId)
      }
    }
  }

  private fun JsonObjectBuilder.addPropertyForFactory(factoryPropertyName: CharSequence, factory: ConfigurationFactory, factoryDefinitionId: String) {
    map(factoryPropertyName) {
      "type" toRaw """["array", "object"]"""

      if (!StringUtil.equals(factoryPropertyName, factory.name)) {
        "description" toUnescaped factory.name
      }

      map("items") {
        rawScalar(ref) {
          append("#/definitions/")
          append(factoryDefinitionId)
        }
      }
      rawScalar(ref) {
        append("#/definitions/")
        append(factoryDefinitionId)
      }
    }
  }

  private fun describeFactory(factory: ConfigurationFactory, definitionId: String) {
    val optionsClass = factory.optionsClass
    if (optionsClass == null) {
      LOG.debug { "Configuration factory \"${factory.name}\" is not described because options class not defined" }

      definitions.map(definitionId) {
        "additionalProperties" to true
      }
      return
    }

    val state = ReflectionUtil.newInstance(optionsClass)
    definitions.map(definitionId) {
      rawMap("properties") { buildJsonSchema(state, it) }
    }
    "additionalProperties" to false
  }
}

// returns null if id is not valid
internal fun rcTypeIdToPropertyName(configurationType: ConfigurationType): CharSequence? {
  return idToPropertyName(configurationType.tag, configurationType, null)
}

// returns null if id is not valid
internal fun rcFactoryIdToPropertyName(factory: ConfigurationFactory): CharSequence? {
  return idToPropertyName(factory.id, null, factory)
}

// returns null if id is not valid
private fun idToPropertyName(string: String, configurationType: ConfigurationType?, factory: ConfigurationFactory?): CharSequence? {
  val result = string
    .removeSuffix("Type")
    .removeSuffix("RunConfiguration")
    .removeSuffix("Configuration")
  if (result.isEmpty()) {
    if (factory == null) {
      LOG.error("Configuration type \"${configurationType!!.displayName}\" is not valid: id is empty")
    }
    else {
      LOG.error("Configuration factory \"${factory.name}\" is not valid: id is empty")
    }
    return null
  }

  var builder: StringBuilder? = null
  var i = 0
  var isAllUpperCased = true
  while (i < result.length) {
    val ch = result[i]
    @Suppress("IfThenToSafeAccess")
    if (ch == '.' || ch == ' ' || ch == '-' || ch == '_') {
      if (builder == null) {
        builder = StringBuilder()
        builder.append(result, 0, i)
      }

      i++
      if (i == result.length) {
        break
      }
      else {
        builder.append(result[i].toUpperCase())
        i++
        continue
      }
    }
    else if (ch == '#') {
      i++
      continue
    }
    else if (ch == '"' || ch == '\'' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\b' || ch == '/' || ch == '\\') {
      if (factory == null) {
        LOG.error("Configuration type \"${configurationType!!.id}\" is not valid: contains invalid symbol \"$ch\"")
      }
      else {
        // todo for factory historically was no id (opposite to type), so, in most cases name can contain invalid chars, may be should be ignored instead of error?
        LOG.error("Configuration factory \"${factory.name}\" is not valid: contains invalid symbol \"$ch\"")
      }
      return null
    }
    else if (i == 0) {
      if (ch.isUpperCase()) {
        if (builder == null) {
          builder = StringBuilder()
          builder.append(result, 0, i)
        }
        builder.append(ch.toLowerCase())
      }
      else {
        isAllUpperCased = false
      }
    }
    else if (builder != null) {
      builder.append(ch)
    }

    if (!ch.isUpperCase()) {
      isAllUpperCased = false
    }

    i++
  }

  if (isAllUpperCased) {
    if (builder == null) {
      return result.toLowerCase()
    }
    else {
      @Suppress("NAME_SHADOWING")
      for (i in 0 until builder.length) {
        builder.setCharAt(i, builder.get(i).toLowerCase())
      }
      return builder
    }
  }
  else {
    return builder ?: result
  }
}