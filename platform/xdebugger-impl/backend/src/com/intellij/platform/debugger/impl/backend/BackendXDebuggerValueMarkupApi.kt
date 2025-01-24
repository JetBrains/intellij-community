// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ui.JBColor
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.handlers.XMarkObjectActionHandler.Companion.updateXValuesInDb
import com.intellij.xdebugger.impl.rhizome.XValueEntity
import com.intellij.xdebugger.impl.rhizome.XValueMarkerDto
import com.intellij.xdebugger.impl.rpc.XDebuggerValueMarkupApi
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.ui.tree.ValueMarkup
import com.jetbrains.rhizomedb.entity
import org.jetbrains.concurrency.await

internal class BackendXDebuggerValueMarkupApi : XDebuggerValueMarkupApi {
  override suspend fun markValue(xValueId: XValueId, markerDto: XValueMarkerDto) {
    val xValueEntity = entity(XValueEntity.XValueId, xValueId) ?: return
    val session = xValueEntity.sessionEntity.session
    val markers = (session as XDebugSessionImpl).getValueMarkers() ?: return

    val markup = ValueMarkup(markerDto.text, markerDto.color ?: JBColor.RED, markerDto.tooltipText)
    markers.markValue(xValueEntity.xValue, markup).await()
    updateXValuesInDb(markers, session)
  }

  override suspend fun unmarkValue(xValueId: XValueId) {
    val xValueEntity = entity(XValueEntity.XValueId, xValueId) ?: return
    val session = xValueEntity.sessionEntity.session
    val markers = (session as XDebugSessionImpl).getValueMarkers() ?: return

    markers.unmarkValue(xValueEntity.xValue).await()
    updateXValuesInDb(markers, session)
  }
}

