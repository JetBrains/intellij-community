// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.util.NlsSafe
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.visualizedtext.TextBasedContentTab
import com.intellij.xdebugger.impl.ui.visualizedtext.TextVisualizerContentType
import com.intellij.xdebugger.impl.ui.visualizedtext.VisualizedContentTabWithStats
import com.intellij.xdebugger.ui.TextValueVisualizer
import com.intellij.xdebugger.ui.VisualizedContentTab
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

internal class JwtTextVisualizer : TextValueVisualizer {
  override fun visualize(value: @NlsSafe String): List<VisualizedContentTab> {
    val jwt = tryParse(value)
    if (jwt == null) return emptyList()

    return listOf(object : TextBasedContentTab(), VisualizedContentTabWithStats {
      override val name
        get() = XDebuggerBundle.message("xdebugger.visualized.text.name.jwt")
      override val id
        get() = JwtTextVisualizer::class.qualifiedName!!
      override val contentTypeForStats
        get() = TextVisualizerContentType.JWT
      override fun formatText() =
        prettify(jwt)
      override val fileType
        get() =
          // Right now we don't want to have an explicit static dependency here.
          // In an ideal world this class would be part of the optional module of the debugger plugin with a dependency on intellij.json.
          FileTypeManager.getInstance().getStdFileType("JSON")
    })
  }

  private data class JWT(val header: JsonNode,
                         val payload: JsonNode,
                         val signature: String)

  private fun tryParse(value: String): JWT? {
    val parts = value.split('.')
    if (parts.size != 3) return null

    // should be inline but KT-17579
    fun parsePart(i: Int): JsonNode? =
      tryDecodeBase64(parts[i])?.let { JsonEncodingUtil.tryParseJson(it) }

    return JWT(
      parsePart(0) ?: return null,
      parsePart(1) ?: return null,
      parts[2])
  }

  private fun prettify(jwt: JWT): String {
    val factory = JsonNodeFactory.instance
    val json = factory.objectNode().apply {
      set<JsonNode>("header", jwt.header)
      set<JsonNode>("payload", jwt.payload)
      put("signature", jwt.signature)
    }
    return JsonEncodingUtil.prettifyJson(json)
  }

  @OptIn(ExperimentalEncodingApi::class)
  private fun tryDecodeBase64(s: String): String? {
    val decoded = try {
      Base64.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL).decode(s)
    } catch (_: IllegalArgumentException) {
      return null
    }
    return String(decoded, Charsets.UTF_8)
  }

}
