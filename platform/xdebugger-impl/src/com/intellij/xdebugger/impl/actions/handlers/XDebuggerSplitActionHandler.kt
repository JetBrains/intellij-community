// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import org.jetbrains.annotations.ApiStatus

/**
 * An abstract handler for debugger-specific actions within the IntelliJ platform.
 * This class bridges action execution and the debugging sessions, providing a mechanism to perform
 * and determine the enablement state of debugger actions tied to a specific debugging session.
 * <p>
 * The handler is supposed to work with [com.intellij.xdebugger.impl.actions.XDebuggerActionBase].
 * This handler can be used in monolith or RemDev.
 *
 * @see XDebuggerActionHandler
 */
@ApiStatus.Internal
abstract class XDebuggerSplitActionHandler : DebuggerActionHandler() {

  override fun perform(project: Project, event: AnActionEvent) {
    val session = DebuggerUIUtil.getSessionProxy(event) ?: return
    perform(session, event.dataContext)
  }

  override fun isEnabled(project: Project, event: AnActionEvent): Boolean {
    if (LightEdit.owns(project)) return false
    val session = DebuggerUIUtil.getSessionProxy(event) ?: return false
    return isEnabled(session, event.dataContext)
  }

  protected abstract fun isEnabled(session: XDebugSessionProxy, dataContext: DataContext): Boolean

  protected abstract fun perform(session: XDebugSessionProxy, dataContext: DataContext)
}
