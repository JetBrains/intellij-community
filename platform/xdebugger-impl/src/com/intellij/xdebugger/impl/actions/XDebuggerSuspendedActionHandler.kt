// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.handlers.XDebuggerActionHandler
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Obsolete
abstract class XDebuggerSuspendedActionHandler : XDebuggerActionHandler() {
  @Suppress("UsagesOfObsoleteApi")
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

@Suppress("UsagesOfObsoleteApi")
@ApiStatus.Internal
abstract class XDebuggerProxySuspendedActionHandler : XDebuggerActionHandler() {
  override fun isEnabled(session: XDebugSessionProxy, dataContext: DataContext): Boolean {
    return isEnabled(session)
  }

  final override fun isEnabled(session: XDebugSession, dataContext: DataContext): Boolean {
    return super.isEnabled(session, dataContext)
  }

  final override fun perform(session: XDebugSession, dataContext: DataContext) {
    super.perform(session, dataContext)
  }

  companion object {
    fun isEnabled(session: XDebugSessionProxy): Boolean {
      return !session.isReadOnly && session.isSuspended
    }
  }
}
