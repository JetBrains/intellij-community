// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.ui.JBColor
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.rpc.XDebuggerValueMarkupApi
import com.intellij.xdebugger.impl.rpc.XValueMarkerDto
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise

internal class FrontendXValueMarkers<V : XValue, M>(private val project: Project) : XValueMarkers<V, M>() {
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
    return project.service<FrontendXValueMarkersService>().markValue(value, markup)
  }

  override fun unmarkValue(value: XValue): Promise<in Any>? {
    return project.service<FrontendXValueMarkersService>().unmarkValue(value)
  }

  override fun getAllMarkers(): Map<M?, ValueMarkup?>? {
    // TODO[IJPL-160146]: Implement getAllMarkers
    return mapOf()
  }

  override fun clear() {
    // TODO[IJPL-160146]: Implement clear
  }
}

@Service(Service.Level.PROJECT)
private class FrontendXValueMarkersService(project: Project, private val cs: CoroutineScope) {
  fun markValue(value: XValue, markup: ValueMarkup): Promise<Any> {
    val valueMarked = cs.async {
      val marker = XValueMarkerDto(markup.text, markup.color, markup.toolTipText)
      XDebuggerValueMarkupApi.getInstance().markValue((value as FrontendXValue).xValueDto.id, marker)
      marker as Any
    }
    return valueMarked.asCompletableFuture().asPromise()
  }

  fun unmarkValue(value: XValue): Promise<in Any>? {
    val valueUnmarked = cs.async {
      XDebuggerValueMarkupApi.getInstance().unmarkValue((value as FrontendXValue).xValueDto.id)
      Any()
    }
    return valueUnmarked.asCompletableFuture().asPromise()
  }
}