// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.ide.rpc.DocumentId
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.evaluation.ExpressionInfo
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XDescriptor
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import com.intellij.xdebugger.impl.rpc.XValueGroupId
import com.intellij.xdebugger.impl.rpc.XValueId
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.DeferredSerializer
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.SendChannelSerializer
import fleet.rpc.remoteApiDescriptor
import fleet.util.UID
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface XDebuggerEvaluatorApi : RemoteApi<Unit> {
  suspend fun evaluate(frameId: XStackFrameId, expression: String, position: XSourcePositionDto?): TimeoutSafeResult<XEvaluationResult>

  suspend fun evaluateXExpression(frameId: XStackFrameId, expression: XExpressionDto, position: XSourcePositionDto?): TimeoutSafeResult<XEvaluationResult>

  suspend fun evaluateInDocument(frameId: XStackFrameId, documentId: DocumentId, offset: Int, type: ValueHintType): TimeoutSafeResult<XEvaluationResult>

  suspend fun expressionInfoAtOffset(frameId: XStackFrameId, documentId: DocumentId, offset: Int, sideEffectsAllowed: Boolean): ExpressionInfo?

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
  @Serializable
  data class AddChildren(
    val names: List<String>,
    val children: List<XValueDto>,
    val isLast: Boolean,
    val topGroups: List<XValueGroupDto>,
    val bottomGroups: List<XValueGroupDto>,
    val topValues: List<XValueDto>,
  ) : XValueComputeChildrenEvent

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

  @Serializable
  data class TooManyChildren(
    val remaining: Int,
    @Serializable(with = SendChannelSerializer::class) val addNextChildren: SendChannel<Unit>? = null,
  ) : XValueComputeChildrenEvent
}

@ApiStatus.Internal
@Serializable
sealed interface XEvaluationResult {
  @Serializable
  data class Evaluated(val valueId: XValueDto) : XEvaluationResult

  @Serializable
  data class EvaluationError(val errorMessage: @NlsContexts.DialogMessage String) : XEvaluationResult

  @Serializable
  data class InvalidExpression(val error: @NlsContexts.DialogMessage String) : XEvaluationResult
}

@ApiStatus.Internal
@Serializable
data class XValueDto(
  val id: XValueId,
  @Serializable(with = DeferredSerializer::class) val descriptor: Deferred<XDescriptor>?,
  val canNavigateToSource: Boolean,
  @Serializable(with = DeferredSerializer::class) val canNavigateToTypeSource: Deferred<Boolean>,
  @Serializable(with = DeferredSerializer::class) val canBeModified: Deferred<Boolean>,
  val valueMark: RpcFlow<XValueMarkerDto?>,
  val presentation: RpcFlow<XValueSerializedPresentation>,
  val fullValueEvaluator: RpcFlow<XFullValueEvaluatorDto?>,
  val name: String?,
)

@ApiStatus.Internal
@Serializable
data class XValueGroupDto(
  val id: XValueGroupId,
  val groupName: String,
  val icon: IconId?,
  val isAutoExpand: Boolean,
  val isRestoreExpansion: Boolean,
  val separator: String,
  val comment: String?,
)

@ApiStatus.Internal
@Serializable
data class XValueMarkerId(val id: UID)


@ApiStatus.Internal
@Serializable
data class XDebuggerEvaluatorDto(val canEvaluateInDocument: Boolean)

@ApiStatus.Internal
@Serializable
data class XFullValueEvaluatorDto(
  @NlsSafe @JvmField val linkText: String,
  @JvmField val isEnabled: Boolean,
  @JvmField val isShowValuePopup: Boolean,
  @JvmField val attributes: FullValueEvaluatorLinkAttributes?,
) {
  @Serializable
  data class FullValueEvaluatorLinkAttributes(
    @JvmField val linkIcon: IconId?,
    @NlsSafe @JvmField val tooltipText: String?,
    // TODO[IJPL-160146]: deal with Supplier<String>?
    @JvmField val shortcut: String?,
  )
}

@ApiStatus.Internal
@Serializable
sealed interface XFullValueEvaluatorResult {
  // TODO[IJPL-160146]: support Font?
  @Serializable
  data class Evaluated(val fullValue: String) : XFullValueEvaluatorResult

  @Serializable
  data class EvaluationError(val errorMessage: @NlsContexts.DialogMessage String) : XFullValueEvaluatorResult
}

