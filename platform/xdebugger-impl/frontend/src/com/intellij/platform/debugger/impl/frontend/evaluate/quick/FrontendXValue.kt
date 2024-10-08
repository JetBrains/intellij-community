// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.openapi.application.EDT
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.ConcurrencyUtil
import com.intellij.xdebugger.Obsolescent
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorApi
import com.intellij.xdebugger.impl.rpc.XValueId
import kotlinx.coroutines.*

internal class FrontendXValue(private val xValueId: XValueId) : XValue() {
  override fun computePresentation(node: XValueNode, place: XValuePlace) {
    node.childCoroutineScope("FrontendXValue#computePresentation").launch(Dispatchers.EDT) {
      XDebuggerEvaluatorApi.getInstance().computePresentation(xValueId)?.collect { presentation ->
        // TODO: pass proper params
        node.setPresentation(null, null, presentation.value, false)
      }
    }
  }
}

@OptIn(DelicateCoroutinesApi::class)
private fun Obsolescent.childCoroutineScope(name: String): CoroutineScope {
  val obsolescent = this
  val scope = GlobalScope.childScope(name)
  scope.launch(context = Dispatchers.IO, start = CoroutineStart.UNDISPATCHED) {
    while (!obsolescent.isObsolete) {
      delay(ConcurrencyUtil.DEFAULT_TIMEOUT_MS)
    }
    scope.cancel()
  }
  return scope
}