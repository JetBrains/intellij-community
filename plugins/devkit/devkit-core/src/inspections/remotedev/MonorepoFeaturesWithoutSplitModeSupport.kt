// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package org.jetbrains.idea.devkit.inspections.remotedev

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.json.JsonMapper
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

internal const val MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_FILE_NAME: String = "MonorepoFeaturesWithoutSplitModeSupport.json5"
internal const val MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RESOURCE_PATH: String =
  "remotedevInspectionData/$MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_FILE_NAME"
internal const val MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH: String =
  "community/plugins/devkit/devkit-core/resources/$MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RESOURCE_PATH"

internal const val SPLIT_MODE_EXCLUDED_MODULES_SCOPE_FILE_NAME: String = "SplitModeExcludedModules.xml"
internal val SPLIT_MODE_EXCLUDED_MODULES_SCOPE_RELATIVE_PATHS: List<String> = listOf(
  ".idea/scopes/$SPLIT_MODE_EXCLUDED_MODULES_SCOPE_FILE_NAME",
  ".idea.bazel/scopes/$SPLIT_MODE_EXCLUDED_MODULES_SCOPE_FILE_NAME",
)

internal fun createSplitModeExcludedModulesScopeXml(
  resourceReader: SplitModeInspectionResourceReader,
  readMode: SplitModeInspectionResourceReadMode,
): String {
  val configurationText = requireNotNull(
    resourceReader.readText(MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RESOURCE_PATH, readMode)
  ) {
    "Cannot load $MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH"
  }
  return createSplitModeExcludedModulesScopeXml(configurationText)
}

internal fun createSplitModeExcludedModulesScopeXml(json5Text: String): String {
  val configuration = parseMonorepoFeaturesWithoutSplitModeSupport(json5Text)
  val scopePattern = createSplitModeExcludedModulesScopePattern(configuration)
  return """
    <component name="DependencyValidationManager">
      <scope name="SplitModeExcludedModules" pattern="${scopePattern.escapeXmlAttributeValue()}" />
    </component>
  """.trimIndent() + "\n"
}

private fun parseMonorepoFeaturesWithoutSplitModeSupport(json5Text: String): MonorepoFeaturesWithoutSplitModeSupportFile {
  val normalizedJson = try {
    json5.readTree(json5Text).toString()
  }
  catch (e: JsonProcessingException) {
    throw IllegalArgumentException("Cannot parse $MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH", e)
  }
  catch (e: IOException) {
    throw IllegalArgumentException("Cannot parse $MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH", e)
  }

  val configuration = try {
    json.decodeFromString<MonorepoFeaturesWithoutSplitModeSupportFile>(normalizedJson)
  }
  catch (e: SerializationException) {
    throw IllegalArgumentException("Cannot parse $MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH", e)
  }
  catch (e: IllegalArgumentException) {
    throw IllegalArgumentException("Cannot parse $MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH", e)
  }

  validateEntries("pathPatterns", configuration.pathPatterns) { entry ->
    require(!entry.startsWith("file:")) {
      "pathPatterns entries must not include the generated 'file:' prefix: $entry"
    }
  }
  validateEntries("moduleNames", configuration.moduleNames) { entry ->
    require('/' !in entry && '[' !in entry && ']' !in entry) {
      "moduleNames entries must be raw module names: $entry"
    }
  }
  return configuration
}

private fun createSplitModeExcludedModulesScopePattern(configuration: MonorepoFeaturesWithoutSplitModeSupportFile): String {
  val pathPatterns = configuration.pathPatterns.map { "file:$it" }
  val modulePatterns = configuration.moduleNames.map { "file[$it]:*/" }
  val patterns = pathPatterns + modulePatterns
  require(patterns.isNotEmpty()) {
    "$MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH must define at least one pathPatterns or moduleNames entry"
  }
  require(patterns.size == patterns.toSet().size) {
    "$MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH contains entries that generate duplicate scope patterns"
  }
  return patterns.joinToString("||")
}

private fun validateEntries(
  fieldName: String,
  entries: List<String>,
  validateEntry: (String) -> Unit,
) {
  val duplicates = entries.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
  require(duplicates.isEmpty()) {
    "$MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH contains duplicate $fieldName entries: ${duplicates.joinToString()}"
  }

  for (entry in entries) {
    require(entry.isNotBlank()) {
      "$MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH contains blank $fieldName entry"
    }
    require(entry == entry.trim()) {
      "$MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH contains $fieldName entry with leading or trailing whitespace: $entry"
    }
    require("||" !in entry) {
      "$MONOREPO_FEATURES_WITHOUT_SPLIT_MODE_SUPPORT_RELATIVE_PATH entries must not contain scope pattern separators: $entry"
    }
    validateEntry(entry)
  }
}

private fun String.escapeXmlAttributeValue(): String {
  return buildString(length) {
    for (character in this@escapeXmlAttributeValue) {
      when (character) {
        '&' -> append("&amp;")
        '"' -> append("&quot;")
        '<' -> append("&lt;")
        '>' -> append("&gt;")
        else -> append(character)
      }
    }
  }
}

@Serializable
private data class MonorepoFeaturesWithoutSplitModeSupportFile(
  @SerialName("pathPatterns")
  val pathPatterns: List<String> = emptyList(),

  @SerialName("moduleNames")
  val moduleNames: List<String> = emptyList(),
)

private val json5: ObjectMapper = JsonMapper.builder()
  .enable(
    JsonReadFeature.ALLOW_JAVA_COMMENTS,
    JsonReadFeature.ALLOW_SINGLE_QUOTES,
    JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES,
    JsonReadFeature.ALLOW_TRAILING_COMMA,
  )
  .build()

private val json = Json {
  ignoreUnknownKeys = true
  isLenient = true
}
