// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.BackendDocumentId
import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.ide.rpc.bindToFrontend
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointProxy.Monolith.Companion.getEditorsProvider
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.rpc.XBreakpointApi
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeId
import com.intellij.xdebugger.impl.rpc.XExpressionDto
import com.intellij.xdebugger.impl.rpc.XSourcePositionDto
import com.intellij.xdebugger.impl.rpc.models.findValue
import com.intellij.xdebugger.impl.rpc.sourcePosition
import com.intellij.xdebugger.impl.rpc.xExpression
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class BackendXBreakpointApi : XBreakpointApi {
  override suspend fun setEnabled(breakpointId: XBreakpointId, enabled: Boolean) {
    val breakpoint = breakpointId.findValue() ?: return
    edtWriteAction {
      breakpoint.isEnabled = enabled
    }
  }

  override suspend fun setSuspendPolicy(breakpointId: XBreakpointId, suspendPolicy: SuspendPolicy) {
    val breakpoint = breakpointId.findValue() ?: return
    edtWriteAction {
      breakpoint.suspendPolicy = suspendPolicy
    }
  }

  override suspend fun setDefaultSuspendPolicy(project: ProjectId, breakpointTypeId: XBreakpointTypeId, policy: SuspendPolicy) {
    val project = project.findProject()
    edtWriteAction {
      val type = XBreakpointUtil.findType(breakpointTypeId.id) ?: return@edtWriteAction
      (XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl).getBreakpointDefaults(type).suspendPolicy = policy
    }
  }

  override suspend fun setConditionEnabled(breakpointId: XBreakpointId, enabled: Boolean) {
    val breakpoint = breakpointId.findValue() ?: return
    edtWriteAction {
      breakpoint.isConditionEnabled = enabled
    }
  }

  override suspend fun setConditionExpression(breakpointId: XBreakpointId, condition: XExpressionDto?) {
    val breakpoint = breakpointId.findValue() ?: return
    val expression = condition?.xExpression()
    edtWriteAction {
      breakpoint.conditionExpression = expression
    }
  }

  override suspend fun setLogMessage(breakpointId: XBreakpointId, enabled: Boolean) {
    val breakpoint = breakpointId.findValue() ?: return
    edtWriteAction {
      breakpoint.isLogMessage = enabled
    }
  }

  override suspend fun setLogStack(breakpointId: XBreakpointId, enabled: Boolean) {
    val breakpoint = breakpointId.findValue() ?: return
    edtWriteAction {
      breakpoint.isLogStack = enabled
    }
  }

  override suspend fun setLogExpressionEnabled(breakpointId: XBreakpointId, enabled: Boolean) {
    val breakpoint = breakpointId.findValue() ?: return
    edtWriteAction {
      breakpoint.isLogExpressionEnabled = enabled
    }
  }

  override suspend fun setLogExpressionObject(breakpointId: XBreakpointId, logExpression: XExpressionDto?) {
    val breakpoint = breakpointId.findValue() ?: return
    val expression = logExpression?.xExpression()
    edtWriteAction {
      breakpoint.logExpressionObject = expression
    }
  }

  override suspend fun setTemporary(breakpointId: XBreakpointId, isTemporary: Boolean) {
    val breakpoint = breakpointId.findValue() as? XLineBreakpoint<*> ?: return
    edtWriteAction {
      breakpoint.isTemporary = isTemporary
    }
  }

  override suspend fun createDocument(frontendDocumentId: FrontendDocumentId, breakpointId: XBreakpointId, expression: XExpressionDto, sourcePosition: XSourcePositionDto?, evaluationMode: EvaluationMode): BackendDocumentId? {
    val breakpoint = breakpointId.findValue() ?: return null
    val project = breakpoint.project
    val editorsProvider = getEditorsProvider(breakpoint.type, breakpoint, breakpoint.project) ?: return null
    return withContext(Dispatchers.EDT) {
      val backendDocument = editorsProvider.createDocument(project, expression.xExpression(), sourcePosition?.sourcePosition(), evaluationMode)
      backendDocument.bindToFrontend(frontendDocumentId)
    }
  }

  override suspend fun removeBreakpoint(breakpointId: XBreakpointId) {
    val breakpoint = breakpointId.findValue() ?: return
    edtWriteAction {
      XDebuggerManager.getInstance(breakpoint.project).breakpointManager.removeBreakpoint(breakpoint)
    }
  }
}