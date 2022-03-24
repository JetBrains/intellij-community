// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework.utils.inlays

import com.intellij.codeInsight.hints.CollectorWithSettings
import com.intellij.codeInsight.hints.InlayHintsProvider
import com.intellij.codeInsight.hints.InlayHintsSinkImpl
import com.intellij.codeInsight.hints.LinearOrderInlayRenderer
import com.intellij.codeInsight.hints.presentation.PresentationRenderer
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Inlay
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase.HintPresence.NO_HINTS
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase.HintPresence.SOME_HINTS
import com.intellij.util.containers.isEmpty
import java.util.regex.Pattern

abstract class InlayHintsProviderTestCase : BasePlatformTestCase() {

  companion object {
    const val NO_HINTS_PREFIX = "// NO_HINTS"
  }

  private enum class HintPresence {
    NO_HINTS,
    SOME_HINTS
  }

  @JvmOverloads
  fun <T : Any> testProvider(fileName: String,
                             expectedText: String,
                             provider: InlayHintsProvider<T>,
                             settings: T = provider.createSettings(),
                             verifyHintPresence: Boolean = false) {
    val sourceText = InlayData.pattern.matcher(expectedText).replaceAll("")
    myFixture.configureByText(fileName, sourceText)
    val actualText = dumpInlayHints(sourceText, provider, settings)
    assertEquals(expectedText, actualText)

    if(verifyHintPresence) {
      val expectedHintPresence = if (expectedText.lineSequence().any { it.startsWith(NO_HINTS_PREFIX) }) NO_HINTS else SOME_HINTS
      val actualHintPresence = if (InlayData.pattern.matcher(expectedText).results().isEmpty()) NO_HINTS else SOME_HINTS
      assertEquals("Hint presence should match the use of the $NO_HINTS_PREFIX directive.", expectedHintPresence, actualHintPresence)
    }
  }

  protected fun <T : Any> dumpInlayHints(sourceText: String,
                                         provider: InlayHintsProvider<T>,
                                         settings: T = provider.createSettings()): String {
    val file = myFixture.file!!
    val editor = myFixture.editor
    val sink = InlayHintsSinkImpl(editor)
    val collector = provider.getCollectorFor(file, editor, settings, sink) ?: error("Collector is expected")
    val collectorWithSettings = CollectorWithSettings(collector, provider.key, file.language, sink)
    collectorWithSettings.collectTraversingAndApply(editor, file, true)
    val model = editor.inlayModel
    val range = file.textRange
    val inlineElements = model.getInlineElementsInRange(range.startOffset, range.endOffset)
    val afterLineElements = model.getAfterLineEndElementsInRange(range.startOffset, range.endOffset)
    val blockElements = model.getBlockElementsInRange(range.startOffset, range.endOffset)
    val inlays = mutableListOf<InlayData>()
    inlineElements.mapTo(inlays) { InlayData(it, InlayType.Inline) }
    afterLineElements.mapTo(inlays) { InlayData(it, InlayType.Inline) }
    blockElements.mapTo(inlays) { InlayData(it, InlayType.Block) }
    val document = myFixture.getDocument(file)
    inlays.sortBy { it.effectiveOffset(document) }
    return buildString {
      var currentOffset = 0
      for (inlay in inlays) {
        val nextOffset = inlay.effectiveOffset(document)
        append(sourceText.subSequence(currentOffset, nextOffset))
        append(inlay)
        currentOffset = nextOffset
      }
      append(sourceText.substring(currentOffset, sourceText.length))
    }
  }

  private data class InlayData(val inlay: Inlay<*>, val type: InlayType) {
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

    companion object {
      val pattern: Pattern = Pattern.compile("<# block ([^#]*)#>(\r\n|\r|\n)|<#([^#]*)#>")
    }
  }

 protected enum class InlayType {
    Inline,
    Block
  }
}