// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.util.Ref
import com.intellij.xdebugger.frame.XStackFrame
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * This allows setting current session's [XStackFrame] and updates DB state, when [XStackFrame] is changed.
 */
internal class XDebugSessionCurrentStackFrameManager {
  // Ref is used to prevent StateFlow's equals checks
  private val currentStackFrame = MutableStateFlow<Ref<XStackFrame?>>(Ref.create(null))

  fun setCurrentStackFrame(stackFrame: XStackFrame?) {
    currentStackFrame.update {
      Ref.create(stackFrame)
    }
  }

  fun getCurrentStackFrame(): XStackFrame? {
    return currentStackFrame.value.get()
  }

  fun getCurrentStackFrameFlow(): StateFlow<Ref<XStackFrame?>> = currentStackFrame
}