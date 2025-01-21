// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.ui.JBColor
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise

internal class FrontendXValueMarkers<V : XValue, M> : XValueMarkers<V, M>() {
  override fun getMarkup(value: XValue): ValueMarkup? {
    val markerDto = (value as? FrontendXValue)?.markerDto ?: return null
    // TODO[IJPL-160146]: Implement implement Color serialization
    return ValueMarkup(markerDto.text, markerDto.color ?: JBColor.RED, markerDto.tooltipText)
  }

  override fun canMarkValue(value: XValue): Boolean {
    // TODO[IJPL-160146]: Implement canMarkValue
    return true
  }

  override fun markValue(value: XValue, markup: ValueMarkup): Promise<in Any>? {
    // TODO[IJPL-160146]: Implement markValue
    return resolvedPromise<Any?>()
  }

  override fun unmarkValue(value: XValue): Promise<in Any>? {
    // TODO[IJPL-160146]: Implement unmarkValue
    return resolvedPromise<Any?>()
  }

  override fun getAllMarkers(): Map<M?, ValueMarkup?>? {
    // TODO[IJPL-160146]: Implement getAllMarkers
    return mapOf()
  }

  override fun clear() {
    // TODO[IJPL-160146]: Implement clear
  }
}