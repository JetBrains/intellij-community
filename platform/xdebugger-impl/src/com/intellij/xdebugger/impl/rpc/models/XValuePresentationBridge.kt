// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc.models

import com.intellij.ide.ui.icons.rpcId
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.NlsSafe
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.frame.presentation.XValuePresentation.XValueTextRenderer
import com.intellij.xdebugger.impl.rpc.XValueAdvancedPresentationPart
import com.intellij.xdebugger.impl.rpc.XValueSerializedPresentation
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeEx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

internal fun XValue.computePresentation(
  cs: CoroutineScope,
  presentationHandler: (XValueSerializedPresentation) -> Unit,
  fullValueEvaluatorHandler: (XFullValueEvaluator?) -> Unit,
) {
  val xValue = this
  cs.launch {
    var isObsolete = false

    val valueNode = object : XValueNodeEx {
      override fun isObsolete(): Boolean {
        return isObsolete
      }

      override fun setPresentation(icon: Icon?, type: @NonNls String?, value: @NonNls String, hasChildren: Boolean) {
        presentationHandler(XValueSerializedPresentation.SimplePresentation(icon?.rpcId(), type, value, hasChildren))
      }

      override fun setPresentation(icon: Icon?, presentation: com.intellij.xdebugger.frame.presentation.XValuePresentation, hasChildren: Boolean) {
        val partsCollector = XValueTextRendererPartsCollector()
        presentation.renderValue(partsCollector)

        presentationHandler(XValueSerializedPresentation.AdvancedPresentation(
          icon?.rpcId(), hasChildren,
          presentation.separator, presentation.isShowName, presentation.type, presentation.isAsync,
          partsCollector.parts
        ))
      }

      override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) {
        fullValueEvaluatorHandler(fullValueEvaluator)
      }


      override fun getXValue(): XValue {
        return xValue
      }

      override fun clearFullValueEvaluator() {
        fullValueEvaluatorHandler(null)
      }
    }
    // TODO: for now XValuePlace.POPUP is not used at all by implementors,
    //  so let's use XValuePlace.TREE here for simplicity
    xValue.computePresentation(valueNode, XValuePlace.TREE)

    launch {
      try {
        awaitCancellation()
      }
      finally {
        isObsolete = true
      }
    }
  }
}


private class XValueTextRendererPartsCollector : XValueTextRenderer {
  private val _parts = mutableListOf<XValueAdvancedPresentationPart>()

  val parts: List<XValueAdvancedPresentationPart>
    get() = _parts

  override fun renderValue(value: @NlsSafe String) {
    _parts.add(XValueAdvancedPresentationPart.Value(value))
  }

  override fun renderStringValue(value: @NlsSafe String) {
    _parts.add(XValueAdvancedPresentationPart.StringValue(value))
  }

  override fun renderNumericValue(value: @NlsSafe String) {
    _parts.add(XValueAdvancedPresentationPart.NumericValue(value))
  }

  override fun renderKeywordValue(value: @NlsSafe String) {
    _parts.add(XValueAdvancedPresentationPart.KeywordValue(value))
  }

  override fun renderValue(value: @NlsSafe String, key: TextAttributesKey) {
    _parts.add(XValueAdvancedPresentationPart.ValueWithAttributes(value, key))
  }

  override fun renderStringValue(value: @NlsSafe String, additionalSpecialCharsToHighlight: @NlsSafe String?, maxLength: Int) {
    _parts.add(XValueAdvancedPresentationPart.StringValueWithHighlighting(value, additionalSpecialCharsToHighlight, maxLength))
  }

  override fun renderComment(comment: @NlsSafe String) {
    _parts.add(XValueAdvancedPresentationPart.Comment(comment))
  }

  override fun renderSpecialSymbol(symbol: @NlsSafe String) {
    _parts.add(XValueAdvancedPresentationPart.SpecialSymbol(symbol))
  }

  override fun renderError(error: @NlsSafe String) {
    _parts.add(XValueAdvancedPresentationPart.Error(error))
  }
}