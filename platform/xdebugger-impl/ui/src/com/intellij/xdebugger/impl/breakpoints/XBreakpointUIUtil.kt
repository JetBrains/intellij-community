// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.CommonBundle
import com.intellij.codeInsight.folding.impl.FoldingUtil
import com.intellij.codeInsight.folding.impl.actions.ExpandRegionAction
import com.intellij.icons.AllIcons
import com.intellij.lang.LanguageUtil
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointHighlighterRange
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointInstallationInfo
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.LayeredIcon
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.DocumentUtil
import com.intellij.util.SmartList
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.breakpoints.XLineBreakpointVerticalPlacement
import com.intellij.xdebugger.impl.FrontendXLineBreakpointVariant
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.computeBreakpointProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import com.intellij.xdebugger.ui.DebuggerColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import javax.swing.Icon
import javax.swing.JList
import javax.swing.event.ListSelectionEvent
import javax.swing.event.ListSelectionListener
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
object XBreakpointUIUtil {
  private val LOG = Logger.getInstance(XBreakpointUIUtil::class.java)
  private val SHOW_BREAKPOINT_AD = Ref<Boolean?>(true)

  @JvmStatic
  @JvmOverloads
  fun findSelectedBreakpoint(
    project: Project,
    editor: Editor,
    placement: XLineBreakpointVerticalPlacement = XLineBreakpointVerticalPlacement.ON_LINE,
  ): XBreakpointProxy? {
    var offset = editor.caretModel.offset
    val editorDocument = editor.document

    val textLength = editorDocument.textLength
    if (offset > textLength) {
      offset = textLength
    }

    val lineBreakpoint = findBreakpoint(project, editorDocument, editorDocument.getLineNumber(offset), placement)
    if (lineBreakpoint != null) return lineBreakpoint

    val session = XDebugManagerProxy.getInstance().getCurrentSessionProxy(project) ?: return null
    val breakpoint = session.getActiveNonLineBreakpoint() ?: return null
    val position = session.getCurrentPosition() ?: return null
    val isCurrentBreakpoint = position.file == FileDocumentManager.getInstance().getFile(editorDocument) &&
                              editorDocument.getLineNumber(offset) == position.line
    return breakpoint.takeIf { isCurrentBreakpoint }
  }

  fun findBreakpoint(
    project: Project,
    document: Document,
    line: Int,
    placement: XLineBreakpointVerticalPlacement = XLineBreakpointVerticalPlacement.ON_LINE,
  ): XLineBreakpointProxy? {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return null
    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
    for (type in breakpointManager.getLineBreakpointTypes()) {
      val breakpoint = breakpointManager.findBreakpointAtLine(type, file, line, placement)
      if (breakpoint != null) {
        return breakpoint
      }
    }
    return null
  }

  @JvmStatic
  fun supportsPlacement(type: XLineBreakpointTypeProxy, placement: XLineBreakpointVerticalPlacement): Boolean {
    return placement != XLineBreakpointVerticalPlacement.INTER_LINE || type.supportsInterLinePlacement()
  }

  /**
   * Toggle line breakpoint with editor support:
   * - unfolds folded block on the line
   * - if folded, checks if line breakpoints could be toggled inside folded text
   */
  suspend fun toggleLineBreakpoint(
    project: Project,
    position: XSourcePosition,
    selectVariantByPositionColumn: Boolean,
    editor: Editor,
    temporary: Boolean,
    moveCaret: Boolean,
    canRemove: Boolean,
    isLogging: Boolean = false,
    logExpression: String? = null,
    placement: XLineBreakpointVerticalPlacement = XLineBreakpointVerticalPlacement.ON_LINE,
  ): XLineBreakpointProxy? = withContext(Dispatchers.EDT) {
    val (typeWinner, lineWinner) = getAvailableLineBreakpointInfo(project,
                                                                  position,
                                                                  selectVariantByPositionColumn,
                                                                  editor,
                                                                  placement)
    if (typeWinner.isEmpty()) {
      fileLogger().warn("Cannot find appropriate type for line breakpoint at $position: ${position.file.url} ${position.line}")
      throw RuntimeException("Cannot find appropriate type")
    }
    val lineStart = position.line
    val winPosition = if (lineStart == lineWinner) position else XSourcePositionImpl.create(position.file, lineWinner)
    if (lineStart != lineWinner) {
      val offset = editor.document.getLineStartOffset(lineWinner)
      ExpandRegionAction.expandRegionAtOffset(editor, offset)
      if (moveCaret) {
        editor.caretModel.moveToOffset(offset)
      }
    }
    val logExpressionObject = XExpressionImpl.fromTextAndLanguage(logExpression, LanguageUtil.getFileLanguage(position.file))
    val breakpointInfo = XLineBreakpointInstallationInfo(typeWinner, winPosition, placement, temporary, isLogging,
                                                         logExpressionObject, canRemove)
    toggleLineBreakpoint(project, editor, breakpointInfo, selectVariantByPositionColumn)
  }

  @JvmOverloads
  @JvmStatic
  fun toggleLineBreakpointAsync(
    project: Project,
    position: XSourcePosition,
    selectVariantByPositionColumn: Boolean,
    editor: Editor,
    temporary: Boolean,
    moveCaret: Boolean,
    canRemove: Boolean,
    isLogging: Boolean = false,
    logExpression: String? = null,
    placement: XLineBreakpointVerticalPlacement = XLineBreakpointVerticalPlacement.ON_LINE,
  ): CompletableFuture<XLineBreakpointProxy?> = project.computeInProjectScope {
    toggleLineBreakpoint(project, position, selectVariantByPositionColumn, editor, temporary, moveCaret, canRemove, isLogging,
                         logExpression, placement)
  }

  @JvmStatic
  fun toggleLineBreakpointAsync(
    project: Project,
    editor: Editor?,
    breakpointInfo: XLineBreakpointInstallationInfo,
    selectVariantByPositionColumn: Boolean,
  ): CompletableFuture<XLineBreakpointProxy?> = project.computeInProjectScope {
    toggleLineBreakpoint(project, editor, breakpointInfo, selectVariantByPositionColumn)
  }

  private suspend fun toggleLineBreakpoint(
    project: Project,
    editor: Editor?,
    breakpointInfo: XLineBreakpointInstallationInfo,
    selectVariantByPositionColumn: Boolean,
  ): XLineBreakpointProxy? {
    return if (XDebuggerUtil.areInlineBreakpointsEnabled(breakpointInfo.position.file)) {
      processInlineBreakpoints(project, editor, breakpointInfo, selectVariantByPositionColumn)
    }
    else {
      selectBreakpointVariantWithPopup(project, breakpointInfo, editor)
    }
  }

  private suspend fun selectBreakpointVariantWithPopup(
    project: Project,
    breakpointInfo: XLineBreakpointInstallationInfo,
    editor: Editor?,
  ): XLineBreakpointProxy? {
    val file = breakpointInfo.position.file
    val line = breakpointInfo.position.line
    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)

    for (type in breakpointInfo.types) {
      val breakpoint = breakpointManager.findBreakpointAtLine(type, file, line, breakpointInfo.placement)
      if (breakpoint != null) {
        removeBreakpointIfPossible(breakpointInfo, breakpoint)
        return null
      }
    }
    return computeBreakpointProxy(project, editor, breakpointInfo) { variants ->
      assert(variants.isNotEmpty())
      withContext(Dispatchers.EDT) {
        for (type in breakpointInfo.types) {
          if (breakpointManager.findBreakpointAtLine(type, file, line, breakpointInfo.placement) != null) {
            return@withContext null
          }
        }
        val relativePoint = if (editor != null) DebuggerUIUtil.getPositionForPopup(editor, line) else null
        if (variants.size > 1 && relativePoint != null && editor != null) {
          val choice = CompletableDeferred<FrontendXLineBreakpointVariant>()
          showBreakpointSelectionPopup(project, breakpointInfo.position, editor, variants, choice, relativePoint)
          choice.await()
        }
        else {
          variants.first()
        }
      }
    }
  }

  private fun showBreakpointSelectionPopup(
    project: Project,
    position: XSourcePosition,
    editor: Editor,
    variants: List<FrontendXLineBreakpointVariant>,
    choice: CompletableDeferred<FrontendXLineBreakpointVariant>,
    relativePoint: RelativePoint,
  ) {
    val line = position.line

    class MySelectionListener : ListSelectionListener {
      var highlighter: RangeHighlighter? = null

      override fun valueChanged(e: ListSelectionEvent) {
        if (!e.valueIsAdjusting) {
          updateHighlighter((e.source as JList<*>).selectedValue)
        }
      }

      fun initialSet(value: Any?) {
        if (highlighter == null) {
          updateHighlighter(value)
        }
      }

      fun updateHighlighter(value: Any?) {
        clearHighlighter()
        if (value is FrontendXLineBreakpointVariant) {
          val lineRange = DocumentUtil.getLineTextRange(editor.document, line)
          val range = value.highlightRange ?: lineRange
          if (!range.isEmpty && range.intersectsStrict(lineRange)) {
            highlighter = editor.markupModel.addRangeHighlighter(
              DebuggerColors.BREAKPOINT_ATTRIBUTES, range.startOffset, range.endOffset,
              DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER,
              HighlighterTargetArea.EXACT_RANGE
            )
          }
        }
      }

      fun clearHighlighter() {
        highlighter?.dispose()
      }
    }

    val defaultIndex = getIndexOfBestMatchingInlineVariant(position.offset, variants)
    val selectionListener = MySelectionListener()
    val step = object :
      BaseListPopupStep<FrontendXLineBreakpointVariant>(XDebuggerBundle.message("popup.title.set.breakpoint"), variants) {
      override fun getTextFor(value: FrontendXLineBreakpointVariant): String {
        @Suppress("HardCodedStringLiteral")
        return value.text
      }

      override fun getIconFor(value: FrontendXLineBreakpointVariant): Icon? = value.icon

      override fun canceled() {
        selectionListener.clearHighlighter()
        choice.cancel()
      }

      override fun onChosen(selectedValue: FrontendXLineBreakpointVariant, finalChoice: Boolean): PopupStep<*>? {
        selectionListener.clearHighlighter()
        choice.complete(selectedValue)
        return FINAL_CHOICE
      }

      override fun getDefaultOptionIndex(): Int = defaultIndex
    }
    val popup = object : ListPopupImpl(project, step) {
      override fun afterShow() {
        super.afterShow()
        selectionListener.initialSet(list.selectedValue)
      }
    }
    DebuggerUIUtil.registerExtraHandleShortcuts(popup, SHOW_BREAKPOINT_AD, IdeActions.ACTION_TOGGLE_LINE_BREAKPOINT)
    popup.addListSelectionListener(selectionListener)
    popup.show(relativePoint)
  }

  private suspend fun processInlineBreakpoints(
    project: Project,
    editor: Editor?,
    breakpointInfo: XLineBreakpointInstallationInfo,
    selectVariantByPositionColumn: Boolean,
  ): XLineBreakpointProxy? {
    return computeBreakpointProxy(project, editor, breakpointInfo) { variants ->
      val variants = variants.filter { it.useAsInlineVariant }
      if (variants.isEmpty()) {
        LOG.error("Unexpected empty variants")
        return@computeBreakpointProxy null
      }

      val breakpoints = findBreakpointsAtLine(project, breakpointInfo)
      if (selectVariantByPositionColumn) {
        val breakpointOrVariant = getBestMatchingBreakpoint(
          breakpointInfo.position.offset,
          (breakpoints.asSequence() + variants.asSequence()).iterator()
        ) { item ->
          if (item is XLineBreakpointProxy) rangeOrNull(item.getHighlightRange())
          else (item as FrontendXLineBreakpointVariant).highlightRange
        }

        if (breakpointOrVariant is XLineBreakpointProxy) {
          removeBreakpointIfPossible(breakpointInfo, breakpointOrVariant)
          null
        }
        else {
          breakpointOrVariant as FrontendXLineBreakpointVariant
        }
      }
      else {
        if (breakpoints.isNotEmpty()) {
          removeBreakpointIfPossible(breakpointInfo, *breakpoints.toTypedArray())
          null
        }
        else {
          variants.maxBy { it.priority }
        }
      }
    }
  }

  private fun getIndexOfBestMatchingInlineVariant(caretOffset: Int, variants: List<FrontendXLineBreakpointVariant>): Int {
    assert(variants.isNotEmpty())
    var bestRange: TextRange? = null
    var bestIndex = -1
    for (i in variants.indices) {
      val range = variants[i].highlightRange
      if (range != null && range.contains(caretOffset) && (bestRange == null || bestRange.length > range.length)) {
        bestRange = range
        bestIndex = i
      }
    }
    return if (bestIndex == -1) 0 else bestIndex
  }

  private fun <T> getBestMatchingBreakpoint(
    caretOffset: Int,
    breakpoints: Iterator<T>,
    rangeProvider: (T) -> TextRange?,
  ): T {
    var bestBreakpoint: T? = null
    var bestDistance = Int.MAX_VALUE
    var bestRangeLength = Int.MAX_VALUE
    while (breakpoints.hasNext()) {
      val breakpoint = breakpoints.next()
      val range = rangeProvider(breakpoint)
      val rangeLength = range?.length ?: Int.MAX_VALUE
      val distance = when {
        range == null -> 0
        range.containsOffset(caretOffset) -> 0
        else -> min(abs(range.startOffset - caretOffset), abs(range.endOffset - caretOffset))
      }
      if (bestBreakpoint == null || distance < bestDistance || (distance == bestDistance && rangeLength < bestRangeLength)) {
        bestBreakpoint = breakpoint
        bestDistance = distance
        bestRangeLength = rangeLength
      }
    }
    return checkNotNull(bestBreakpoint)
  }

  private fun rangeOrNull(range: XLineBreakpointHighlighterRange?): TextRange? {
    return (range as? XLineBreakpointHighlighterRange.Available)?.range
  }

  private suspend fun getAvailableLineBreakpointInfo(
    project: Project,
    position: XSourcePosition,
    selectTypeByPositionColumn: Boolean,
    editor: Editor,
    placement: XLineBreakpointVerticalPlacement,
  ): Pair<List<XLineBreakpointTypeProxy>, Int> {
    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
    val lineTypes = breakpointManager.getLineBreakpointTypes()
    return getAvailableLineBreakpointInfo(position, selectTypeByPositionColumn, editor, lineTypes,
                                          { type, line -> breakpointManager.findBreakpointAtLine(type, position.file, line, placement) },
                                          { type -> type.priority },
                                          { callback -> readAction { callback() } },
                                          { type, line -> supportsPlacement(type, placement) && type.canPutAt(editor, line, project) })
  }

  inline fun <T, B> getAvailableLineBreakpointInfo(
    position: XSourcePosition,
    selectTypeByPositionColumn: Boolean,
    editor: Editor?,
    lineTypes: List<T>,
    breakpointProvider: (T, Int) -> B?,
    crossinline computePriority: (T) -> Int,
    runReadAction: (callback: () -> Unit) -> Unit,
    canPutAt: (T, Int) -> Boolean,
  ): Pair<List<T>, Int> {
    val lineStart = position.line
    val file = position.file

    if (!file.isValid) {
      return Pair.create(emptyList(), -1)
    }

    // for folded text check each line and find out type with the biggest priority,
    // do it unless we were asked to select type strictly by caret position
    var linesEnd = lineStart
    if (editor != null && !selectTypeByPositionColumn) {
      runReadAction {
        val region = FoldingUtil.findFoldRegionStartingAtLine(editor, lineStart)
        if (region != null && !region.isExpanded) {
          linesEnd = region.document.getLineNumber(region.endOffset)
        }
      }
    }
    val typeWinner = SmartList<T>()
    var lineWinner = -1
    if (linesEnd != lineStart) { // folding mode
      for (line in lineStart..linesEnd) {
        var maxPriority = 0
        for (type in lineTypes) {
          maxPriority = max(maxPriority, computePriority(type))
          val breakpoint = breakpointProvider(type, line)
          if ((canPutAt(type, line) || breakpoint != null) &&
              (typeWinner.isEmpty() || computePriority(type) > computePriority(typeWinner[0]))
          ) {
            typeWinner.clear()
            typeWinner.add(type)
            lineWinner = line
          }
        }
        // already found max priority type - stop
        if (!typeWinner.isEmpty() && computePriority(typeWinner[0]) == maxPriority) {
          break
        }
      }
    }
    else {
      for (type in lineTypes) {
        val breakpoint = breakpointProvider(type, lineStart)
        if (canPutAt(type, lineStart) || breakpoint != null) {
          typeWinner.add(type)
          lineWinner = lineStart
        }
      }
      // First type is the most important one.
      typeWinner.sortByDescending { computePriority(it) }
    }
    return Pair.create(typeWinner, lineWinner)
  }

  internal fun findBreakpointsAtLine(
    project: Project,
    breakpointInfo: XLineBreakpointInstallationInfo,
  ): List<XLineBreakpointProxy> {
    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
    val file = breakpointInfo.position.file
    val line = breakpointInfo.position.line
    return breakpointInfo.types
      .flatMap { t -> breakpointManager.findBreakpointsAtLine(t, file, line, breakpointInfo.placement) }
      .toList()
  }

  internal suspend fun <T : XBreakpointProxy> removeBreakpointIfPossible(
    info: XLineBreakpointInstallationInfo,
    vararg breakpoints: T,
  ) {
    if (!info.canRemoveBreakpoint()) {
      return
    }

    removeBreakpointsWithConfirmation(*breakpoints).await()
  }

  /**
   * Remove breakpoint. Show confirmation dialog if breakpoint has non-empty condition or log expression.
   * Returns whether breakpoint was really deleted.
   */
  @JvmStatic
  fun removeBreakpointWithConfirmation(breakpoint: XBreakpointProxy): CompletableFuture<Boolean> {
    val project = breakpoint.project
    if ((!DebuggerUIUtil.isEmptyExpression(breakpoint.getConditionExpression()) || !DebuggerUIUtil.isEmptyExpression(breakpoint.getLogExpressionObject())) &&
        !ApplicationManager.getApplication().isHeadlessEnvironment &&
        !ApplicationManager.getApplication().isUnitTestMode &&
        XDebuggerSettingsManager.getInstance().generalSettings.isConfirmBreakpointRemoval) {
      @Suppress("HardCodedStringLiteral")
      val message = buildString {
        append("<html>")
        append(XDebuggerBundle.message("message.confirm.breakpoint.removal.message"))
        if (!DebuggerUIUtil.isEmptyExpression(breakpoint.getConditionExpression())) {
          append("<br>")
          append(XDebuggerBundle.message("message.confirm.breakpoint.removal.message.condition"))
          append("<br><pre>")
          append(StringUtil.escapeXmlEntities(breakpoint.getConditionExpression()!!.expression))
          append("</pre>")
        }
        if (!DebuggerUIUtil.isEmptyExpression(breakpoint.getLogExpressionObject())) {
          append("<br>")
          append(XDebuggerBundle.message("message.confirm.breakpoint.removal.message.log"))
          append("<br><pre>")
          append(StringUtil.escapeXmlEntities(breakpoint.getLogExpressionObject()!!.expression))
          append("</pre>")
        }
      }
      if (Messages.showOkCancelDialog(
          message,
          XDebuggerBundle.message("message.confirm.breakpoint.removal.title"),
          CommonBundle.message("button.remove"),
          Messages.getCancelButton(),
          Messages.getQuestionIcon(),
          object : DoNotAskOption.Adapter() {
            override fun rememberChoice(isSelected: Boolean, exitCode: Int) {
              if (isSelected) {
                XDebuggerSettingsManager.getInstance().generalSettings.isConfirmBreakpointRemoval = false
              }
            }
          }
        ) != Messages.OK) {
        return CompletableFuture.completedFuture(false)
      }
    }
    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
    breakpointManager.rememberRemovedBreakpoint(breakpoint)
    return breakpointManager.removeBreakpoint(breakpoint).thenApply { true }
  }

  @JvmStatic
  fun removeBreakpointsWithConfirmation(breakpoints: List<XBreakpointProxy>): CompletableFuture<Void?> {
    if (breakpoints.isEmpty()) return CompletableFuture.completedFuture(null)
    // FIXME[inline-bp]: support multiple breakpoints restore
    // FIXME[inline-bp]: Reconsider this, maybe we should have single confirmation for all breakpoints.
    return removeBreakpointsWithConfirmation(*breakpoints.toTypedArray())
  }

  private fun <T : XBreakpointProxy> removeBreakpointsWithConfirmation(vararg breakpoints: T): CompletableFuture<Void?> {
    val futures = breakpoints.map { removeBreakpointWithConfirmation(it) }
    return CompletableFuture.allOf(*futures.toTypedArray())
  }

  @JvmStatic
  fun calculateIcon(breakpoint: XBreakpointProxy): Icon {
    val specialIcon = calculateSpecialIcon(breakpoint)
    val icon = specialIcon ?: breakpoint.type.enabledIcon
    return withQuestionBadgeIfNeeded(icon, breakpoint)
  }

  private fun withQuestionBadgeIfNeeded(icon: Icon, breakpoint: XBreakpointProxy): Icon {
    if (DebuggerUIUtil.isEmptyExpression(breakpoint.getConditionExpression()) && !breakpoint.hasCustomCondition()) {
      return icon
    }
    val newIcon = LayeredIcon(2)
    newIcon.setIcon(icon, 0)
    val hShift = if (ExperimentalUI.isNewUI()) 7 else 10
    newIcon.setIcon(AllIcons.Debugger.Question_badge, 1, hShift, 6)
    return JBUIScale.scaleIcon(newIcon)
  }

  private fun calculateSpecialIcon(breakpoint: XBreakpointProxy): Icon? {
    val type = breakpoint.type
    val debugManager = XDebugManagerProxy.getInstance()
    val session = debugManager.getCurrentSessionProxy(breakpoint.project)
    val breakpointManager = debugManager.getBreakpointManagerProxy(breakpoint.project)

    if (!breakpoint.isEnabled()) {
      return if (session != null && session.areBreakpointsMuted()) {
        type.mutedDisabledIcon
      }
      else if (breakpoint.getSuspendPolicy() == SuspendPolicy.NONE) {
        type.suspendNoneDisabledIcon
      }
      else {
        type.disabledIcon
      }
    }

    if (session == null) {
      if (breakpointManager.dependentBreakpointManager.getMasterBreakpoint(breakpoint) != null) {
        return type.inactiveDependentIcon
      }
    }
    else {
      if (session.areBreakpointsMuted()) {
        return type.mutedEnabledIcon
      }
      if (session.isInactiveSlaveBreakpoint(breakpoint)) {
        return type.inactiveDependentIcon
      }
      breakpoint.getCustomizedPresentationForCurrentSession()?.icon?.let { return it }
    }

    if (breakpoint.getSuspendPolicy() == SuspendPolicy.NONE) {
      return type.suspendNoneIcon
    }

    breakpoint.getCustomizedPresentation()?.icon?.let { return it }

    if (breakpoint is XLineBreakpointProxy && breakpoint.isTemporary() && breakpoint.type.temporaryIcon != null) {
      return breakpoint.type.temporaryIcon
    }

    return null
  }
}

@Service(Service.Level.PROJECT)
private class XBreakpointUtilProjectCoroutineScope(val cs: CoroutineScope)

// TODO: Replace with `coroutineScope.future` after IJPL-184112 is fixed.
private fun <T> Project.computeInProjectScope(action: suspend () -> T): CompletableFuture<T> {
  val result = CompletableFuture<T>()
  service<XBreakpointUtilProjectCoroutineScope>().cs.launch(start = CoroutineStart.ATOMIC) {
    try {
      result.complete(action())
    }
    catch (e: CancellationException) {
      result.completeExceptionally(e)
      throw e
    }
    catch (e: Throwable) {
      result.completeExceptionally(e)
    }
  }
  return result
}
