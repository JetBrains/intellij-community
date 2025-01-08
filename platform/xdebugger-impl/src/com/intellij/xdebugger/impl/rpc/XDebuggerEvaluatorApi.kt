// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.rpc

import com.intellij.ide.rpc.DocumentId
import com.intellij.ide.ui.icons.IconId
import com.intellij.openapi.editor.colors.TextAttributesKey
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

  suspend fun computePresentation(xValueDto: XValueDto, xValuePlace: XValuePlace): Flow<XValuePresentationEvent>?

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
sealed interface XValuePresentationEvent {
  @ApiStatus.Internal
  @Serializable
  data class SetSimplePresentation(
    @JvmField val icon: IconId?,
    @JvmField val presentationType: String?,
    @JvmField val value: String,
    @JvmField val hasChildren: Boolean,
  ) : XValuePresentationEvent

  @ApiStatus.Internal
  @Serializable
  data class SetAdvancedPresentation(
    @JvmField val icon: IconId?,
    @JvmField val hasChildren: Boolean,
    @JvmField val separator: String,
    @JvmField val isShownName: Boolean,
    @JvmField val presentationType: String?,
    @JvmField val isAsync: Boolean,
    @JvmField val parts: List<XValueAdvancedPresentationPart>,
  ) : XValuePresentationEvent
}

@ApiStatus.Internal
@Serializable
sealed interface XValueAdvancedPresentationPart {
  @ApiStatus.Internal
  @Serializable
  data class Value(@JvmField val value: String) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class StringValue(@JvmField val value: String) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class NumericValue(@JvmField val value: String) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class KeywordValue(@JvmField val value: String) : XValueAdvancedPresentationPart

  // TODO[IJPL-160146]: support [TextAttributesKey] serialization
  @ApiStatus.Internal
  @Serializable
  data class ValueWithAttributes(@JvmField val value: String, @Transient @JvmField val key: TextAttributesKey? = null) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class StringValueWithHighlighting(
    @JvmField val value: String,
    @JvmField val additionalSpecialCharsToHighlight: String?,
    @JvmField val maxLength: Int,
  ) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class Comment(@JvmField val comment: String) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class SpecialSymbol(@JvmField val symbol: String) : XValueAdvancedPresentationPart

  @ApiStatus.Internal
  @Serializable
  data class Error(@JvmField val error: String) : XValueAdvancedPresentationPart
}