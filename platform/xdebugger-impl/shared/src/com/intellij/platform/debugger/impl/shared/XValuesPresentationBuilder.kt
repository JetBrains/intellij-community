// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.shared

import com.intellij.platform.debugger.impl.rpc.XFullValueEvaluatorDto
import com.intellij.platform.debugger.impl.rpc.XValueComputeChildrenEvent
import com.intellij.platform.debugger.impl.rpc.XValueId
import com.intellij.platform.debugger.impl.rpc.XValueSerializedPresentation
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class XValuesPresentationBuilder {
  private val presentationFlows = hashMapOf<XValueId, MutableSharedFlow<XValueSerializedPresentation>>()
  private val fullValueEvaluatorFlows = hashMapOf<XValueId, MutableStateFlow<XFullValueEvaluatorDto?>>()

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

  fun createFlows(xValueId: XValueId): Pair<SharedFlow<XValueSerializedPresentation>, StateFlow<XFullValueEvaluatorDto?>> {
    val presentationFlow = MutableSharedFlow<XValueSerializedPresentation>(1, 1, BufferOverflow.DROP_OLDEST)
    val fullValueEvaluatorFlow = MutableStateFlow<XFullValueEvaluatorDto?>(null)
    presentationFlows[xValueId] = presentationFlow
    fullValueEvaluatorFlows[xValueId] = fullValueEvaluatorFlow
    return presentationFlow.asSharedFlow() to fullValueEvaluatorFlow.asStateFlow()
  }
}
