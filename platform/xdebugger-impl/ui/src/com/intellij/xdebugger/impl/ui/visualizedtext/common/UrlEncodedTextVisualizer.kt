// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext.common

import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.util.NlsSafe
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.ui.visualizedtext.TextBasedContentTab
import com.intellij.xdebugger.impl.ui.visualizedtext.TextVisualizerContentType
import com.intellij.xdebugger.impl.ui.visualizedtext.VisualizedContentTabWithStats
import com.intellij.xdebugger.ui.TextValueVisualizer
import com.intellij.xdebugger.ui.VisualizedContentTab
import java.net.URLDecoder

internal class UrlEncodedTextVisualizer : TextValueVisualizer {
  override fun visualize(value: @NlsSafe String): List<VisualizedContentTab> {
    val decoded = tryParse(value)
    if (decoded == null) return emptyList()

    return listOf(object : TextBasedContentTab(), VisualizedContentTabWithStats {
      override val name
        get() = XDebuggerBundle.message("xdebugger.visualized.text.name.url")
      override val id
        get() = UrlEncodedTextVisualizer::class.qualifiedName!!
      override val contentTypeForStats
        get() = TextVisualizerContentType.URLEncoded
      override fun formatText() =
        decoded
      override val fileType
        get() = FileTypes.PLAIN_TEXT
    })
  }

  private fun tryParse(value: String): String? {
    if (!value.contains('%')) {
      // Not-URL or URL with only spaces, not so interesting but very annoying (i.e., no need to decode "123+456").
      return null
    }
    val decoded = try {
      URLDecoder.decode(value, Charsets.UTF_8)
    } catch (_: IllegalArgumentException) {
      return null
    }

    if (decoded == value) {
      // Not-URL or URL without escaped characters.
      return null
    }

    return decoded
  }

}
