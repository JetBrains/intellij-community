// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.rpc

import com.intellij.ide.rpc.DocumentId
import com.intellij.ide.ui.colors.SerializableSimpleTextAttributes
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.rpc.Id
import com.intellij.platform.rpc.RemoteApiProviderService
import com.intellij.platform.rpc.UID
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.evaluation.ExpressionInfo
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XDescriptor
import com.intellij.xdebugger.frame.XPinToTopData
import com.intellij.xdebugger.impl.evaluate.XEvaluationOrigin
import com.intellij.xdebugger.impl.evaluate.quick.common.ValueHintType
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.core.DeferredSerializer
import fleet.rpc.core.RpcFlow
import fleet.rpc.core.SendChannelSerializer
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.SendChannel
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
@Rpc
interface XDebuggerEvaluatorApi : RemoteApi<Unit> {
  suspend fun evaluate(frameId: XStackFrameId, expression: String, position: XSourcePositionDto?, origin: XEvaluationOrigin): TimeoutSafeResult<XEvaluationResult>

  suspend fun evaluateXExpression(frameId: XStackFrameId, expression: XExpressionDto, position: XSourcePositionDto?, origin: XEvaluationOrigin): TimeoutSafeResult<XEvaluationResult>

  suspend fun evaluateInDocument(frameId: XStackFrameId, documentId: DocumentId, offset: Int, type: ValueHintType, origin: XEvaluationOrigin): TimeoutSafeResult<XEvaluationResult>

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

  /**
   * This event is introduced as an optimization for fast node expand on frontend.
   * Having [XValueSerializedPresentation] passed as [XValueComputeChildrenEvent] helps to avoid an additional round-trip.
   */
  @Serializable
  data class XValuePresentationEvent(val xValueId: XValueId, val presentation: XValueSerializedPresentation) : XValueComputeChildrenEvent

  /**
   * @see XValuePresentationEvent
   */
  @Serializable
  data class XValueFullValueEvaluatorEvent(val xValueId: XValueId, val fullValueEvaluator: XFullValueEvaluatorDto?) : XValueComputeChildrenEvent

  @Serializable
  data class XValueAdditionalLinkEvent(val xValueId: XValueId, val link: XDebuggerTreeNodeHyperlinkDto?) : XValueComputeChildrenEvent

  @Serializable
  data class SetAlreadySorted(val value: Boolean) : XValueComputeChildrenEvent

  @Serializable
  data class SetErrorMessage(val message: String, val link: XDebuggerTreeNodeHyperlinkDto?) : XValueComputeChildrenEvent

  @Serializable
  data class SetMessage(
    val message: String,
    val icon: IconId?,
    val attributes: SerializableSimpleTextAttributes,
    val link: XDebuggerTreeNodeHyperlinkDto?,
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
  data class Evaluated(val xValue: XValueDtoWithPresentation) : XEvaluationResult

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
  @Serializable(with = DeferredSerializer::class) val canMarkValue: Deferred<Boolean>,
  val valueMark: RpcFlow<XValueMarkerDto?>,
  val name: String?,
  val textProvider: RpcFlow<XValueTextProviderDto>?,
  @Serializable(with = DeferredSerializer::class) val pinToTopData: Deferred<XPinToTopData>?,
)

@ApiStatus.Internal
@Serializable
data class XValueDtoWithPresentation(
  val value: XValueDto,
  val presentation: RpcFlow<XValueSerializedPresentation>,
  val fullValueEvaluator: RpcFlow<XFullValueEvaluatorDto?>,
  val additionalLink: RpcFlow<XDebuggerTreeNodeHyperlinkDto?>,
)

@ApiStatus.Internal
@Serializable
data class XValueTextProviderDto(
  val shouldShowTextValue: Boolean,
  val textValue: String?,
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
  @JvmField val isEnabledFlow: RpcFlow<Boolean>,
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

@ApiStatus.Internal
@Serializable
data class XDebuggerHyperlinkId(override val uid: UID) : Id

@ApiStatus.Internal
@Serializable
data class XDebuggerTreeNodeHyperlinkDto(
  val id: XDebuggerHyperlinkId,
  val text: @Nls String,
  val tooltip: @Nls String?,
  val icon: IconId?,
  val shortcut: String?,
  val alwaysOnScreen: Boolean,
  @Transient val attributes: SimpleTextAttributes? = null,
  @Transient val local: XDebuggerTreeNodeHyperlink? = null,
)
