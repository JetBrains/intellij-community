// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.frame

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValueContainer
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.createFrontendXDebuggerEvaluator
import com.intellij.ui.ColoredText
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.rpc.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.Icon

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

  private val presentationEvents = mutableListOf<XStackFramePresentationEvent>()

  private var icon: Icon? = null
  private var tooltipText: @NlsSafe String? = null

  init {
    cs.launch {
      XStackFrameApi.getInstance().customizePresentation(id).collect {
        when (it) {
          is XStackFramePresentationEvent.AppendColoredText, is XStackFramePresentationEvent.AppendTextWithAttributes -> {
            presentationEvents.add(it)
          }
          is XStackFramePresentationEvent.SetIcon -> {
            icon = it.iconId?.icon()
          }
          is XStackFramePresentationEvent.SetTooltip -> {
            tooltipText = it.text
          }
        }
      }
    }
  }

  override fun getSourcePosition(): XSourcePosition? {
    return frameDto.sourcePosition?.sourcePosition()
  }

  override fun getEqualityObject(): Any? = frameDto.equalityObject

  override fun computeChildren(node: XCompositeNode) {
    xValueContainer.computeChildren(node)
  }

  override fun isDocumentEvaluator(): Boolean {
    return frameDto.evaluator.canEvaluateInDocument
  }

  override fun getEvaluator(): XDebuggerEvaluator? = evaluator

  override fun customizePresentation(component: ColoredTextContainer) {
    component.setIcon(icon)
    component.setToolTipText(tooltipText)
    for (event in presentationEvents) {
      when (event) {
        is XStackFramePresentationEvent.AppendTextWithAttributes -> {
          component.append(event.fragment, event.attributes ?: SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
        is XStackFramePresentationEvent.AppendColoredText -> {
          val builder = ColoredText.builder()
          for (fragment in event.fragments) {
            builder.append(fragment.text, fragment.attributes ?: SimpleTextAttributes.REGULAR_ATTRIBUTES)
          }
          component.append(builder.build())
        }
        is XStackFramePresentationEvent.SetIcon -> {
          // ignore this events, they are handled by `icon`
        }
        is XStackFramePresentationEvent.SetTooltip -> {
          // ignore this events, they are handled by `tooltipText`
        }
      }
    }
  }
}
