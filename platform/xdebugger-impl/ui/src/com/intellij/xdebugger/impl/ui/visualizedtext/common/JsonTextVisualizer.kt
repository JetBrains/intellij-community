// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.visualizedtext.TextBasedContentTab
import com.intellij.xdebugger.impl.ui.visualizedtext.TextVisualizerContentType
import com.intellij.xdebugger.impl.ui.visualizedtext.VisualizedContentTabWithStats
import com.intellij.xdebugger.ui.TextValueVisualizer
import com.intellij.xdebugger.ui.VisualizedContentTab

internal class JsonTextVisualizer : TextValueVisualizer {

  override fun visualize(value: @NlsSafe String): List<VisualizedContentTab> {
    val json = detectJson(value)
    if (json == null) return emptyList()

    return listOf(object : TextBasedContentTab(), VisualizedContentTabWithStats {
      override val name
        get() = XDebuggerBundle.message("xdebugger.visualized.text.name.json")
      override val id
        get() = JsonTextVisualizer::class.qualifiedName!!
      override val contentTypeForStats
        get() = TextVisualizerContentType.JSON
      override fun formatText() =
        JsonEncodingUtil.prettifyJson(json)
      override val fileType
        get() = jsonFileType
    })
  }

  override fun detectFileType(value: @NlsSafe String): FileType? =
    if (detectJson(value) != null) jsonFileType else null

  private fun detectJson(value: String): JsonNode? {
    // Visualize only complex JSON, it's useless to visualize 123 as JSON primitive.
    val firstChar = value.firstOrNull { !it.isWhitespace() }
    if (firstChar != '[' && firstChar != '{') return null

    // Also no need to visualize empty arrays and dictionaries as JSON.
    if (value == "[]" || value == "{}") return null

    return JsonEncodingUtil.tryParseJson(value)
  }

  private val jsonFileType
    get() =
      // Right now we don't want to have an explicit static dependency here.
      // In an ideal world, this class would be part of the optional module of the debugger plugin with a dependency on intellij.json.
      FileTypeManager.getInstance().getStdFileType("JSON")
}

internal object JsonEncodingUtil {
  private fun objectMapper() = ObjectMapper()

  fun tryParseJson(value: String): JsonNode? =
    try {
      objectMapper()
        .readTree(value)
    } catch (_: JsonProcessingException) {
      null
    }

  fun prettifyJson(element: JsonNode): String =
    objectMapper()
      .writerWithDefaultPrettyPrinter()
      .writeValueAsString(element)
}