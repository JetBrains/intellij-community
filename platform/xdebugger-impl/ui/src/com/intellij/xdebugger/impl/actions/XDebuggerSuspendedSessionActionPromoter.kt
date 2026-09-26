// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions

import com.intellij.openapi.actionSystem.ActionPromoter
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy

internal class XDebuggerSuspendedSessionActionPromoter : ActionPromoter {
  override fun promote(actions: List<AnAction>, context: DataContext): List<AnAction>? {
    val debuggerActions = actions.filterIsInstance<XDebuggerActionBase>()
    if (debuggerActions.isEmpty()) return null
    val session = getSessionProxy(context) ?: return null
    if (!XDebuggerProxySuspendedActionHandler.isEnabled(session)) return null
    return debuggerActions
  }

  private fun getSessionProxy(context: DataContext): XDebugSessionProxy? {
    context.getData(XDebugSessionProxy.DEBUG_SESSION_PROXY_KEY)?.let { return it }
    val project = context.getData(CommonDataKeys.PROJECT) ?: return null
    return XDebugManagerProxy.getInstance().getCurrentSessionProxy(project)
  }
}
