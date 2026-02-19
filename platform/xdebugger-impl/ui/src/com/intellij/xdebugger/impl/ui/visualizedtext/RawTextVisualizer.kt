// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.visualizedtext

import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.util.NlsSafe
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.ui.TextValueVisualizer
import com.intellij.xdebugger.ui.VisualizedContentTab

// It's not registered as an extension, added explicitly as the last visualizer.
internal object RawTextVisualizer : TextValueVisualizer {
  override fun visualize(value: @NlsSafe String): List<VisualizedContentTab> =
    listOf(object : TextBasedContentTab(), VisualizedContentTabWithStats {
      override val name
        get() = XDebuggerBundle.message("xdebugger.visualized.text.name.raw")
      override val id
        get() = RawTextVisualizer::class.qualifiedName!!
      override val contentTypeForStats
        get() = TextVisualizerContentType.RAW
      override fun formatText() =
        value
      override val fileType
        get() = FileTypes.PLAIN_TEXT
    })
}
