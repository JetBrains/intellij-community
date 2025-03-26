// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValueContainer
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.createFrontendXDebuggerEvaluator
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.rpc.XExecutionStackApi
import com.intellij.xdebugger.impl.rpc.XStackFrameDto
import com.intellij.xdebugger.impl.rpc.XStackFrameId
import com.intellij.xdebugger.impl.rpc.sourcePosition
import kotlinx.coroutines.CoroutineScope

// TODO[IJPL-177087] methods
internal class FrontendXStackFrame(
  private val frameDto: XStackFrameDto,
  private val project: Project,
  private val cs: CoroutineScope,
) : XStackFrame() {
  private val evaluator by lazy {
    createFrontendXDebuggerEvaluator(project, cs, frameDto.evaluator, frameDto.stackFrameId)
  }

  private val xValueContainer = FrontendXValueContainer(project, cs, false) {
    XExecutionStackApi.getInstance().computeVariables(frameDto.stackFrameId)
  }

  val id: XStackFrameId get() = frameDto.stackFrameId

  override fun getSourcePosition(): XSourcePosition? {
    return frameDto.sourcePosition?.sourcePosition()
  }

  override fun getEqualityObject(): Any? = frameDto.equalityObject

  override fun computeChildren(node: XCompositeNode) {
    xValueContainer.computeChildren(node)
  }

  override fun getEvaluator(): XDebuggerEvaluator? = evaluator
}
