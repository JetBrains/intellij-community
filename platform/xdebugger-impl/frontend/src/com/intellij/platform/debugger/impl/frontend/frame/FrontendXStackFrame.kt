// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.ide.ui.colors.attributes
import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValueContainer
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.createFrontendXDebuggerEvaluator
import com.intellij.platform.debugger.impl.rpc.XStackFrameDto
import com.intellij.platform.debugger.impl.rpc.XStackFrameId
import com.intellij.platform.debugger.impl.rpc.XStackFramePresentation
import com.intellij.ui.ColoredTextContainer
import com.intellij.util.ThreeState
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XStackFrameUiPresentationContainer
import com.intellij.xdebugger.impl.frame.XStackFrameWithSeparatorAbove
import com.intellij.xdebugger.impl.rpc.sourcePosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.Color

internal class FrontendXStackFrame(
  project: Project,
  private val frameDto: XStackFrameDto,
  cs: CoroutineScope,
) : XStackFrame(), XStackFrameWithSeparatorAbove {

  val id: XStackFrameId get() = frameDto.stackFrameId

  private val evaluator by lazy {
    createFrontendXDebuggerEvaluator(project, cs, frameDto.evaluator, id)
  }

  private val xValueContainer = FrontendXValueContainer(project, cs, false, id)

  val backgroundColor: Color?
    get() = frameDto.backgroundColor?.colorId?.color()

  val canDropFlow: MutableStateFlow<CanDropState> = MutableStateFlow(CanDropState.fromThreeState(frameDto.canDrop))

  // For speedsearch in the frame list. We can't use text presentation for that as it might differ from the UI presentation.
  // Yet search shouldn't do any RPC to call customizePresentation on a backend counterpart.
  private val currentUiPresentation = MutableStateFlow(XStackFrameUiPresentationContainer())

  override fun getSourcePosition(): XSourcePosition? {
    return frameDto.sourcePosition?.sourcePosition()
  }

  fun getAlternativeSourcePosition(): XSourcePosition? {
    return frameDto.alternativeSourcePosition?.sourcePosition()
  }

  override fun getEqualityObject(): Any? = frameDto.equalityObject

  override fun computeChildren(node: XCompositeNode) {
    xValueContainer.computeChildren(node)
  }

  override fun isDocumentEvaluator(): Boolean {
    return frameDto.evaluator.canEvaluateInDocument
  }

  override fun getEvaluator(): XDebuggerEvaluator = evaluator

  override fun customizeTextPresentation(component: ColoredTextContainer) {
    val (fragments, iconId, tooltipText) = frameDto.textPresentation
    component.setIcon(iconId?.icon())
    component.setToolTipText(tooltipText)
    for ((text, attributes) in fragments) {
      component.append(text, attributes.attributes())
    }
  }

  override fun customizePresentation(component: ColoredTextContainer) {
    currentUiPresentation.value.customizePresentation(component)
  }

  fun newUiPresentation(presentation: XStackFramePresentation) {
    val presentationContainer = XStackFrameUiPresentationContainer().apply {
      setIcon(presentation.iconId?.icon())
      setToolTipText(presentation.tooltipText)
      presentation.fragments.forEach { (text, attributes) ->
        append(text, attributes.attributes())
      }
    }
    currentUiPresentation.value = presentationContainer
  }

  override fun customizePresentation(): Flow<XStackFrameUiPresentationContainer> {
    return currentUiPresentation
  }

  override fun hasSeparatorAbove(): Boolean {
    return frameDto.captionInfo.hasSeparatorAbove
  }

  override fun getCaptionAboveOf(): @NlsContexts.Separator String? {
    return frameDto.captionInfo.caption
  }

  override fun equals(other: Any?): Boolean {
    return other is FrontendXStackFrame && other.id == id
  }

  override fun hashCode(): Int {
    return id.hashCode()
  }

  internal enum class CanDropState(val state: ThreeState) {
    UNSURE(ThreeState.UNSURE), COMPUTING(ThreeState.UNSURE), YES(ThreeState.YES), NO(ThreeState.NO);

    companion object {
      fun fromThreeState(state: ThreeState): CanDropState = when (state) {
        ThreeState.YES -> YES
        ThreeState.NO -> NO
        ThreeState.UNSURE -> UNSURE
      }

      fun fromBoolean(value: Boolean): CanDropState = if (value) YES else NO
    }
  }
}
