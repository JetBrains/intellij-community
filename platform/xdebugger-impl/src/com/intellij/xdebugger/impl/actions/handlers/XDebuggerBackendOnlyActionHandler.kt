// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.frontend.FrontendApplicationInfo.getFrontendType
import com.intellij.frontend.FrontendType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.SplitDebuggerMode.showSplitWarnings
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.DebuggerActionHandler
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import org.jetbrains.annotations.ApiStatus
import com.intellij.xdebugger.impl.actions.XDebuggerProxySuspendedActionHandler

/**
 * Base class for backend-only debugger action handlers.
 *
 * Use this base class for backend-only action handlers, which do not operate on the frontend.
 * Also, please mark the action corresponding to this handler as [ActionRemoteBehaviorSpecification.BackendOnly].
 *
 * If you need to create an action which operates on the frontend, use [XDebuggerActionHandler] instead.
 */
@ApiStatus.Experimental
abstract class XDebuggerBackendOnlyActionHandler : DebuggerActionHandler() {

  final override fun isEnabled(project: Project, event: AnActionEvent): Boolean {
    if (showSplitWarnings() && getFrontendType() is FrontendType.Remote) {
      LOG.error("XDebuggerBackendOnlyActionHandler should only be used for backend-only actions. " +
                "If your action operates on the frontend, use XDebuggerActionHandler instead.")
    }
    val session = DebuggerUIUtil.getSession(event) ?: return false
    return isEnabled(session, event.dataContext)
  }

  final override fun perform(project: Project, event: AnActionEvent) {
    val session = DebuggerUIUtil.getSession(event) ?: return
    perform(session, event.dataContext)
  }

  abstract fun isEnabled(session: XDebugSession, dataContext: DataContext): Boolean

  abstract fun perform(session: XDebugSession, dataContext: DataContext)

  companion object {
    private val LOG: Logger = Logger.getInstance(XDebuggerBackendOnlyActionHandler::class.java)
  }
}

/**
 * Base class for backend-only debugger action handlers which require the debug session to be suspended.
 *
 * Use this base class for backend-only action handlers, which do not operate on the frontend.
 * Also, please mark the action corresponding to this handler as [ActionRemoteBehaviorSpecification.BackendOnly].
 *
 * If you need to create an action which operates on the frontend, use [XDebuggerProxySuspendedActionHandler] instead.
 */
@ApiStatus.Experimental
abstract class XDebuggerSuspendedBackendOnlyActionHandler : XDebuggerBackendOnlyActionHandler() {

  override fun isEnabled(session: XDebugSession, dataContext: DataContext): Boolean =
    !(session as XDebugSessionImpl).isReadOnly && session.isSuspended
}