// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValueContainer
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.createFrontendXDebuggerEvaluator
import com.intellij.platform.debugger.impl.rpc.*
import com.intellij.ui.ColoredTextContainer
import com.intellij.util.ThreeState
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XStackFrameUiPresentationContainer
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.awt.Color

internal class FrontendXStackFrame(
  val id: XStackFrameId,
  private val project: Project,
  private val suspendContextLifetimeScope: CoroutineScope,
  private val sourcePosition: XSourcePositionDto?,
  private val customBackgroundInfo: XStackFrameCustomBackgroundInfo?,
  private val equalityObject: XStackFrameEqualityObject?,
  private val evaluatorDto: XDebuggerEvaluatorDto,
  private val captionInfo: XStackFrameCaptionInfo,
  private val textPresentation: XStackFramePresentation,
  canDrop: ThreeState,
) : XStackFrame(), XDebuggerFramesList.ItemWithSeparatorAbove {
  private val evaluator by lazy {
    createFrontendXDebuggerEvaluator(project, suspendContextLifetimeScope, evaluatorDto, id)
  }

  private val xValueContainer = FrontendXValueContainer(project, suspendContextLifetimeScope, false) {
    XExecutionStackApi.getInstance().computeVariables(id)
  }

  val fileId: VirtualFileId? get() = sourcePosition?.fileId

  // TODO[IJPL-177087]: collapse these two into a three-state value? (NoCustomBackground/WithCustomBackground(null)/WithCustomBackground(color)?
  //  Is it worth it?
  val requiresCustomBackground: Boolean
    get() = customBackgroundInfo != null
  val customBackgroundColor: Color?
    get() = customBackgroundInfo?.backgroundColor?.color()

  val canDropFlow: MutableStateFlow<CanDropState> = MutableStateFlow(CanDropState.fromThreeState(canDrop))

  // For speedsearch in the frame list. We can't use text presentation for that as it might differ from the UI presentation.
  // Yet search shouldn't do any RPC to call customizePresentation on a backend counterpart.
  private val currentUiPresentation = MutableStateFlow(XStackFrameUiPresentationContainer())

  override fun getSourcePosition(): XSourcePosition? {
    return sourcePosition?.sourcePosition()
  }

  override fun getEqualityObject(): Any? = equalityObject

  override fun computeChildren(node: XCompositeNode) {
    xValueContainer.computeChildren(node)
  }

  override fun isDocumentEvaluator(): Boolean {
    return evaluatorDto.canEvaluateInDocument
  }

  override fun getEvaluator(): XDebuggerEvaluator = evaluator

  override fun customizeTextPresentation(component: ColoredTextContainer) {
    val (fragments, iconId, tooltipText) = textPresentation
    component.setIcon(iconId?.icon())
    component.setToolTipText(tooltipText)
    for ((text, attributes) in fragments) {
      component.append(text, attributes.toSimpleTextAttributes())
    }
  }

  override fun customizePresentation(component: ColoredTextContainer) {
    currentUiPresentation.value.customizePresentation(component)
  }

  override fun customizePresentation(): Flow<XStackFrameUiPresentationContainer> {
    return channelFlow {
      suspendContextLifetimeScope.launch {
        XExecutionStackApi.getInstance().computeUiPresentation(id).collectLatest { presentation ->
          val presentation = XStackFrameUiPresentationContainer().apply {
            setIcon(presentation.iconId?.icon())
            setToolTipText(presentation.tooltipText)
            presentation.fragments.forEach { (text, attributes) ->
              append(text, attributes.toSimpleTextAttributes())
            }
          }
          send(presentation)
          currentUiPresentation.value = presentation
        }
      }
      awaitClose()
    }
  }

  override fun hasSeparatorAbove(): Boolean {
    return captionInfo.hasSeparatorAbove
  }

  override fun getCaptionAboveOf(): @NlsContexts.Separator String? {
    return captionInfo.caption
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
