// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerActionHandler
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import org.jetbrains.annotations.ApiStatus

@Deprecated("Use XDebuggerProxySuspendedActionHandler instead")
abstract class XDebuggerSuspendedActionHandler : XDebuggerActionHandler() {
  @Deprecated("Deprecated in Java")
  override fun isEnabled(session: XDebugSession, dataContext: DataContext): Boolean {
    return isEnabled(session)
  }

  @ApiStatus.Internal
  companion object {
    @JvmStatic
    fun isEnabled(session: XDebugSession): Boolean {
      return !(session as XDebugSessionImpl).isReadOnly && session.isSuspended
    }
  }
}

@ApiStatus.Internal
abstract class XDebuggerProxySuspendedActionHandler : XDebuggerActionHandler() {
  override fun isEnabled(session: XDebugSessionProxy, dataContext: DataContext): Boolean {
    return !session.isReadOnly && session.isSuspended
  }

  @Deprecated("Deprecated in Java")
  final override fun isEnabled(session: XDebugSession, dataContext: DataContext): Boolean {
    @Suppress("DEPRECATION")
    return super.isEnabled(session, dataContext)
  }

  @Deprecated("Deprecated in Java")
  final override fun perform(session: XDebugSession, dataContext: DataContext) {
    @Suppress("DEPRECATION")
    super.perform(session, dataContext)
  }
}
