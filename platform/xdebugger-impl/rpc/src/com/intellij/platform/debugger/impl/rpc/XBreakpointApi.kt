// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.ide.rpc.DocumentPatchVersion
import com.intellij.ide.rpc.DocumentPatchVersionAccessor
import com.intellij.ide.rpc.FrontendDocumentId
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.evaluation.EvaluationMode
import com.intellij.xdebugger.impl.rpc.XBreakpointId
import com.intellij.xdebugger.impl.rpc.XBreakpointTypeId
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XBreakpointApi : RemoteApi<Unit> {
  suspend fun setEnabled(breakpointId: XBreakpointId, requestId: Long, enabled: Boolean)

  suspend fun setSuspendPolicy(breakpointId: XBreakpointId, requestId: Long, suspendPolicy: SuspendPolicy)

  suspend fun setDefaultSuspendPolicy(project: ProjectId, breakpointTypeId: XBreakpointTypeId, policy: SuspendPolicy)

  suspend fun getDefaultGroup(project: ProjectId): String?

  suspend fun setDefaultGroup(project: ProjectId, group: String?)

  suspend fun setConditionEnabled(breakpointId: XBreakpointId, requestId: Long, enabled: Boolean)

  suspend fun setConditionExpression(breakpointId: XBreakpointId, requestId: Long, condition: XExpressionDto?)

  suspend fun setLogMessage(breakpointId: XBreakpointId, requestId: Long, enabled: Boolean)

  suspend fun setLogStack(breakpointId: XBreakpointId, requestId: Long, enabled: Boolean)

  suspend fun setLogExpressionEnabled(breakpointId: XBreakpointId, requestId: Long, enabled: Boolean)

  suspend fun setLogExpressionObject(breakpointId: XBreakpointId, requestId: Long, logExpression: XExpressionDto?)

  suspend fun setTemporary(breakpointId: XBreakpointId, requestId: Long, isTemporary: Boolean)

  suspend fun setUserDescription(breakpointId: XBreakpointId, requestId: Long, description: String?)

  suspend fun setGroup(breakpointId: XBreakpointId, requestId: Long, group: String?)

  suspend fun setFileUrl(breakpointId: XBreakpointId, requestId: Long, fileUrl: String?)

  /**
   * Returns `true` on success, `false` if the request should be retried later due to version mismatch.
   */
  suspend fun updatePosition(breakpointId: XBreakpointId, requestId: Long, documentPatchVersion: DocumentPatchVersion?): Boolean

  /**
   * Returns `true` on success, `false` if the request should be retried later due to version mismatch.
   */
  suspend fun setLine(breakpointId: XBreakpointId, requestId: Long, line: Int, documentPatchVersion: DocumentPatchVersion?): Boolean

  suspend fun createDocument(frontendDocumentId: FrontendDocumentId, breakpointId: XBreakpointId, expression: XExpressionDto, sourcePosition: XSourcePositionDto?, evaluationMode: EvaluationMode): XExpressionDocumentDto?

  companion object {
    @JvmStatic
    suspend fun getInstance(): XBreakpointApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XBreakpointApi>())
    }
  }
}


@ApiStatus.Internal
fun Document.patchVersion(project: Project): DocumentPatchVersion? {
  return DocumentPatchVersionAccessor.getDocumentVersion(this, project)
}
