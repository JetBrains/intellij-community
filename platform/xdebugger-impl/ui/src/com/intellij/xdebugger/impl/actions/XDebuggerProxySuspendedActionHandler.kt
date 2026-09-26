// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerSplitActionHandler
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class XDebuggerProxySuspendedActionHandler : XDebuggerSplitActionHandler() {
  override fun isEnabled(session: XDebugSessionProxy, dataContext: DataContext): Boolean {
    return isEnabled(session)
  }

  companion object {
    fun isEnabled(session: XDebugSessionProxy): Boolean {
      return !session.isReadOnly && session.isSuspended
    }
  }
}
