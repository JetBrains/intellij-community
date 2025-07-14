// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.platform.debugger.impl.rpc.XDebuggerValueMarkupApi
import com.intellij.platform.debugger.impl.rpc.XValueMarkerDto
import com.intellij.ui.JBColor
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModel
import com.intellij.xdebugger.impl.rpc.models.BackendXValueModelsManager
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup
import org.jetbrains.concurrency.await

internal class BackendXDebuggerValueMarkupApi : XDebuggerValueMarkupApi {
  override suspend fun markValue(xValueId: XValueId, markerDto: XValueMarkerDto) {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return
    val session = xValueModel.session
    val markers = session.getValueMarkers() ?: return

    val markup = ValueMarkup(markerDto.text, markerDto.color ?: JBColor.RED, markerDto.tooltipText)
    markers.markValue(xValueModel.xValue, markup).await()
    updateMarkersForAllXValueModels(markers, session)
  }

  override suspend fun unmarkValue(xValueId: XValueId) {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return
    val session = xValueModel.session
    val markers = session.getValueMarkers() ?: return

    markers.unmarkValue(xValueModel.xValue).await()
    updateMarkersForAllXValueModels(markers, session)
  }

  private fun updateMarkersForAllXValueModels(markers: XValueMarkers<*, *>, session: XDebugSessionImpl) {
    val sessionXValueModels = BackendXValueModelsManager.getInstance(session.project).getXValueModelsForSession(session)
    // TODO[IJPL-160146]: Don't update all the xValues, since some markers may not be changed
    for (sessionXValueModel in sessionXValueModels) {
      val marker = markers.getMarkup(sessionXValueModel.xValue)
      sessionXValueModel.setMarker(marker)
    }
  }
}

