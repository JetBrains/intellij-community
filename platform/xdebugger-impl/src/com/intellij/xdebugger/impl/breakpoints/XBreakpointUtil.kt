// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.codeInsight.folding.impl.FoldingUtil
import com.intellij.codeInsight.folding.impl.actions.ExpandRegionAction
import com.intellij.configurationStore.ComponentSerializationUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.SmartList
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.*
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.breakpoints.ui.BreakpointItem
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import one.util.streamex.StreamEx
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.asPromise
import java.util.concurrent.CompletableFuture
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

  @ApiStatus.Obsolete
  @JvmStatic
  fun findSelectedBreakpoint(project: Project, editor: Editor): Pair<GutterIconRenderer?, XBreakpoint<*>?> {
    val pair = findSelectedBreakpointProxy(project, editor)
    val (renderer, breakpoint) = pair
    if (breakpoint is XBreakpointProxy.Monolith) {
      return Pair.create(renderer, breakpoint.breakpoint)
    }
    return Pair.create(null, null)
  }

  @ApiStatus.Internal
  @JvmStatic
  fun findSelectedBreakpointProxy(project: Project, editor: Editor): Pair<GutterIconRenderer?, XBreakpointProxy?> {
    var offset = editor.caretModel.offset
    val editorDocument = editor.document

    val textLength = editorDocument.textLength
    if (offset > textLength) {
      offset = textLength
    }

    val breakpoint = findBreakpoint(project, editorDocument, offset)
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

  private fun findBreakpoint(project: Project, document: Document, offset: Int): XLineBreakpointProxy? {
    val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project)
    val line = document.getLineNumber(offset)
    val file = FileDocumentManager.getInstance().getFile(document) ?: return null
    for (type in breakpointManager.getLineBreakpointTypes()) {
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
    editor: Editor,
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
    editor: Editor,
    temporary: Boolean,
    moveCaret: Boolean,
    canRemove: Boolean,
  ): Promise<XLineBreakpoint<*>?> {
    return toggleLineBreakpointProxy(project, position, selectVariantByPositionColumn, editor, temporary, moveCaret, canRemove).asPromise()
      .then { proxy ->
        (proxy as? XLineBreakpointProxy.Monolith)?.breakpoint as? XLineBreakpoint<*>
      }
  }

  /**
   * Toggle line breakpoint with editor support:
   * - unfolds folded block on the line
   * - if folded, checks if line breakpoints could be toggled inside folded text
   */
  @JvmStatic
  @VisibleForTesting
  @ApiStatus.Internal
  fun toggleLineBreakpointProxy(
    project: Project,
    position: XSourcePosition,
    selectVariantByPositionColumn: Boolean,
    editor: Editor,
    temporary: Boolean,
    moveCaret: Boolean,
    canRemove: Boolean,
    isConditional: Boolean = false,
    condition: String? = null,
  ): CompletableFuture<XLineBreakpointProxy?> {
    // TODO: Replace with `coroutineScope.future` after IJPL-184112 is fixed
    val future = CompletableFuture<XLineBreakpointProxy?>()
    project.service<XBreakpointUtilProjectCoroutineScope>().cs.launch(Dispatchers.EDT) {
      try {
        val (typeWinner, lineWinner) = getAvailableLineBreakpointInfoProxy(project, position, selectVariantByPositionColumn, editor)
        if (typeWinner.isEmpty()) {
          future.completeExceptionally(RuntimeException("Cannot find appropriate type"))
        }
        val lineStart = position.line
        val winPosition = if (lineStart == lineWinner) position else XSourcePositionImpl.create(position.file, lineWinner)

        val res = XDebuggerUtilImpl.toggleAndReturnLineBreakpointProxy(
          project, typeWinner, winPosition, selectVariantByPositionColumn, temporary, editor, canRemove, isConditional, condition)
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
  ): List<XLineBreakpointType<*>> {
    return getAvailableLineBreakpointTypesInfo(project, position, selectTypeByPositionColumn, editor).first
  }

  private fun getAvailableLineBreakpointTypesInfo(
    project: Project,
    position: XSourcePosition,
    selectTypeByPositionColumn: Boolean,
    editor: Editor?,
  ): Pair<List<XLineBreakpointType<*>>, Int> {
    val breakpointManager = XDebuggerManager.getInstance(project).breakpointManager
    return getAvailableLineBreakpointInfo(position, selectTypeByPositionColumn, editor,
                                          XDebuggerUtil.getInstance().lineBreakpointTypes.toList(),
                                          { type, line -> breakpointManager.findBreakpointAtLine(type, position.file, line) },
                                          { type -> type.priority },
                                          { callback -> callback() },
                                          { type, line -> type.canPutAt(position.file, line, project) }
    )
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

  private inline fun <T, B> getAvailableLineBreakpointInfo(
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

@Service(Service.Level.PROJECT)
private class XBreakpointUtilProjectCoroutineScope(val cs: CoroutineScope)