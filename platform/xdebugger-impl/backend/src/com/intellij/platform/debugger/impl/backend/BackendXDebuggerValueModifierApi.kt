// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.util.NlsContexts
import com.intellij.xdebugger.frame.XValueModifier
import com.intellij.xdebugger.impl.rhizome.XValueEntity
import com.intellij.xdebugger.impl.rpc.*
import com.jetbrains.rhizomedb.entity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred

internal class BackendXDebuggerValueModifierApi : XDebuggerValueModifierApi {
  override suspend fun setValue(xValueId: XValueId, xExpressionDto: XExpressionDto): Deferred<SetValueResult> {
    val xValue = entity(XValueEntity.XValueId, xValueId)?.xValue ?: return CompletableDeferred(SetValueResult.Success)
    val valueSetDeferred = CompletableDeferred<SetValueResult>()

    val modifier = xValue.modifier
    if (modifier == null) {
      // TODO[IJPL-160146]: handle case when xValue.modifier is null
      return CompletableDeferred(SetValueResult.Success)
    }

    modifier.setValue(xExpressionDto.xExpression(), object : XValueModifier.XModificationCallback {
      override fun valueModified() {
        valueSetDeferred.complete(SetValueResult.Success)
      }

      override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
        valueSetDeferred.complete(SetValueResult.ErrorOccurred(errorMessage))
      }
    })

    return valueSetDeferred
  }

  override suspend fun initialValueEditorText(xValueId: XValueId): String? {
    val xValue = entity(XValueEntity.XValueId, xValueId)?.xValue ?: return null
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