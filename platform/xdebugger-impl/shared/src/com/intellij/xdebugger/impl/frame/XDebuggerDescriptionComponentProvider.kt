// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.frame.XExecutionStack
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.JComponent

@Experimental
@Internal
interface XDebuggerDescriptionComponentProvider {
  val currentDescriptionComponent: MutableStateFlow<JComponent?>

  fun onExecutionStackSelected(stack: XExecutionStack, sessionProxy: XDebugSessionProxy)

  fun clear()
}
