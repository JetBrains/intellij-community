// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.application.EDT
import com.intellij.platform.debugger.impl.rpc.XFullValueEvaluatorDto
import com.intellij.platform.debugger.impl.rpc.XFullValueEvaluatorResult
import com.intellij.platform.debugger.impl.rpc.XValueApi
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.impl.rpc.XValueId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.util.function.Supplier

internal class FrontendXFullValueEvaluator(
  private val xValueCs: CoroutineScope,
  private val xValueId: XValueId,
  private val dto: XFullValueEvaluatorDto,
) : XFullValueEvaluator() {
  private val linkAttributes = dto.attributes?.let {
    LinkAttributes(it.tooltipText, it.shortcut?.let { shortcut -> Supplier { shortcut } }, it.linkIcon?.icon())
  }

  private var isShowValuePopup = dto.isShowValuePopup

  private var isEnabled = dto.isEnabled

  override fun isShowValuePopup(): Boolean {
    return isShowValuePopup
  }

  override fun isEnabled(): Boolean {
    return isEnabled
  }

  override fun setShowValuePopup(value: Boolean): XFullValueEvaluator {
    isShowValuePopup = value
    return this
  }

  override fun setIsEnabled(value: Boolean): XFullValueEvaluator {
    isEnabled = value
    return this
  }

  override fun getLinkText(): @Nls String {
    return dto.linkText
  }

  override fun getLinkAttributes(): LinkAttributes? {
    return linkAttributes
  }

  override fun startEvaluation(callback: XFullValueEvaluationCallback) {
    callback.childCoroutineScope(parentScope = xValueCs, "XFullValueEvaluationCallback").launch(Dispatchers.EDT) {
      XValueApi.getInstance().evaluateFullValue(xValueId).collect { result ->
        when (result) {
          is XFullValueEvaluatorResult.Evaluated -> {
            callback.evaluated(result.fullValue)
          }
          is XFullValueEvaluatorResult.EvaluationError -> {
            callback.errorOccurred(result.errorMessage)
          }
        }
      }
    }
  }
}