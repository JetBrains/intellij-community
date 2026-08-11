// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.findDocument
import com.intellij.platform.debugger.impl.rpc.XBreakpointApi
import com.intellij.platform.debugger.impl.rpc.XBreakpointId
import com.intellij.platform.debugger.impl.rpc.XBreakpointTypeId
import com.intellij.platform.debugger.impl.rpc.XExpressionDocumentDto
import com.intellij.platform.debugger.impl.rpc.XExpressionDto
import com.intellij.platform.debugger.impl.rpc.XSourcePositionDto
import com.intellij.platform.debugger.impl.rpc.xExpression
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XLineBreakpointVerticalPlacement
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.impl.breakpoints.XBreakpointManagerImpl
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import com.intellij.xdebugger.impl.breakpoints.XLineBreakpointImpl
import com.intellij.xdebugger.impl.proxy.getEditorsProvider
import com.intellij.xdebugger.impl.rpc.models.findValue

internal class BackendXBreakpointApi : XBreakpointApi {
  override suspend fun setEnabled(breakpointId: XBreakpointId, requestId: Long, enabled: Boolean) {
    val breakpoint = breakpointId.findValue() ?: return
    breakpoint.setEnabled(requestId, enabled)
  }

  override suspend fun setSuspendPolicy(breakpointId: XBreakpointId, requestId: Long, suspendPolicy: SuspendPolicy) {
    val breakpoint = breakpointId.findValue() ?: return
    breakpoint.setSuspendPolicy(requestId, suspendPolicy)
  }

  override suspend fun setDefaultSuspendPolicy(project: ProjectId, breakpointTypeId: XBreakpointTypeId, policy: SuspendPolicy) {
    val project = project.findProjectOrNull() ?: return
    val type = XBreakpointUtil.findType(breakpointTypeId.id) ?: return
    getBreakpointManager(project).setDefaultSuspendPolicy(type, policy)
  }

  override suspend fun getDefaultGroup(project: ProjectId): String? {
    val project = project.findProjectOrNull() ?: return null
    return getBreakpointManager(project).defaultGroup
  }

  override suspend fun setDefaultGroup(project: ProjectId, group: String?) {
    val project = project.findProjectOrNull() ?: return
    getBreakpointManager(project).defaultGroup = group
  }

  override suspend fun setConditionEnabled(breakpointId: XBreakpointId, requestId: Long, enabled: Boolean) {
    val breakpoint = breakpointId.findValue() ?: return
    breakpoint.setConditionEnabled(requestId, enabled)
  }

  override suspend fun setConditionExpression(breakpointId: XBreakpointId, requestId: Long, condition: XExpressionDto?) {
    val breakpoint = breakpointId.findValue() ?: return
    val expression = condition?.xExpression()
    breakpoint.setConditionExpression(requestId, expression)
  }

  override suspend fun setFileUrl(breakpointId: XBreakpointId, requestId: Long, fileUrl: String?) {
    val breakpoint = breakpointId.findValue() as? XLineBreakpointImpl<*> ?: return
    breakpoint.setFileUrl(requestId, fileUrl)
  }

  override suspend fun setPlacement(breakpointId: XBreakpointId, requestId: Long, placement: XLineBreakpointVerticalPlacement) {
    val breakpoint = breakpointId.findValue() as? XLineBreakpointImpl<*> ?: return
    breakpoint.setPlacement(requestId, placement)
  }

  override suspend fun setLine(breakpointId: XBreakpointId, requestId: Long, line: Int, documentPatchVersion: DocumentPatchVersion?): Boolean {
    val breakpoint = breakpointId.findValue() as? XLineBreakpointImpl<*> ?: return true
    if (!breakpoint.awaitDocumentIsInSyncAndCommitted(documentPatchVersion)) return false
    breakpoint.setLine(requestId, line)
    return true
  }

  override suspend fun updatePosition(breakpointId: XBreakpointId, requestId: Long, documentPatchVersion: DocumentPatchVersion?): Boolean {
    val breakpoint = breakpointId.findValue() as? XLineBreakpointImpl<*> ?: return true
    if (!breakpoint.awaitDocumentIsInSyncAndCommitted(documentPatchVersion)) return false
    breakpoint.resetSourcePosition(requestId)
    return true
  }

  override suspend fun setLogMessage(breakpointId: XBreakpointId, requestId: Long, enabled: Boolean) {
    val breakpoint = breakpointId.findValue() ?: return
    breakpoint.setLogMessage(requestId, enabled)
  }

  override suspend fun setLogStack(breakpointId: XBreakpointId, requestId: Long, enabled: Boolean) {
    val breakpoint = breakpointId.findValue() ?: return
    breakpoint.setLogStack(requestId, enabled)
  }

  override suspend fun setLogExpressionEnabled(breakpointId: XBreakpointId, requestId: Long, enabled: Boolean) {
    val breakpoint = breakpointId.findValue() ?: return
    breakpoint.setLogExpressionEnabled(requestId, enabled)
  }

  override suspend fun setLogExpressionObject(breakpointId: XBreakpointId, requestId: Long, logExpression: XExpressionDto?) {
    val breakpoint = breakpointId.findValue() ?: return
    val expression = logExpression?.xExpression()
    breakpoint.setLogExpressionObject(requestId, expression)
  }

  override suspend fun setTemporary(breakpointId: XBreakpointId, requestId: Long, isTemporary: Boolean) {
    val breakpoint = breakpointId.findValue() as? XLineBreakpointImpl<*> ?: return
    breakpoint.setTemporary(requestId, isTemporary)
  }

  override suspend fun setUserDescription(breakpointId: XBreakpointId, requestId: Long, description: String?) {
    val breakpoint = breakpointId.findValue() ?: return
    breakpoint.setUserDescription(requestId, description)
  }

  override suspend fun setGroup(breakpointId: XBreakpointId, requestId: Long, group: String?) {
    val breakpoint = breakpointId.findValue() ?: return
    breakpoint.setGroup(requestId, group)
  }

  override suspend fun createDocument(frontendDocumentId: FrontendDocumentId, breakpointId: XBreakpointId, expression: XExpressionDto, sourcePosition: XSourcePositionDto?, evaluationMode: EvaluationMode): XExpressionDocumentDto? {
    val breakpoint = breakpointId.findValue() ?: return null
    val project = breakpoint.project
    val editorsProvider = getEditorsProvider(breakpoint.type, breakpoint, breakpoint.project) ?: return null
    return createBackendDocument(project, frontendDocumentId, editorsProvider, expression, sourcePosition, evaluationMode)
  }

  private fun getBreakpointManager(project: Project): XBreakpointManagerImpl =
    XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl
}

private suspend fun XLineBreakpointImpl<*>.awaitDocumentIsInSyncAndCommitted(version: DocumentPatchVersion?): Boolean {
  val document = readAction { file?.findDocument() } ?: return true
  return document.awaitIsInSyncAndCommitted(project, version)
}
