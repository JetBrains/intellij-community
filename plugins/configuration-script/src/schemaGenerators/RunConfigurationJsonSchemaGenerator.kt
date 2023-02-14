package com.intellij.configurationScript.schemaGenerators

import com.intellij.configurationScript.Keys
import com.intellij.configurationScript.LOG
import com.intellij.configurationScript.SchemaGenerator
import com.intellij.execution.configurations.ConfigurationFactory
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.RunConfigurationOptions
import com.intellij.openapi.components.BaseState
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.ReflectionUtil
import org.jetbrains.io.JsonObjectBuilder

internal inline fun processConfigurationTypes(processor: (configurationType: ConfigurationType, propertyName: CharSequence, factories: Array<ConfigurationFactory>) -> Unit) {
  for (type in ConfigurationType.CONFIGURATION_TYPE_EP.extensionList) {
    val propertyName = rcTypeIdToPropertyName(type) ?: continue
    val factories = type.configurationFactories
    if (factories.isEmpty()) {
      LOG.error("Configuration type \"${type.displayName}\" is not valid: factory list is empty")
      continue
    }

    processor(type, propertyName, factories)
  }
}

private inline fun processFactories(factories: Array<ConfigurationFactory>,
                                    typeDefinitionId: CharSequence,
                                    processor: (factoryPropertyName: CharSequence, factoryDefinitionId: CharSequence, factory: ConfigurationFactory) -> Unit) {
  for (factory in factories) {
    val factoryPropertyName = rcFactoryIdToPropertyName(factory) ?: continue
    val factoryDefinitionId = "${typeDefinitionId}-${factoryPropertyName}Factory"
    processor(factoryPropertyName, factoryDefinitionId, factory)
  }
}

internal class RunConfigurationJsonSchemaGenerator : SchemaGenerator {
  private val objectSchemaGenerator = OptionClassJsonSchemaGenerator("runConfigurationDefinitions")

  override val definitionNodeKey: CharSequence
    get() = objectSchemaGenerator.definitionNodeKey

  override fun generate(rootBuilder: JsonObjectBuilder) {
    rootBuilder.map(Keys.runConfigurations) {
      definitionReference(objectSchemaGenerator.definitionPointerPrefix, Keys.runConfigurations)
    }
  }

  override fun generateDefinitions(): CharSequence {
    val properties = JsonObjectBuilder(StringBuilder(), indentLevel = 1)
    addTemplatesNode(properties)

    processConfigurationTypes { type, typePropertyName, factories ->
      val isMultiFactory = factories.size > 1
      val typeDefinitionId = generateTypeDefinitionId(typePropertyName)
      val typeDescription = getTypeDescription(type, typePropertyName)

      addPropertyForConfigurationType(properties, typePropertyName, isMultiFactory, typeDefinitionId)

      if (isMultiFactory) {
        processFactories(factories,
                                                                           typeDefinitionId) { factoryPropertyName, factoryDefinitionId, factory ->
          describeFactory(factory, factoryDefinitionId, if (StringUtil.equals(factoryPropertyName, factory.name)) null else factory.name)
        }

        objectSchemaGenerator.definitions.map(typeDefinitionId) {
          "type" to "object"

          if (typeDescription != null) {
            "description" toUnescaped typeDescription
          }

          map("properties") {
            processFactories(factories, typeDefinitionId) { factoryPropertyName, factoryDefinitionId, _ ->
              addPropertyForFactory(factoryPropertyName, factoryDefinitionId, isSingleChildOnly = false)
              // describeFactory cannot be here because JsonBuilder instance here equals to definitions - recursive building is not supported (to reuse StringBuilder instance)
            }
          }

          "additionalProperties" to false
        }
      }
      else {
        describeFactory(factories.first(), typeDefinitionId, typeDescription)
      }
    }

    // must be after generation
    objectSchemaGenerator.definitions.map(Keys.runConfigurations) {
      "type" to "object"
      "description" to "The run configurations"
      rawBuilder("properties", properties)
      "additionalProperties" to false
    }

    return objectSchemaGenerator.describe()
  }

  private fun addTemplatesNode(properties: JsonObjectBuilder) {
    val description = "The run configuration templates"
    properties.map(Keys.templates) {
      definitionReference(objectSchemaGenerator.definitionPointerPrefix, Keys.templates)
    }

    objectSchemaGenerator.definitions.map(Keys.templates) {
      "type" to "object"
      "description" toUnescaped description
      map("properties") {
        processConfigurationTypes { type, typePropertyName, factories ->
          val typeDefinitionId = generateTypeDefinitionId(typePropertyName)
          val typeDescription = getTypeDescription(type, typePropertyName)
          if (factories.size == 1) {
            addPropertyForConfigurationType(this, typePropertyName, true, typeDefinitionId)
          }
          else {
            // for multi-factory RC type we cannot simply reference to definition because the only child is expected (RC type cannot have more than one template)
            map(typePropertyName) {
              "type" to "object"

              if (typeDescription != null) {
                "description" toUnescaped typeDescription
              }

              map("properties") {
                processFactories(factories,
                                                                                   typeDefinitionId) { factoryPropertyName, factoryDefinitionId, _ ->
                  addPropertyForFactory(factoryPropertyName, factoryDefinitionId, isSingleChildOnly = true)
                }
              }
            }
          }
        }
      }
    }
  }

  private fun addPropertyForConfigurationType(properties: JsonObjectBuilder, typePropertyName: CharSequence, isSingleChildOnly: Boolean, definitionId: CharSequence) {
    properties.map(typePropertyName) {
      if (isSingleChildOnly) {
        "type" to "object"
        definitionReference(objectSchemaGenerator.definitionPointerPrefix, definitionId)
      }
      else {
        rawArray("oneOf") {
          it.json {
            definitionReference(objectSchemaGenerator.definitionPointerPrefix, definitionId)
          }
          it.json {
            "type" to "array"
            map("items") {
              definitionReference(objectSchemaGenerator.definitionPointerPrefix, definitionId)
            }
            "additionalProperties" to false
          }
        }
      }
    }
  }

  private fun JsonObjectBuilder.addPropertyForFactory(factoryPropertyName: CharSequence, factoryDefinitionId: CharSequence, isSingleChildOnly: Boolean) {
    map(factoryPropertyName) {
      if (isSingleChildOnly) {
        "type" to "object"
      }
      else {
        "type" toRaw """["array", "object"]"""

        map("items") {
          definitionReference(objectSchemaGenerator.definitionPointerPrefix, factoryDefinitionId)
        }
      }
      definitionReference(objectSchemaGenerator.definitionPointerPrefix, factoryDefinitionId)
    }
  }

  private fun describeFactory(factory: ConfigurationFactory, definitionId: CharSequence, description: String?) {
    val optionsClass = factory.optionsClass
    val state: BaseState
    if (optionsClass == null) {
      LOG.debug { "Configuration factory \"${factory.name}\" is not fully described because options class not defined" }
      // nor LocatableRunConfigurationOptions, neither ModuleBasedConfigurationOptions define any useful properties, so,
      // RunConfigurationOptions is enough without guessing actual RC type.
      state = RunConfigurationOptions()
    }
    else {
      state = ReflectionUtil.newInstance(optionsClass)
    }

    objectSchemaGenerator.definitions.map(definitionId) {
      "type" to "object"
      if (description != null) {
        "description" toUnescaped description
      }
      map("properties") {
        buildJsonSchema(state, this, objectSchemaGenerator) { name ->
          // we don't specify default value ("default") because it is tricky - not value from factory, but from RC template maybe used,
          // and on time when schema is generated, we cannot compute efficient default value
          if (name == "isAllowRunningInParallel") {
            factory.singletonPolicy.isPolicyConfigurable
          }
          else {
            true
          }
        }
      }
    }
    "additionalProperties" to false
  }
}

// returns null if id is not valid
internal fun rcTypeIdToPropertyName(configurationType: ConfigurationType): CharSequence? {
  val result = idToPropertyName(configurationType.tag, configurationType, null) ?: return null
  if (StringUtil.equals(result, Keys.templates)) {
    LOG.error("Configuration type \"${configurationType.displayName}\" has forbidden id")
  }
  return result
}

// returns null if id is not valid
internal fun rcFactoryIdToPropertyName(factory: ConfigurationFactory): CharSequence? {
  return idToPropertyName(factory.id, null, factory)
}

// returns null if id is not valid
private fun idToPropertyName(string: String, configurationType: ConfigurationType?, factory: ConfigurationFactory?): CharSequence? {
  if (string == "JetRunConfigurationType") {
    return "kotlin"
  }

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
      for (i in builder.indices) {
        builder.setCharAt(i, builder.get(i).toLowerCase())
      }
      return builder
    }
  }
  else {
    return builder ?: result
  }
}

private fun generateTypeDefinitionId(propertyName: CharSequence): String {
  return "${propertyName[0].toUpperCase()}${propertyName.substring(1)}Type"
}

private fun getTypeDescription(type: ConfigurationType, typePropertyName: CharSequence): String? {
  val description = type.configurationTypeDescription
  return if (StringUtil.equals(typePropertyName, description)) null else description
}