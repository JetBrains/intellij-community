// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.platform.debugger.impl.rpc.XValueAdvancedPresentationPart
import com.intellij.platform.debugger.impl.rpc.XValueSerializedPresentation
import com.intellij.ui.SimpleColoredText
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueTextRendererImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun XValueSerializedPresentation.AdvancedPresentation.renderValue(renderer: XValuePresentation.XValueTextRenderer) {
  for (part in parts) {
    when (part) {
      is XValueAdvancedPresentationPart.Comment -> renderer.renderComment(part.text)
      is XValueAdvancedPresentationPart.Error -> renderer.renderError(part.text)
      is XValueAdvancedPresentationPart.KeywordValue -> renderer.renderKeywordValue(part.text)
      is XValueAdvancedPresentationPart.NumericValue -> renderer.renderNumericValue(part.text)
      is XValueAdvancedPresentationPart.SpecialSymbol -> renderer.renderSpecialSymbol(part.text)
      is XValueAdvancedPresentationPart.StringValue -> renderer.renderStringValue(part.text)
      is XValueAdvancedPresentationPart.StringValueWithHighlighting -> {
        renderer.renderStringValue(part.text, part.additionalSpecialCharsToHighlight, part.maxLength)
      }
      is XValueAdvancedPresentationPart.Value -> renderer.renderValue(part.text)
      is XValueAdvancedPresentationPart.ValueWithAttributes -> {
        val attributesKey = part.key
        if (attributesKey != null) {
          renderer.renderValue(part.text, attributesKey)
        }
        else {
          renderer.renderValue(part.text)
        }
      }
    }
  }
}

@ApiStatus.Internal
fun XValueSerializedPresentation.AdvancedPresentation.computeValueText(): String {
  val text = SimpleColoredText()
  renderValue(XValueTextRendererImpl(text))
  return text.toString()
}
