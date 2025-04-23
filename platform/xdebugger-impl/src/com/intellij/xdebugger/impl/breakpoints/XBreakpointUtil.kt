// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.codeInsight.folding.impl.FoldingUtil
import com.intellij.codeInsight.folding.impl.actions.ExpandRegionAction
import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.*
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise
import kotlin.math.max

object XBreakpointUtil {
  /**
   * The forcibly shortened version of [XBreakpointType.getShortText].
   */
  @JvmStatic
  fun getShortText(breakpoint: XBreakpoint<*>): @Nls String {
    val len = 70
    return StringUtil.shortenTextWithEllipsis(breakpoint.shortText, len, len / 2)
  }

  /**
   * @see XBreakpointType.getDisplayText
   */
  @JvmStatic
  fun getDisplayText(breakpoint: XBreakpoint<*>): @Nls String =
    breakpoint.displayText

  /**
   * @see XBreakpointType.getGeneralDescription
   */
  @JvmStatic
  fun getGeneralDescription(breakpoint: XBreakpoint<*>): @Nls String =
    breakpoint.generalDescription

  /**
   * @see XBreakpointType.getPropertyXMLDescriptions
   */
  @JvmStatic
  fun getPropertyXMLDescriptions(breakpoint: XBreakpoint<*>): List<String> =
    breakpoint.propertyXMLDescriptions

  @JvmStatic
  fun findType(id: @NonNls String): XBreakpointType<*, *>? =
    breakpointTypes().find { it.id == id }

  @ApiStatus.Internal
  @JvmStatic
  fun breakpointTypes(): StreamEx<XBreakpointType<*, *>> =
    StreamEx.of(XBreakpointType.EXTENSION_POINT_NAME.extensionList)

  @JvmStatic
  fun findSelectedBreakpoint(project: Project, editor: Editor): Pair<GutterIconRenderer?, XBreakpoint<*>?> {
    var offset = editor.caretModel.offset
    val editorDocument = editor.document

    val textLength = editorDocument.textLength
    if (offset > textLength) {
      offset = textLength
    }

    val breakpoint = findBreakpoint(project, editorDocument, offset)
    if (breakpoint != null) {
      return Pair.create(getBreakpointGutterIconRenderer(breakpoint), breakpoint)
    }

    val session = XDebuggerManager.getInstance(project).currentSession as XDebugSessionImpl?
    if (session != null) {
      val breakpoint = session.activeNonLineBreakpoint
      if (breakpoint != null) {
        val position = session.currentPosition
        if (position != null) {
          if (position.file == FileDocumentManager.getInstance().getFile(editorDocument) &&
              editorDocument.getLineNumber(offset) == position.line
          ) {
            return Pair.create((breakpoint as XBreakpointBase<*, *, *>).createGutterIconRenderer(), breakpoint)
          }
        }
      }
    }

    return Pair.create(null, null)
  }

  @ApiStatus.Internal
  fun getBreakpointGutterIconRenderer(breakpoint: Any): GutterIconRenderer? {
    if (breakpoint is XLineBreakpointImpl<*>) {
      val highlighter = breakpoint.highlighter
      if (highlighter != null) {
        return highlighter.getGutterIconRenderer()
      }
    }
    return null
  }

  private fun findBreakpoint(project: Project, document: Document, offset: Int): XBreakpoint<*>? {
    val breakpointManager = XDebuggerManager.getInstance(project).getBreakpointManager()
    val line = document.getLineNumber(offset)
    val file = FileDocumentManager.getInstance().getFile(document)
    if (file == null) {
      return null
    }
    for (type in XDebuggerUtil.getInstance().getLineBreakpointTypes()) {
      val breakpoint = breakpointManager.findBreakpointAtLine(type, file, line)
      if (breakpoint != null) {
        return breakpoint
      }
    }

    return null
  }

  @JvmStatic
  @ApiStatus.Internal
  fun subscribeOnBreakpointsChanges(project: Project, disposable: Disposable, onBreakpointChange: (XBreakpoint<*>) -> Unit) {
    project.getMessageBus().connect(disposable).subscribe(XBreakpointListener.TOPIC, object : XBreakpointListener<XBreakpoint<*>> {
      override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
        onBreakpointChange(breakpoint)
      }

      override fun breakpointChanged(breakpoint: XBreakpoint<*>) {
        onBreakpointChange(breakpoint)
      }

      override fun breakpointRemoved(breakpoint: XBreakpoint<*>) {
        onBreakpointChange(breakpoint)
      }
    })
  }

  @ApiStatus.Internal
  @JvmStatic
  fun getAllBreakpointItems(project: Project): List<BreakpointItem> {
    return XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project).getAllBreakpointItems()
  }

  /**
   * Toggle line breakpoint with editor support:
   * - unfolds folded block on the line
   * - if folded, checks if line breakpoints could be toggled inside folded text
   *
   */
  @Deprecated("use {@link #toggleLineBreakpoint(Project, XSourcePosition, boolean, Editor, boolean, boolean, boolean)}")
  @JvmStatic
  fun toggleLineBreakpoint(
    project: Project,
    position: XSourcePosition,
    editor: Editor?,
    temporary: Boolean,
    moveCaret: Boolean,
    canRemove: Boolean,
  ): Promise<XLineBreakpoint<*>?> =
    toggleLineBreakpoint(project, position, true, editor, temporary, moveCaret, canRemove)

  /**
   * Toggle line breakpoint with editor support:
   * - unfolds folded block on the line
   * - if folded, checks if line breakpoints could be toggled inside folded text
   */
  @JvmStatic
  fun toggleLineBreakpoint(
    project: Project,
    position: XSourcePosition,
    selectVariantByPositionColumn: Boolean,
    editor: Editor?,
    temporary: Boolean,
    moveCaret: Boolean,
    canRemove: Boolean,
  ): Promise<XLineBreakpoint<*>?> {
    val info = getAvailableLineBreakpointInfo(project, position, selectVariantByPositionColumn, editor)
    val typeWinner = info.first
    val lineWinner = info.second

    if (typeWinner.isEmpty()) {
      return rejectedPromise(RuntimeException("Cannot find appropriate type"))
    }

    val lineStart = position.line
    val winPosition = if (lineStart == lineWinner) position else XSourcePositionImpl.create(position.file, lineWinner)
    val res = XDebuggerUtilImpl.toggleAndReturnLineBreakpoint(
      project, typeWinner, winPosition, selectVariantByPositionColumn, temporary, editor, canRemove)

    if (editor != null && lineStart != lineWinner) {
      val offset = editor.document.getLineStartOffset(lineWinner)
      ExpandRegionAction.expandRegionAtOffset(editor, offset)
      if (moveCaret) {
        editor.caretModel.moveToOffset(offset)
      }
    }
    return res
  }

  @ApiStatus.Internal
  @JvmStatic
  fun <B : XBreakpoint<P>, P : XBreakpointProperties<*>, T : XBreakpointType<B, P>> createBreakpoint(
    type: T,
    state: BreakpointState,
    breakpointManager: XBreakpointManagerImpl,
  ): XBreakpointBase<B, P, *> {
    return if (type is XLineBreakpointType<*> && state is LineBreakpointState) {
      @Suppress("UNCHECKED_CAST")
      XLineBreakpointImpl<P>(type as XLineBreakpointType<P>, breakpointManager, createProperties(type, state), state) as XBreakpointBase<B, P, *>
    }
    else {
      XBreakpointBase<B, P, BreakpointState>(type, breakpointManager, createProperties(type, state), state)
    }
  }

  private fun <P : XBreakpointProperties<*>> createProperties(
    type: XBreakpointType<*, P>,
    state: BreakpointState,
  ): P? {
    val properties = type.createProperties()
    if (properties != null) {
      ComponentSerializationUtil.loadComponentState(properties as PersistentStateComponent<*>, state.propertiesElement)
    }
    return properties
  }

  @JvmStatic
  fun getAvailableLineBreakpointTypes(
    project: Project,
    linePosition: XSourcePosition,
    editor: Editor?,
  ): List<XLineBreakpointType<*>> =
    getAvailableLineBreakpointTypes(project, linePosition, false, editor)

  @JvmStatic
  fun getAvailableLineBreakpointTypes(
    project: Project,
    position: XSourcePosition,
    selectTypeByPositionColumn: Boolean,
    editor: Editor?,
  ): List<XLineBreakpointType<*>> =
    getAvailableLineBreakpointInfo(project, position, selectTypeByPositionColumn, editor).first

  private fun getAvailableLineBreakpointInfo(
    project: Project,
    position: XSourcePosition,
    selectTypeByPositionColumn: Boolean,
    editor: Editor?,
  ): Pair<List<XLineBreakpointType<*>>, Int> {
    val lineStart = position.line
    val file = position.file

    if (!file.isValid) {
      return Pair.create(emptyList(), -1)
    }

    // for folded text check each line and find out type with the biggest priority,
    // do it unless we were asked to select type strictly by caret position
    var linesEnd = lineStart
    if (editor != null && !selectTypeByPositionColumn) {
      val region = FoldingUtil.findFoldRegionStartingAtLine(editor, lineStart)
      if (region != null && !region.isExpanded) {
        linesEnd = region.document.getLineNumber(region.endOffset)
      }
    }

    val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
    val lineTypes = XDebuggerUtil.getInstance().lineBreakpointTypes
    val typeWinner = SmartList<XLineBreakpointType<*>>()
    var lineWinner = -1
    if (linesEnd != lineStart) { // folding mode
      for (line in lineStart..linesEnd) {
        var maxPriority = 0
        for (type in lineTypes) {
          maxPriority = max(maxPriority, type.priority)
          val breakpoint = breakpointManager.findBreakpointAtLine(type, file, line)
          if ((type.canPutAt(file, line, project) || breakpoint != null) &&
              (typeWinner.isEmpty() || type.priority > typeWinner[0].priority)
          ) {
            typeWinner.clear()
            typeWinner.add(type)
            lineWinner = line
          }
        }
        // already found max priority type - stop
        if (!typeWinner.isEmpty() && typeWinner[0].priority == maxPriority) {
          break
        }
      }
    }
    else {
      for (type in lineTypes) {
        val breakpoint = breakpointManager.findBreakpointAtLine(type, file, lineStart)
        if ((type.canPutAt(file, lineStart, project) || breakpoint != null)) {
          typeWinner.add(type)
          lineWinner = lineStart
        }
      }
      // First type is the most important one.
      typeWinner.sortByDescending { it.priority }
    }
    return Pair.create(typeWinner, lineWinner)
  }
}

val XBreakpoint<*>.shortText: @Nls String
  get() {
    @Suppress("UNCHECKED_CAST") val t = type as XBreakpointType<XBreakpoint<*>, *>
    return t.getShortText(this)
  }

val XBreakpoint<*>.displayText: @Nls String
  get() {
    @Suppress("UNCHECKED_CAST") val t = type as XBreakpointType<XBreakpoint<*>, *>
    return t.getDisplayText(this)
  }

val XBreakpoint<*>.generalDescription: @Nls String
  get() {
    @Suppress("UNCHECKED_CAST") val t = type as XBreakpointType<XBreakpoint<*>, *>
    return t.getGeneralDescription(this)
  }

val XBreakpoint<*>.propertyXMLDescriptions: List<@Nls String>
  get() {
    @Suppress("UNCHECKED_CAST") val t = type as XBreakpointType<XBreakpoint<*>, *>
    return t.getPropertyXMLDescriptions(this)
  }

val <P : XBreakpointProperties<*>> XLineBreakpoint<P>.highlightRange: TextRange?
  get() =
    type.getHighlightRange(this)
