// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.ide.rpc.BackendDocumentId
import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeId
import com.intellij.xdebugger.impl.rpc.XExpressionDto
import com.intellij.xdebugger.impl.rpc.XSourcePositionDto
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XBreakpointApi : RemoteApi<Unit> {
  suspend fun setEnabled(breakpointId: XBreakpointId, enabled: Boolean)

  suspend fun setSuspendPolicy(breakpointId: XBreakpointId, suspendPolicy: SuspendPolicy)

  suspend fun setDefaultSuspendPolicy(project: ProjectId, breakpointTypeId: XBreakpointTypeId, policy: SuspendPolicy)

  suspend fun setConditionEnabled(breakpointId: XBreakpointId, enabled: Boolean)

  suspend fun setConditionExpression(breakpointId: XBreakpointId, condition: XExpressionDto?)

  suspend fun setLogMessage(breakpointId: XBreakpointId, enabled: Boolean)

  suspend fun setLogStack(breakpointId: XBreakpointId, enabled: Boolean)

  suspend fun setLogExpressionEnabled(breakpointId: XBreakpointId, enabled: Boolean)

  suspend fun setLogExpressionObject(breakpointId: XBreakpointId, logExpression: XExpressionDto?)

  suspend fun setTemporary(breakpointId: XBreakpointId, isTemporary: Boolean)

  suspend fun setUserDescription(breakpointId: XBreakpointId, description: String?)

  suspend fun setFileUrl(breakpointId: XBreakpointId, fileUrl: String?)

  suspend fun setLine(breakpointId: XBreakpointId, line: Int)

  suspend fun createDocument(frontendDocumentId: FrontendDocumentId, breakpointId: XBreakpointId, expression: XExpressionDto, sourcePosition: XSourcePositionDto?, evaluationMode: EvaluationMode): BackendDocumentId?

  suspend fun removeBreakpoint(breakpointId: XBreakpointId)

  companion object {
    @JvmStatic
    suspend fun getInstance(): XBreakpointApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XBreakpointApi>())
    }
  }
}