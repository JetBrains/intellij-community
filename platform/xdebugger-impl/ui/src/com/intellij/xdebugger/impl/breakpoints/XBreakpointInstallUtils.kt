// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy.Companion.getInstance
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointHighlighterRange
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointInstallationInfo
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.DocumentUtil
import com.intellij.util.ModalityUiUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XExpression;
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XLineBreakpointVerticalPlacement
import com.intellij.xdebugger.impl.FrontendXLineBreakpointVariant
import com.intellij.xdebugger.impl.VariantChoiceData
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUIUtil.findBreakpointsAtLine
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUIUtil.removeBreakpointIfPossible
import com.intellij.xdebugger.impl.computeBreakpointProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.ui.DebuggerColors
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import javax.swing.Icon
import javax.swing.JList
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import kotlin.math.abs
import kotlin.math.min

@ApiStatus.Internal
object XBreakpointInstallUtils {
  private val LOG = Logger.getInstance(XBreakpointInstallUtils::class.java)
  private val SHOW_BREAKPOINT_AD = Ref<Boolean?>(true)

  @JvmStatic
  fun toggleAndReturnLineBreakpointProxy(
    project: Project,
    types: List<XLineBreakpointTypeProxy>,
    position: XSourcePosition,
    selectVariantByPositionColumn: Boolean,
    temporary: Boolean,
    editor: Editor?,
    canRemove: Boolean,
    isLogging: Boolean,
    logExpression: XExpression?,
  ): CompletableFuture<XLineBreakpointProxy?> {
    val breakpointInfo = XLineBreakpointInstallationInfo(types,
                                                         position,
                                                         XLineBreakpointVerticalPlacement.ON_LINE,
                                                         temporary,
                                                         isLogging,
                                                         logExpression,
                                                         canRemove)
    return toggleAndReturnLineBreakpointProxy(project, editor, breakpointInfo, selectVariantByPositionColumn)
  }

  @JvmStatic
  fun toggleAndReturnLineBreakpointProxy(
    project: Project,
    editor: Editor?,
    breakpointInfo: XLineBreakpointInstallationInfo,
    selectVariantByPositionColumn: Boolean,
  ): CompletableFuture<XLineBreakpointProxy?> {
    if (XDebuggerUtil.areInlineBreakpointsEnabled(breakpointInfo.position.getFile())) {
      return processInlineBreakpoints(project, editor, breakpointInfo, selectVariantByPositionColumn)
    }
    else {
      return selectBreakpointVariantWithPopup(project, breakpointInfo, editor)
    }
  }

  private fun selectBreakpointVariantWithPopup(
    project: Project,
    breakpointInfo: XLineBreakpointInstallationInfo,
    editor: Editor?,
  ): CompletableFuture<XLineBreakpointProxy?> {
    val file = breakpointInfo.position.getFile()
    val line = breakpointInfo.position.getLine()
    val breakpointManager = getInstance().getBreakpointManagerProxy(project)

    for (type in breakpointInfo.types) {
      val breakpoint = breakpointManager.findBreakpointAtLine(type, file, line, breakpointInfo.placement)
      if (breakpoint != null) {
        return removeBreakpointIfPossible(breakpointInfo, breakpoint).thenApply { null }
      }
    }
    return computeBreakpointProxy(project, editor, breakpointInfo) { variantChoice: VariantChoiceData ->
      assert(!variantChoice.variants.isEmpty())
      ModalityUiUtil.invokeLaterIfNeeded(ModalityState.defaultModalityState()) {
        for (type in breakpointInfo.types) {
          if (breakpointManager.findBreakpointAtLine(type, file, line, breakpointInfo.placement) != null) {
            variantChoice.breakpointRemoved()
            return@invokeLaterIfNeeded
          }
        }
        val relativePoint = if (editor != null) DebuggerUIUtil.getPositionForPopup(editor, line) else null
        if (variantChoice.variants.size > 1 && relativePoint != null) {
          showBreakpointSelectionPopup(
            project,
            breakpointInfo.position,
            editor!!,
            variantChoice,
            relativePoint
          )
        }
        else {
          variantChoice.select(variantChoice.variants[0])
        }
      }
    }
  }

  private fun showBreakpointSelectionPopup(
    project: Project,
    position: XSourcePosition,
    editor: Editor,
    choiceData: VariantChoiceData,
    relativePoint: RelativePoint,
  ) {
    val line = position.getLine()

    class MySelectionListener : ListSelectionListener {
      var myHighlighter: RangeHighlighter? = null

      override fun valueChanged(e: ListSelectionEvent) {
        if (!e.valueIsAdjusting) {
          updateHighlighter((e.getSource() as JList<*>).getSelectedValue())
        }
      }

      fun initialSet(value: Any?) {
        if (myHighlighter == null) {
          updateHighlighter(value)
        }
      }

      fun updateHighlighter(value: Any?) {
        clearHighlighter()
        if (value is FrontendXLineBreakpointVariant) {
          var range = value.highlightRange
          val lineRange = DocumentUtil.getLineTextRange(editor.getDocument(), line)
          if (range == null) {
            range = lineRange
          }
          if (!range.isEmpty && range.intersectsStrict(lineRange)) {
            myHighlighter = editor.getMarkupModel().addRangeHighlighter(
              DebuggerColors.BREAKPOINT_ATTRIBUTES, range.startOffset, range.endOffset,
              DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER,
              HighlighterTargetArea.EXACT_RANGE
            )
          }
        }
      }

      fun clearHighlighter() {
        myHighlighter?.dispose()
      }
    }

    val defaultIndex = getIndexOfBestMatchingInlineVariant(position.getOffset(), choiceData.variants)

    val selectionListener = MySelectionListener()
    val step = object :
      BaseListPopupStep<FrontendXLineBreakpointVariant>(XDebuggerBundle.message("popup.title.set.breakpoint"), choiceData.variants) {
      override fun getTextFor(value: FrontendXLineBreakpointVariant): String {
        @Suppress("HardCodedStringLiteral")
        return value.text
      }

      override fun getIconFor(value: FrontendXLineBreakpointVariant): Icon? {
        return value.icon
      }

      override fun canceled() {
        selectionListener.clearHighlighter()
        choiceData.cancel()
      }

      override fun onChosen(selectedValue: FrontendXLineBreakpointVariant, finalChoice: Boolean): PopupStep<*>? {
        selectionListener.clearHighlighter()
        choiceData.select(selectedValue)
        return FINAL_CHOICE
      }

      override fun getDefaultOptionIndex(): Int {
        return defaultIndex
      }
    }
    val popup: ListPopupImpl = object : ListPopupImpl(project, step) {
      override fun afterShow() {
        super.afterShow()
        selectionListener.initialSet(list.getSelectedValue())
      }
    }
    DebuggerUIUtil.registerExtraHandleShortcuts(popup, SHOW_BREAKPOINT_AD, IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT)

    popup.addListSelectionListener(selectionListener)
    popup.show(relativePoint)
  }

  private fun processInlineBreakpoints(
    project: Project,
    editor: Editor?,
    breakpointInfo: XLineBreakpointInstallationInfo,
    selectVariantByPositionColumn: Boolean,
  ): CompletableFuture<XLineBreakpointProxy?> {
    return computeBreakpointProxy(project, editor, breakpointInfo) { variantChoice ->
      val variants = variantChoice.variants.filter { v -> v.useAsInlineVariant }
      if (variants.isEmpty()) {
        LOG.error("Unexpected empty variants")
        variantChoice.cancel()
        return@computeBreakpointProxy
      }

      val breakpoints = findBreakpointsAtLine(project, breakpointInfo)

      val variant: FrontendXLineBreakpointVariant?
      if (selectVariantByPositionColumn) {
        val breakpointOrVariant = getBestMatchingBreakpoint(
          breakpointInfo.position.getOffset(),
          (breakpoints.asSequence() + variants.asSequence()).iterator()
        ) { o ->
          if (o is XLineBreakpointProxy)
            rangeOrNull(o.getHighlightRange())
          else
            (o as FrontendXLineBreakpointVariant).highlightRange
        }

        if (breakpointOrVariant is XLineBreakpointProxy) {
          removeBreakpointIfPossible(breakpointInfo, breakpointOrVariant)
            .thenRun { variantChoice.breakpointRemoved() }
          return@computeBreakpointProxy
        }

        variant = breakpointOrVariant as FrontendXLineBreakpointVariant
      }
      else {
        if (!breakpoints.isEmpty()) {
          removeBreakpointIfPossible(breakpointInfo, *breakpoints.toTypedArray())
            .thenRun { variantChoice.breakpointRemoved() }
          return@computeBreakpointProxy
        }

        variant = variants.maxBy { v -> v.priority }
      }

      variantChoice.select(variant)
    }
  }

}

private fun getIndexOfBestMatchingInlineVariant(caretOffset: Int, variants: List<FrontendXLineBreakpointVariant>): Int {
  assert(!variants.isEmpty())
  var bestRange: TextRange? = null
  var bestIndex = -1
  for (i in variants.indices) {
    val variant = variants[i]
    val range = variant.highlightRange
    if (range != null && range.contains(caretOffset)) {
      if (bestRange == null || bestRange.length > range.length) {
        bestRange = range
        bestIndex = i
      }
    }
  }
  // Use first variant if nothing interesting is found.
  return if (bestIndex == -1) 0 else bestIndex
}

private fun <T> getBestMatchingBreakpoint(
  caretOffset: Int,
  breakpoints: Iterator<T>,
  rangeProvider: (T) -> TextRange?,
): T {
  // Best matching = closest to the insertion point and minimal by range of all breakpoints or breakpoint variants
  var bestBreakpoint: T? = null
  var bestDistance = Int.MAX_VALUE
  var bestRangeLength = Int.MAX_VALUE
  while (breakpoints.hasNext()) {
    val b = breakpoints.next()
    val range = rangeProvider(b)
    val rangeLength = range?.length ?: Int.MAX_VALUE
    val distance = when {
      range == null -> 0 // note that range = null means "whole line"
      range.containsOffset(caretOffset) -> 0 //include end offset
      else -> min(abs(range.startOffset - caretOffset), abs(range.endOffset - caretOffset))
    }
    if (bestBreakpoint == null || distance < bestDistance || (distance == bestDistance && rangeLength < bestRangeLength)) {
      bestBreakpoint = b
      bestDistance = distance
      bestRangeLength = rangeLength
    }
  }
  checkNotNull(bestBreakpoint)
  return bestBreakpoint
}

private fun rangeOrNull(range: XLineBreakpointHighlighterRange?): TextRange? {
  if (range is XLineBreakpointHighlighterRange.Available) {
    return range.range
  }
  return null
}
