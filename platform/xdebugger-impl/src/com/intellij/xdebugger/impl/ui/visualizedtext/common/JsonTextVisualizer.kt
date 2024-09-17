// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
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
    // Visualize only complex JSON, it's useless to visualize 123 as JSON primitive.
    val firstChar = value.firstOrNull { !it.isWhitespace() }
    if (firstChar != '[' && firstChar != '{') return emptyList()

    // Also no need to visualize empty arrays and dictionaries as JSON.
    if (value == "[]" || value == "{}") return emptyList()

    val json = JsonEncodingUtil.tryParseJson(value)
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
        get() =
          // Right now we don't want to have an explicit static dependency here.
          // In an ideal world this class would be part of the optional module of the debugger plugin with a dependency on intellij.json.
          FileTypeManager.getInstance().getStdFileType("JSON")
    })
  }
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