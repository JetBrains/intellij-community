// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.platform.debugger.impl.rpc.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class XValueStateFlows(
  val presentationFlow: Flow<XValueSerializedPresentation>,
  val fullValueEvaluatorFlow: Flow<XFullValueEvaluatorDto?>,
  val additionalLinkFlow: Flow<XDebuggerTreeNodeHyperlinkDto?>,
)

@ApiStatus.Internal
class XValuesPresentationBuilder {

  private val presentationFlows = hashMapOf<XValueId, MutableSharedFlow<XValueSerializedPresentation>>()
  private val fullValueEvaluatorFlows = hashMapOf<XValueId, MutableStateFlow<XFullValueEvaluatorDto?>>()
  private val additionalLinkFlows = hashMapOf<XValueId, MutableStateFlow<XDebuggerTreeNodeHyperlinkDto?>>()

  fun consume(event: XValueComputeChildrenEvent.XValuePresentationEvent) {
    val flow = presentationFlows[event.xValueId]
    checkNotNull(flow) { "Presentation event received for unknown XValueId: ${event.xValueId}" }
    flow.tryEmit(event.presentation)
  }

  fun consume(event: XValueComputeChildrenEvent.XValueFullValueEvaluatorEvent) {
    val flow = fullValueEvaluatorFlows[event.xValueId]
    checkNotNull(flow) { "Full value evaluator event received for unknown XValueId: ${event.xValueId}" }
    flow.value = event.fullValueEvaluator
  }

  fun consume(event: XValueComputeChildrenEvent.XValueAdditionalLinkEvent) {
    val flow = additionalLinkFlows[event.xValueId]
    checkNotNull(flow) { "Additional link event received for unknown XValueId: ${event.xValueId}" }
    flow.value = event.link
  }

  fun createFlows(xValueId: XValueId): XValueStateFlows {
    val presentationFlow = MutableSharedFlow<XValueSerializedPresentation>(1, 1, BufferOverflow.DROP_OLDEST)
    val fullValueEvaluatorFlow = MutableStateFlow<XFullValueEvaluatorDto?>(null)
    val additionalLinkFlow = MutableStateFlow<XDebuggerTreeNodeHyperlinkDto?>(null)
    presentationFlows[xValueId] = presentationFlow
    fullValueEvaluatorFlows[xValueId] = fullValueEvaluatorFlow
    additionalLinkFlows[xValueId] = additionalLinkFlow
    return XValueStateFlows(
      presentationFlow.asSharedFlow(),
      fullValueEvaluatorFlow.asStateFlow(),
      additionalLinkFlow.asStateFlow()
    )
  }
}
