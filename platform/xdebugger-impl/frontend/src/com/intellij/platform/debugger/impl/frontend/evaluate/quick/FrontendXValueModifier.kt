// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.frame.XValueModifier
import com.intellij.xdebugger.impl.breakpoints.XExpressionImpl
import com.intellij.platform.debugger.impl.rpc.SetValueResult
import com.intellij.platform.debugger.impl.rpc.XDebuggerValueModifierApi
import com.intellij.platform.debugger.impl.rpc.XValueDto
import com.intellij.xdebugger.impl.rpc.toRpc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.lang.Deprecated

internal class FrontendXValueModifier(private val project: Project, private val xValueDto: XValueDto) : XValueModifier() {
  @Suppress("removal", "DEPRECATED_JAVA_ANNOTATION")
  @Deprecated(forRemoval = true)
  override fun setValue(expression: String, callback: XModificationCallback) {
    setValue(XExpressionImpl.fromText(expression), callback)
  }

  override fun setValue(expression: XExpression, callback: XModificationCallback) {
    project.service<FrontendXValueModifierCoroutineScopeProvider>().cs.launch {
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
    project.service<FrontendXValueModifierCoroutineScopeProvider>().cs.launch {
      val initialValue = XDebuggerValueModifierApi.getInstance().initialValueEditorText(xValueDto.id)
      callback.setValue(initialValue)
    }
  }
}

@Service(Service.Level.PROJECT)
private class FrontendXValueModifierCoroutineScopeProvider(project: Project, val cs: CoroutineScope)