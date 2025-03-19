// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.rhizome.XValueEntity
import com.intellij.xdebugger.impl.rpc.XDebuggerWatchesApi
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.jetbrains.rhizomedb.entity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BackendXDebuggerWatchesApi : XDebuggerWatchesApi {
  override suspend fun addXValueWatch(xValueId: XValueId) {
    val xValueEntity = entity(XValueEntity.XValueId, xValueId) ?: return
    val xValue = xValueEntity.xValue
    val session = xValueEntity.sessionEntity.session
    withContext(Dispatchers.EDT) {
      val watchesView = (session as XDebugSessionImpl).sessionTab?.watchesView
      if (watchesView != null) {
        DebuggerUIUtil.addToWatches(watchesView, xValue)
      }
    }
  }
}