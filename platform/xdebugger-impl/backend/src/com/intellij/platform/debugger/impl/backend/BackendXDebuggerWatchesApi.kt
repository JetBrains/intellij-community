// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.xdebugger.impl.frame.BackendXValueModel
import com.intellij.xdebugger.impl.rpc.XDebuggerWatchesApi
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BackendXDebuggerWatchesApi : XDebuggerWatchesApi {
  override suspend fun addXValueWatch(xValueId: XValueId) {
    val xValueModel = BackendXValueModel.findById(xValueId) ?: return
    val xValue = xValueModel.xValue
    val session = xValueModel.session
    withContext(Dispatchers.EDT) {
      val watchesView = session.sessionTab?.watchesView
      if (watchesView != null) {
        DebuggerUIUtil.addToWatches(watchesView, xValue)
      }
    }
  }
}