/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.actions.XDebuggerProxySuspendedActionHandler
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import com.intellij.xdebugger.impl.performDebuggerActionAsync
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.rpc.toRpc
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class XDebuggerRunToCursorActionHandler(private val myIgnoreBreakpoints: Boolean) : XDebuggerProxySuspendedActionHandler() {
  override fun isEnabled(session: XDebugSessionProxy, dataContext: DataContext): Boolean {
    return super.isEnabled(session, dataContext) && XDebuggerUtilImpl.getCaretPosition(session.project, dataContext) != null
  }

  override fun perform(session: XDebugSessionProxy, dataContext: DataContext) {
    val position = XDebuggerUtilImpl.getCaretPosition(session.project, dataContext) ?: return
    performDebuggerActionAsync(session.project, dataContext) {
      XDebugSessionApi.getInstance().runToPosition(session.id, position.toRpc(), myIgnoreBreakpoints)
    }
  }
}
