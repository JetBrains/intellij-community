// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.platform.debugger.impl.frontend.util.SequentialRpcRequestsExecutor
import com.intellij.platform.debugger.impl.rpc.SetValueResult
import com.intellij.platform.debugger.impl.rpc.XDebuggerValueModifierApi
import com.intellij.platform.debugger.impl.rpc.XValueDto
import com.intellij.platform.debugger.impl.rpc.toRpc
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.frame.XValueModifier
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import kotlinx.coroutines.CoroutineScope
import java.lang.Deprecated

internal class FrontendXValueModifier(
  cs: CoroutineScope,
  private val xValueDto: XValueDto,
) : XValueModifier() {
  private val sequentialExecutor = SequentialRpcRequestsExecutor.create(cs)

  override fun setValue(expression: XExpression, callback: XModificationCallback) {
    sequentialExecutor.execute {
      val result = XDebuggerValueModifierApi.getInstance().setValue(xValueDto.id, expression.toRpc()).await()

      when (result) {
        SetValueResult.Success -> {
          callback.valueModified()
        }
        is SetValueResult.ErrorOccurred -> {
          callback.errorOccurred(result.message)
        }
      }
    }
  }

  override fun calculateInitialValueEditorText(callback: XInitialValueCallback?) {
    if (callback == null) {
      return
    }
    sequentialExecutor.execute {
      val initialValue = XDebuggerValueModifierApi.getInstance().initialValueEditorText(xValueDto.id)
      callback.setValue(initialValue)
    }
  }
}
