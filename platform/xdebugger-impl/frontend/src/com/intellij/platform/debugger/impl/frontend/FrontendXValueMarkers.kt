// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.colors.rpcId
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.platform.debugger.impl.rpc.XDebuggerValueMarkupApi
import com.intellij.platform.debugger.impl.rpc.XValueMarkerDto
import com.intellij.xdebugger.frame.XValue
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.future.asCompletableFuture
import kotlinx.coroutines.launch
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise

internal class FrontendXValueMarkers<V : XValue, M>(private val project: Project) : XValueMarkers<V, M>() {
  override fun getMarkup(value: XValue): ValueMarkup? {
    val markerDto = FrontendXValue.asFrontendXValueOrNull(value)?.markerDto ?: return null
    return ValueMarkup(markerDto.text, markerDto.colorId?.color(), markerDto.tooltipText)
  }

  override fun canMarkValue(value: XValue): Boolean {
    return FrontendXValue.asFrontendXValueOrNull(value)?.canMarkValue ?: false
  }

  override fun markValue(value: XValue, markup: ValueMarkup): Promise<in Any> {
    return project.service<FrontendXValueMarkersService>().markValue(value, markup)
  }

  override fun unmarkValue(value: XValue): Promise<in Any> {
    return project.service<FrontendXValueMarkersService>().unmarkValue(value)
  }

  override fun getAllMarkers(): Map<M?, ValueMarkup?> {
    // TODO[IJPL-160146]: Implement getAllMarkers
    return mapOf()
  }

  override fun clear() {
    val debugSessionProxy = XDebugManagerProxy.getInstance().getCurrentSessionProxy(project) ?: return
    project.service<FrontendXValueMarkersService>().clear(debugSessionProxy)
  }
}

@Service(Service.Level.PROJECT)
private class FrontendXValueMarkersService(private val cs: CoroutineScope) {
  fun markValue(value: XValue, markup: ValueMarkup): Promise<Any> {
    val valueMarked = cs.async {
      val marker = XValueMarkerDto(markup.text, markup.color.rpcId(), markup.toolTipText)
      val xValueId = XDebugManagerProxy.getInstance().getXValueId(value) ?: return@async Any()
      XDebuggerValueMarkupApi.getInstance().markValue(xValueId, marker)
      marker as Any
    }
    return valueMarked.asCompletableFuture().asPromise()
  }

  fun unmarkValue(value: XValue): Promise<in Any> {
    val valueUnmarked = cs.async {
      val xValueId = XDebugManagerProxy.getInstance().getXValueId(value) ?: return@async Any()
      XDebuggerValueMarkupApi.getInstance().unmarkValue(xValueId)
      Any()
    }
    return valueUnmarked.asCompletableFuture().asPromise()
  }

  fun clear(debugSessionProxy: XDebugSessionProxy) {
    cs.launch {
      XDebuggerValueMarkupApi.getInstance().clear(debugSessionProxy.id)
    }
  }
}