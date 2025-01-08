// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.kernel.backend.findValueEntity
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueModifier
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import com.intellij.xdebugger.impl.rpc.SetValueResult
import com.intellij.xdebugger.impl.rpc.XDebuggerValueModifierApi
import com.intellij.xdebugger.impl.rpc.XValueDto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

internal class BackendXDebuggerValueModifierApi : XDebuggerValueModifierApi {
  override suspend fun setValue(xValueDto: XValueDto, expression: String): Deferred<SetValueResult> {
    val xValue = xValueDto.eid.findValueEntity<XValue>()?.value ?: return CompletableDeferred(SetValueResult.Success)
    val valueSetDeferred = CompletableDeferred<SetValueResult>()

    val modifier = xValue.modifier
    if (modifier == null) {
      // TODO[IJPL-160146]: handle case when xValue.modifier is null
      return CompletableDeferred(SetValueResult.Success)
    }

    // TODO[IJPL-160146]: decide what to do with XExpression
    val xExpression = XExpressionImpl.fromText(expression)
    modifier.setValue(xExpression, object : XValueModifier.XModificationCallback {
      override fun valueModified() {
        valueSetDeferred.complete(SetValueResult.Success)
      }

      override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
        valueSetDeferred.complete(SetValueResult.ErrorOccurred(errorMessage))
      }
    })

    return valueSetDeferred
  }

  override suspend fun initialValueEditorText(xValueDto: XValueDto): String? {
    val xValue = xValueDto.eid.findValueEntity<XValue>()?.value ?: return null
    val modifier = xValue.modifier
    if (modifier == null) {
      return null
    }

    val initialValueDeferred = CompletableDeferred<String?>()

    modifier.calculateInitialValueEditorText { initialValue ->
      initialValueDeferred.complete(initialValue)
    }

    return initialValueDeferred.await()
  }
}