// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.ide.ui.colors.color
import com.intellij.ide.ui.icons.icon
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValueContainer
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.createFrontendXDebuggerEvaluator
import com.intellij.platform.debugger.impl.frontend.storage.currentPresentation
import com.intellij.ui.ColoredTextContainer
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList
import com.intellij.xdebugger.impl.rpc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

internal class FrontendXStackFrame(
  val id: XStackFrameId,
  private val project: Project,
  private val suspendContextLifetimeScope: CoroutineScope,
  private val sourcePosition: XSourcePositionDto?,
  private val customBackgroundInfo: XStackFrameCustomBackgroundInfo?,
  private val equalityObject: XStackFrameEqualityObject?,
  private val evaluatorDto: XDebuggerEvaluatorDto,
  private val captionInfo: XStackFrameCaptionInfo,
  private val canDrop: Boolean,
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
  val customBackgroundColor: java.awt.Color?
    get() = customBackgroundInfo?.backgroundColor?.color()

  val canDropFlow = MutableStateFlow(if (canDrop) CanDropState.CAN_DROP else CanDropState.UNSURE)

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

  override fun getEvaluator(): XDebuggerEvaluator? = evaluator

  override fun customizePresentation(component: ColoredTextContainer) {
    // TODO[IJPL-177087]: what if presentation changes over time for the same backend stack frame?
    //  Probably we should go for a different approach: store something that can trigger XDebuggerFramesList to repaint,
    //  repurpose this method implementation to send a request to backend to customize presentation there
    //  (probably via smth like BackendXStackFrameApi.customizePresentation which I removed in this commit),
    //  collect changes here (like it was for BackendXStackFrameApi.customizePresentation),
    //  and in this collection trigger XDebuggerFramesList.repaint.
    //  ...
    //  But maybe there are easier solutions
    val (fragments, iconId, tooltipText) = suspendContextLifetimeScope.currentPresentation(id) ?: return
    component.setIcon(iconId?.icon())
    component.setToolTipText(tooltipText)
    for ((text, attributes) in fragments) {
      component.append(text, attributes.toSimpleTextAttributes())
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

  /**
  canDrop can depend on other frames in the stack,
  and thus it can change after other stacks are loaded.
  Hypothesis: canDrop can only change once and only from false to true,
  ergo,
  - if an RPC request returns true, then it's final -- CAN_DROP; no more recomputations
  - if an RPC request returns false, we can't say if it's final or not -- UNSURE; should request more if necessary
  - COMPUTING means that we haven't received a response yet; don't do more requests
   */
  internal enum class CanDropState(val canDrop: Boolean = false) {
    UNSURE, COMPUTING, CAN_DROP(true)
  }
}
