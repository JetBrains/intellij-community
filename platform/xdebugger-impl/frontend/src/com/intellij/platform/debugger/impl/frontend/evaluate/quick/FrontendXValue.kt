// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.openapi.application.EDT
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorApi
import com.intellij.xdebugger.impl.rpc.XValueId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal class FrontendXValue(private val scope: CoroutineScope, private val xValueId: XValueId) : XValue() {
  override fun computePresentation(node: XValueNode, place: XValuePlace) {
    scope.launch(Dispatchers.EDT) {
      XDebuggerEvaluatorApi.getInstance().computePresentation(xValueId)?.collect { presentation ->
        // TODO: pass proper params
        node.setPresentation(null, null, presentation.value, false)
      }
    }
  }
}