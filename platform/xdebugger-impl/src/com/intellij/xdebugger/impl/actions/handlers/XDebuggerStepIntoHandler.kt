// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.xdebugger.impl.actions.XDebuggerProxySuspendedActionHandler
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.rpc.XSmartStepIntoTargetDto

internal class XDebuggerStepIntoHandler : XDebuggerSmartStepIntoHandler() {
  override fun isEnabled(session: XDebugSessionProxy, dataContext: DataContext): Boolean {
    return XDebuggerProxySuspendedActionHandler.isEnabled(session)
  }

  override suspend fun computeTargets(session: XDebugSessionProxy): List<XSmartStepIntoTargetDto> {
    return XDebugSessionApi.getInstance().computeStepTargets(session.id)
  }

  override suspend fun handleSimpleCases(targets: List<XSmartStepIntoTarget>, session: XDebugSessionProxy): Boolean {
    if (targets.size == 1) {
      val singleTarget = targets[0]
      if (singleTarget.needsForcedSmartStepInto) {
        return super.handleSimpleCases(targets, session)
      }
    }
    if (targets.size < 2) {
      XDebugSessionApi.getInstance().stepInto(session.id)
      return true
    }
    return false
  }
}
