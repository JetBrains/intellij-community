// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.codeVision

import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.codeVision.ui.model.CodeVisionListData
import com.intellij.codeInsight.codeVision.ui.renderers.CodeVisionRenderer
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.utils.inlays.InlayHintsProviderTestCase
import java.util.regex.Pattern

abstract class CodeVisionTestCase : InlayHintsProviderTestCase() {
  override fun setUp() {
    Registry.get("editor.codeVision.new").setValue(true, testRootDisposable)
    TestModeFlags.set(CodeVisionHost.isCodeVisionTestKey, true, testRootDisposable)
    super.setUp()
  }

  protected open val onlyCodeVisionHintsAllowed: Boolean
    get() = true

  protected fun testProviders(expectedText: String, fileName: String, vararg enabledProviderIds: String) {
    // set enabled providers
    val settings = CodeVisionSettings.instance()
    val codeVisionHost = CodeVisionInitializer.getInstance(project).getCodeVisionHost()
    codeVisionHost.providers.forEach {
      settings.setProviderEnabled(it.id, enabledProviderIds.contains(it.id))
    }

    val sourceText = CodeVisionInlayData.pattern.matcher(expectedText).replaceAll("")
    myFixture.configureByText(fileName, sourceText)

    val editor = myFixture.editor
    project.putUserData(CodeVisionHost.isCodeVisionTestKey, true)
    codeVisionHost.providers.forEach {
      if (it.id == "vcs.code.vision" && enabledProviderIds.contains(it.id)) {
        it.preparePreview(myFixture.editor, myFixture.file)
      }
    }
    myFixture.doHighlighting()

    codeVisionHost.calculateCodeVisionSync(editor, testRootDisposable)

    val actualText = dumpCodeVisionHints(sourceText)
    assertEquals(expectedText, actualText)
  }

  private fun dumpCodeVisionHints(sourceText: String): String {
    val file = myFixture.file!!
    val editor = myFixture.editor
    val model = editor.inlayModel
    val range = file.textRange
    val inlineElements = model.getInlineElementsInRange(range.startOffset, range.endOffset)
    val afterLineElements = model.getAfterLineEndElementsInRange(range.startOffset, range.endOffset)
    val blockElements = model.getBlockElementsInRange(range.startOffset, range.endOffset)
    val inlays = mutableListOf<CodeVisionInlayData>()
    inlineElements.mapTo(inlays) { CodeVisionInlayData(it, InlayType.Inline) }
    afterLineElements.mapTo(inlays) { CodeVisionInlayData(it, InlayType.Inline) }
    blockElements.mapTo(inlays) { CodeVisionInlayData(it, InlayType.Block) }
    val document = myFixture.getDocument(file)
    inlays.sortBy { it.effectiveOffset(document) }
    return buildString {
      var currentOffset = 0
      for (inlay in inlays) {
        if (inlay.inlay.renderer !is CodeVisionRenderer) {
          if (onlyCodeVisionHintsAllowed) error("renderer not supported")
          else continue
        }
        val nextOffset = inlay.effectiveOffset(document)
        append(sourceText.subSequence(currentOffset, nextOffset))
        append(inlay)
        currentOffset = nextOffset
      }
      append(sourceText.substring(currentOffset, sourceText.length))
    }
  }

  private data class CodeVisionInlayData(val inlay: Inlay<*>, val type: InlayType) {
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
      return buildString {
        append("<# ")
        if (type == InlayType.Block) {
          append("block ")
        }
        append(inlay.getUserData(CodeVisionListData.KEY)?.visibleLens?.joinToString(prefix = "[", postfix = "]", separator = "   ") { it.longPresentation })
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
}