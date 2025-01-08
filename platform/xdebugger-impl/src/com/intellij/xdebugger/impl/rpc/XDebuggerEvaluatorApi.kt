// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.rpc.DocumentId
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.jetbrains.rhizomedb.EID
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.DeferredSerializer
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebuggerEvaluatorApi : RemoteApi<Unit> {
  suspend fun evaluate(evaluatorDto: XDebuggerEvaluatorDto, expression: String): Deferred<XEvaluationResult>

  suspend fun evaluateInDocument(evaluatorDto: XDebuggerEvaluatorDto, documentId: DocumentId, offset: Int, type: ValueHintType): Deferred<XEvaluationResult>

  suspend fun disposeXValue(xValueDto: XValueDto)

  suspend fun computePresentation(xValueDto: XValueDto, xValuePlace: XValuePlace): Flow<XValuePresentation>?

  suspend fun computeChildren(xValueDto: XValueDto): Flow<XValueComputeChildrenEvent>?

  companion object {
    @JvmStatic
    suspend fun getInstance(): XDebuggerEvaluatorApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<XDebuggerEvaluatorApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
sealed interface XValueComputeChildrenEvent {
  // TODO[IJPL-160146]: support [XValueGroup]
  @Serializable
  data class AddChildren(val names: List<String>, val children: List<XValueDto>, val isLast: Boolean) : XValueComputeChildrenEvent

  @Serializable
  data class SetAlreadySorted(val value: Boolean) : XValueComputeChildrenEvent

  // TODO[IJPL-160146]: support XDebuggerTreeNodeHyperlink serialization
  @Serializable
  data class SetErrorMessage(val message: String, @Transient val link: XDebuggerTreeNodeHyperlink? = null) : XValueComputeChildrenEvent

  // TODO[IJPL-160146]: support XDebuggerTreeNodeHyperlink serialization
  // TODO[IJPL-160146]: support SimpleTextAttributes serialization
  @Serializable
  data class SetMessage(
    val message: String,
    val icon: IconId?,
    @Transient val attributes: SimpleTextAttributes? = null,
    @Transient val link: XDebuggerTreeNodeHyperlink? = null,
  ) : XValueComputeChildrenEvent

  // TODO[IJPL-160146]: support addNextChildren serialization
  @Serializable
  data class TooManyChildren(val remaining: Int, @Transient val addNextChildren: Runnable? = null) : XValueComputeChildrenEvent
}

@ApiStatus.Internal
@Serializable
sealed interface XEvaluationResult {
  @Serializable
  data class Evaluated(val valueId: XValueDto) : XEvaluationResult

  @Serializable
  data class EvaluationError(val errorMessage: @NlsContexts.DialogMessage String) : XEvaluationResult
}

@ApiStatus.Internal
@Serializable
data class XValueDto(val eid: EID, @Serializable(with = DeferredSerializer::class) val canBeModified: Deferred<Boolean>)


@ApiStatus.Internal
@Serializable
data class XDebuggerEvaluatorDto(val eid: EID, val canEvaluateInDocument: Boolean)

@ApiStatus.Internal
@Serializable
data class XValuePresentation(
  @JvmField val icon: IconId?,
  @JvmField val type: String?,
  @JvmField val value: String,
  @JvmField val hasChildren: Boolean,
)