// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.CommonBundle
import com.intellij.codeInsight.folding.impl.FoldingUtil
import com.intellij.codeInsight.folding.impl.actions.ExpandRegionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.util.text.StringUtil
import com.intellij.platform.debugger.impl.shared.proxy.XBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointInstallationInfo
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointTypeProxy
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.LayeredIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.SmartList
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.SuspendPolicy
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.CompletableFuture
import javax.swing.Icon
import kotlin.math.max

@ApiStatus.Internal
object XBreakpointUIUtil {
  @JvmStatic
  fun findSelectedBreakpointProxy(project: Project, editor: Editor): Pair<GutterIconRenderer?, XBreakpointProxy?> {
    var offset = editor.caretModel.offset
    val editorDocument = editor.document

    val textLength = editorDocument.textLength
    if (offset > textLength) {
      offset = textLength
    }

    val breakpoint = findBreakpoint(project, editorDocument, editorDocument.getLineNumber(offset))
    if (breakpoint != null) {
      return Pair.create(breakpoint.getGutterIconRenderer(), breakpoint)
    }

    val session = XDebugManagerProxy.getInstance().getCurrentSessionProxy(project)
    if (session != null) {
      val breakpoint = session.getActiveNonLineBreakpoint()
      if (breakpoint != null) {
        val position = session.getCurrentPosition()
        if (position != null) {
          if (position.file == FileDocumentManager.getInstance().getFile(editorDocument) &&
              editorDocument.getLineNumber(offset) == position.line
          ) {
            return Pair.create(breakpoint.createGutterIconRenderer(), breakpoint)
          }
        }
      }
    }

    return Pair.create(null, null)
  }

  fun findBreakpoint(project: Project, document: Document, line: Int): XLineBreakpointProxy? {
    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
    val file = FileDocumentManager.getInstance().getFile(document) ?: return null
    for (type in breakpointManager.getLineBreakpointTypes()) {
      val breakpoint = breakpointManager.findBreakpointAtLine(type, file, line)
      if (breakpoint != null) {
        return breakpoint
      }
    }
    return null
  }

  /**
   * Toggle line breakpoint with editor support:
   * - unfolds folded block on the line
   * - if folded, checks if line breakpoints could be toggled inside folded text
   */
  @JvmOverloads
  @JvmStatic
  fun toggleLineBreakpointProxy(
    project: Project,
    position: XSourcePosition,
    selectVariantByPositionColumn: Boolean,
    editor: Editor,
    temporary: Boolean,
    moveCaret: Boolean,
    canRemove: Boolean,
    isLogging: Boolean = false,
    logExpression: String? = null,
  ): CompletableFuture<XLineBreakpointProxy?> {
    // TODO: Replace with `coroutineScope.future` after IJPL-184112 is fixed
    val future = CompletableFuture<XLineBreakpointProxy?>()
    project.service<XBreakpointUtilProjectCoroutineScope>().cs.launch(Dispatchers.EDT) {
      try {
        val (typeWinner, lineWinner) = getAvailableLineBreakpointInfoProxy(project, position, selectVariantByPositionColumn, editor)
        if (typeWinner.isEmpty()) {
          fileLogger().warn("Cannot find appropriate type for line breakpoint at $position: ${position.file.url} ${position.line}")
          future.completeExceptionally(RuntimeException("Cannot find appropriate type"))
          return@launch
        }
        val lineStart = position.line
        val winPosition = if (lineStart == lineWinner) position else XSourcePositionImpl.create(position.file, lineWinner)

        val res = XBreakpointInstallUtils.toggleAndReturnLineBreakpointProxy(
          project, typeWinner, winPosition, selectVariantByPositionColumn, temporary, editor, canRemove, isLogging, logExpression)
        if (lineStart != lineWinner) {
          val offset = editor.document.getLineStartOffset(lineWinner)
          ExpandRegionAction.expandRegionAtOffset(editor, offset)
          if (moveCaret) {
            editor.caretModel.moveToOffset(offset)
          }
        }
        future.complete(res.await())
      }
      catch (e: Throwable) {
        future.completeExceptionally(e)
      }
    }

    return future
  }

  private suspend fun getAvailableLineBreakpointInfoProxy(
    project: Project,
    position: XSourcePosition,
    selectTypeByPositionColumn: Boolean,
    editor: Editor,
  ): Pair<List<XLineBreakpointTypeProxy>, Int> {
    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
    val lineTypes = breakpointManager.getLineBreakpointTypes()
    return getAvailableLineBreakpointInfo(position, selectTypeByPositionColumn, editor, lineTypes,
                                          { type, line -> breakpointManager.findBreakpointAtLine(type, position.file, line) },
                                          { type -> type.priority },
                                          { callback -> readAction { callback() } },
                                          { type, line -> type.canPutAt(editor, line, project) })
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

  @JvmStatic
  fun findBreakpointsAtLine(
    project: Project,
    breakpointInfo: XLineBreakpointInstallationInfo,
  ): List<XLineBreakpointProxy> {
    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
    val file = breakpointInfo.position.file
    val line = breakpointInfo.position.line
    return breakpointInfo.types
      .flatMap { t -> breakpointManager.findBreakpointsAtLine(t, file, line) }
      .toList()
  }

  @JvmStatic
  fun <T : XBreakpointProxy> removeBreakpointIfPossible(
    info: XLineBreakpointInstallationInfo,
    vararg breakpoints: T,
  ): CompletableFuture<Void?> {
    if (!info.canRemoveBreakpoint()) {
      return CompletableFuture.completedFuture(null)
    }

    return removeBreakpointsWithConfirmation(*breakpoints)
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
    if (DebuggerUIUtil.isEmptyExpression(breakpoint.getConditionExpression())) {
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
