// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.inlays

import com.intellij.codeInsight.hints.LinearOrderInlayRenderer
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorCustomElementRenderer
import com.intellij.openapi.editor.Inlay
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.util.regex.Pattern

internal object InlayTestUtil {
  val inlayPattern: Pattern = Pattern.compile("<# block ([^#]*)#>(\r\n|\r|\n)|<#([^#]*)#>")

  internal fun dumpHintsInternal(
    sourceText: String,
    fixture: CodeInsightTestFixture,
    filter: ((Inlay<*>) -> Boolean)? = null,
    renderer: (EditorCustomElementRenderer, Inlay<*>) -> String = { r, _ -> r.toString() }
  ): String {
    val file = fixture.file!!
    val editor = fixture.editor
    val model = editor.inlayModel
    val range = file.textRange
    val inlineElements = model.getInlineElementsInRange(range.startOffset, range.endOffset)
    val afterLineElements = model.getAfterLineEndElementsInRange(range.startOffset, range.endOffset)
    val blockElements = model.getBlockElementsInRange(range.startOffset, range.endOffset)
    val inlays = mutableListOf<InlayData>()
    inlineElements.mapTo(inlays) { InlayData(it, InlayType.Inline) }
    afterLineElements.mapTo(inlays) { InlayData(it, InlayType.Inline) }
    blockElements.mapTo(inlays) { InlayData(it, InlayType.Block) }
    val document = fixture.getDocument(file)
    inlays.sortBy { it.effectiveOffset(document) }
    return buildString {
      var currentOffset = 0
      for (inlay in inlays) {
        if (filter != null) {
          if (!filter(inlay.inlay)) {
            continue
          }
        }
        val nextOffset = inlay.effectiveOffset(document)
        append(sourceText.subSequence(currentOffset, nextOffset))
        append(inlay.render(renderer))
        currentOffset = nextOffset
      }
      append(sourceText.substring(currentOffset, sourceText.length))
    }
  }

  internal data class InlayData(val inlay: Inlay<*>, val type: InlayType) {
    fun effectiveOffset(document: Document): Int {
      return when (type) {
        InlayType.Inline -> inlay.offset
        InlayType.Block -> {
          val offset = inlay.offset
          val lineNumber = document.getLineNumber(offset)
          document.getLineStartOffset(lineNumber)
        }
      }
    }

    fun render(r: (EditorCustomElementRenderer, Inlay<*>) -> String): String {
      return buildString {
        append("<# ")
        if (type == InlayType.Block) {
          append("block ")
        }
        append(r(inlay.renderer, inlay))
        append(" #>")
        if (type == InlayType.Block) {
          append('\n')
        }
      }
    }

    override fun toString(): String {
      val renderer = inlay.renderer
      if (renderer !is PresentationRenderer && renderer !is LinearOrderInlayRenderer<*>) error("renderer not supported")
      return buildString {
        append("<# ")
        if (type == InlayType.Block) {
          append("block ")
        }
        append(renderer.toString())
        append(" #>")
        if (type == InlayType.Block) {
          append('\n')
        }
      }
    }
  }

  enum class InlayType {
    Inline,
    Block
  }
}