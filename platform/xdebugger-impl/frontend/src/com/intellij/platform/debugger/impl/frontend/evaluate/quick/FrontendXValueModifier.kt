// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XExpression
import com.intellij.xdebugger.frame.XValueModifier
import com.intellij.xdebugger.impl.rpc.SetValueResult
import com.intellij.xdebugger.impl.rpc.XDebuggerValueModifierApi
import com.intellij.xdebugger.impl.rpc.XValueDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.lang.Deprecated

internal class FrontendXValueModifier(private val project: Project, private val xValueDto: XValueDto) : XValueModifier() {
  @Suppress("removal", "DEPRECATED_JAVA_ANNOTATION")
  @Deprecated(forRemoval = true)
  override fun setValue(expression: String, callback: XModificationCallback) {
    project.service<FrontendXValueModifierCoroutineScopeProvider>().cs.launch {
      val result = XDebuggerValueModifierApi.getInstance().setValue(xValueDto, expression).await()

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

  @Suppress("removal", "DEPRECATION")
  override fun setValue(expression: XExpression, callback: XModificationCallback) {
    // TODO[IJPL-160146]: don't use expression: String, XExpression should always be used
    setValue(expression.expression, callback)
  }

  override fun calculateInitialValueEditorText(callback: XInitialValueCallback?) {
    if (callback == null) {
      return
    }
    project.service<FrontendXValueModifierCoroutineScopeProvider>().cs.launch {
      val initialValue = XDebuggerValueModifierApi.getInstance().initialValueEditorText(xValueDto)
      // TODO[IJPL-160146]: what to do with [null] initialValue
      callback.setValue(initialValue)
    }
  }
}

@Service(Service.Level.PROJECT)
private class FrontendXValueModifierCoroutineScopeProvider(project: Project, val cs: CoroutineScope)