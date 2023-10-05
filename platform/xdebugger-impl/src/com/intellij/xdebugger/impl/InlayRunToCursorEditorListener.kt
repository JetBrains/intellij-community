// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.daemon.impl.IntentionsUIImpl
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.PriorityQuestionAction
import com.intellij.codeInsight.hint.QuestionAction
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.impl.IdeaActionButtonLook
import com.intellij.openapi.actionSystem.impl.ToolbarUtils.createImmediatelyUpdatedToolbar
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.IterationState
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.ColorUtil
import com.intellij.ui.HintHint
import com.intellij.ui.JBColor
import com.intellij.ui.LightweightHint
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.util.PlatformUtils
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.breakpoints.XBreakpointUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import java.awt.*
import java.lang.ref.WeakReference
import javax.swing.*

private const val NEGATIVE_INLAY_PANEL_SHIFT = -6 // it is needed to fit into 2-space tabulation
private const val MINIMAL_TEXT_OFFSET = 16
private const val ACTION_BUTTON_SIZE = 22
private const val ACTION_BUTTON_GAP = 2

private val inlayToolbarStrokeColor = Color.WHITE

class InlayRunToCursorEditorListener(private val project: Project, private val coroutineScope: CoroutineScope) : EditorMouseMotionListener, EditorMouseListener {
  companion object {
    @JvmStatic
    val isInlayRunToCursorEnabled: Boolean get() =
      Registry.`is`("debugger.inlayRunToCursor") ||
      AdvancedSettings.getBoolean("debugger.inlay.run.to.cursor") && (PlatformUtils.isIntelliJ())
  }

  private var currentJob: Job? = null

  private var currentHint = WeakReference<RunToCursorHint?>(null)
  private var currentEditor = WeakReference<Editor?>(null)
  private var currentLineNumber = -1

  fun installScrollListeners(debuggerManagerImpl: XDebuggerManagerImpl) {
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor
        editor.getScrollingModel().addVisibleAreaListener(VisibleAreaListener {
          showInlayRunToCursorIfNeeded(editor, null)
        })
      }
    }, debuggerManagerImpl)
  }

  override fun mouseMoved(e: EditorMouseEvent) {
    showInlayRunToCursorIfNeeded(e.editor, e)
  }

  fun reshowInlayRunToCursor(editor: Editor) {
    currentEditor = WeakReference(null)
    currentLineNumber = -1
    showInlayRunToCursorIfNeeded(editor, null)
  }

  private fun showInlayRunToCursorIfNeeded(editor: Editor, e: EditorMouseEvent?) {
    val shouldHideCurrentHint = tryToScheduleInlayRunToCursor(editor, e)
    if (shouldHideCurrentHint) {
      val hint = currentHint.get()
      hint?.hide()
    }
  }

  private fun tryToScheduleInlayRunToCursor(editor: Editor, e: EditorMouseEvent?): Boolean {
    if (editor.project != project) return true

    if (!isInlayRunToCursorEnabled) {
      IntentionsUIImpl.DISABLE_INTENTION_BULB[project] = false
      return true
    }

    if (editor.getEditorKind() != EditorKind.MAIN_EDITOR) {
      return true
    }
    val session = XDebuggerManager.getInstance(project).getCurrentSession() as XDebugSessionImpl?
    if (session == null || !session.isPaused || session.isReadOnly) {
      IntentionsUIImpl.DISABLE_INTENTION_BULB[project] = false
      return true
    }
    IntentionsUIImpl.DISABLE_INTENTION_BULB[project] = true
    val lineNumber = if (e != null) XDebuggerManagerImpl.getLineNumber(e) else lineFromCurrentMouse(editor)
    if (lineNumber < 0) {
      return true
    }
    if (currentEditor.get() === editor && currentLineNumber == lineNumber) {
      return false
    }
    currentEditor = WeakReference(editor)
    currentLineNumber = lineNumber
    scheduleInlayRunToCursor(editor, lineNumber, session)
    return true
  }

  private fun lineFromCurrentMouse(editor: Editor): Int {
    val location = MouseInfo.getPointerInfo().location
    SwingUtilities.convertPointFromScreen(location, editor.getContentComponent())

    val logicalPosition: LogicalPosition = editor.xyToLogicalPosition(location)
    if (logicalPosition.line >= editor.document.getLineCount()) {
      return -1
    }

    return logicalPosition.line
  }

  private fun scheduleInlayRunToCursor(editor: Editor, lineNumber: Int, session: XDebugSessionImpl) {
    currentJob?.cancel()
    if (editor !is EditorImpl) return
    val scope = coroutineScope.childScope()
    Disposer.register(editor.disposable, Disposable {
      scope.cancel()
    })
    scope.launch(Dispatchers.EDT) {
      currentJob = coroutineContext.job
      scheduleInlayRunToCursorAsync(editor, lineNumber, session)
      currentJob = null
    }
  }

  private fun getFirstNonSpacePos(editor: Editor, lineNumber: Int): Point? {
    val document = editor.getDocument()
    if (lineNumber >= document.lineCount) return null

    var firstNonSpaceSymbol = document.getLineStartOffset(lineNumber)
    val charsSequence = document.charsSequence
    while (true) {
      if (firstNonSpaceSymbol >= charsSequence.length) {
        return null //end of file
      }
      val c = charsSequence[firstNonSpaceSymbol]
      if (c == '\n') {
        return null // empty line
      }
      if (!Character.isWhitespace(c)) {
        break
      }
      firstNonSpaceSymbol++
    }
    return editor.offsetToXY(firstNonSpaceSymbol)
  }

  @RequiresEdt
  private suspend fun scheduleInlayRunToCursorAsync(editor: Editor, lineNumber: Int, session: XDebugSessionImpl) {
    val firstNonSpacePos = getFirstNonSpacePos(editor, lineNumber) ?: return
    if (firstNonSpacePos.x < JBUI.scale(MINIMAL_TEXT_OFFSET)) {
      return
    }
    val lineY = editor.logicalPositionToXY(LogicalPosition(lineNumber, 0)).y

    val group = DefaultActionGroup()
    val pausePosition = session.currentPosition
    if (pausePosition != null && pausePosition.getFile() == editor.virtualFile && pausePosition.getLine() == lineNumber) {
      group.add(ActionManager.getInstance().getAction(XDebuggerActions.RESUME))
      showHint(editor, lineNumber, firstNonSpacePos, group, lineY)
    }
    else {
      val hoverPosition = XSourcePositionImpl.create(FileDocumentManager.getInstance().getFile(editor.getDocument()), lineNumber) ?: return
      val hasGeneralBreakpoint = readAction {
        val types = XBreakpointUtil.getAvailableLineBreakpointTypes(project, hoverPosition, editor)
        types.any { it.enabledIcon === AllIcons.Debugger.Db_set_breakpoint }
      }

      if (hasGeneralBreakpoint) {
        group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_RUN_TO_CURSOR))
      }

      val extraActions = ActionManager.getInstance().getAction("XDebugger.RunToCursorInlayExtraActions") as DefaultActionGroup
      group.addAll(extraActions)

      showHint(editor, lineNumber, firstNonSpacePos, group, lineY)
    }
  }

  @RequiresEdt
  private suspend fun showHint(editor: Editor, lineNumber: Int, firstNonSpacePos: Point, group: DefaultActionGroup, lineY: Int) {
    val rootPane = editor.getComponent().rootPane
    if (rootPane == null) {
      return
    }
    val caretLine = editor.getCaretModel().logicalPosition.line
    val minimalOffsetBeforeText = MINIMAL_TEXT_OFFSET + (ACTION_BUTTON_GAP * 2 + ACTION_BUTTON_SIZE) * group.childrenCount
    if (editor.getSettings().isShowIntentionBulb() && caretLine == lineNumber && firstNonSpacePos.x >= JBUI.scale(minimalOffsetBeforeText)) {
      group.add(ActionManager.getInstance().getAction(IdeActions.ACTION_SHOW_INTENTION_ACTIONS))
    }
    if (group.childrenCount == 0) return

    val editorContentComponent = editor.contentComponent
    val position = SwingUtilities.convertPoint(
      editorContentComponent,
      Point(0, lineY + (editor.lineHeight - JBUI.scale(ACTION_BUTTON_SIZE))/2),
      rootPane.layeredPane
    )

    // so  some floating tool window is likely overlay our editor
    if (isOutOfVisibleEditor(rootPane, position.x, position.y, JBUI.scale(ACTION_BUTTON_SIZE), editorContentComponent)) return

    position.x += JBUI.scale(NEGATIVE_INLAY_PANEL_SHIFT)

    val initIsCompleted = Mutex(true)
    val toolbarImpl = createImmediatelyUpdatedToolbar(group, ActionPlaces.EDITOR_HINT, editor.getComponent(), true) {
      initIsCompleted.unlock()
    } as ActionToolbarImpl
    toolbarImpl.setLayoutPolicy(ActionToolbar.NOWRAP_LAYOUT_POLICY)
    toolbarImpl.setActionButtonBorder(JBUI.Borders.empty(0, ACTION_BUTTON_GAP))
    toolbarImpl.setNeedCheckHoverOnLayout(true)
    toolbarImpl.setBorder(null)
    toolbarImpl.setOpaque(false)
    val hoverColor = editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT).backgroundColor
    val effectiveHoverColor = getEditorBackgroundColorForTheLineStart(editor, lineNumber)?.let {
      ColorUtil.alphaBlending(ColorUtil.withAlpha(hoverColor, HintRenderer.BACKGROUND_ALPHA.toDouble()), it)
    } ?: hoverColor
    toolbarImpl.setCustomButtonLook(InlayPopupButtonLook(effectiveHoverColor))
    toolbarImpl.setAdditionalDataProvider { dataId: String? ->
      if (XDebuggerUtilImpl.LINE_NUMBER.`is`(dataId)) {
        return@setAdditionalDataProvider lineNumber
      }
      null
    }
    val justPanel: JPanel = NonOpaquePanel()
    justPanel.preferredSize = JBDimension((2 * ACTION_BUTTON_GAP + ACTION_BUTTON_SIZE) * group.childrenCount, ACTION_BUTTON_SIZE)
    justPanel.add(toolbarImpl.component)
    val hint = RunToCursorHint(justPanel, this)
    val questionAction: QuestionAction = object : PriorityQuestionAction {
      override fun execute(): Boolean {
        return true
      }

      override fun getPriority(): Int {
        return PriorityQuestionAction.INTENTION_BULB_PRIORITY
      }
    }
    val offset = editor.getCaretModel().offset

    initIsCompleted.lock()

    val flags = HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or
      HintManager.HIDE_IF_OUT_OF_EDITOR or HintManager.DONT_CONSUME_ESCAPE

    HintManagerImpl.getInstanceImpl().showQuestionHint(editor, position, offset, offset, hint, flags, questionAction, HintManager.RIGHT)
  }

  private fun isOutOfVisibleEditor(rootPane: JRootPane, x: Int, y: Int, h: Int, editorContentComponent: JComponent): Boolean {
    return SwingUtilities.getDeepestComponentAt(rootPane.layeredPane, x, y) != editorContentComponent ||
          SwingUtilities.getDeepestComponentAt(rootPane.layeredPane, x, y + h) != editorContentComponent
  }

  private fun getEditorBackgroundColorForTheLineStart(editor: Editor, lineNumber: Int): Color? {
    val lineStartOffset = editor.getDocument().getLineStartOffset(lineNumber)
    val editorEx = editor as? EditorEx ?: return null
    val iterationState = IterationState(editorEx, lineStartOffset, lineStartOffset + 1, null, false, false, true, false)
    val mergedAttributes = iterationState.mergedAttributes
    return mergedAttributes.backgroundColor
  }

  private class RunToCursorHint(component: JComponent, private val listener: InlayRunToCursorEditorListener) : LightweightHint(component) {
    override fun show(parentComponent: JComponent, x: Int, y: Int, focusBackComponent: JComponent, hintHint: HintHint) {
      listener.currentHint = WeakReference(this)
      super.show(parentComponent, x, y, focusBackComponent, HintHint(parentComponent, Point(x, y)))
    }

    override fun hide(ok: Boolean) {
      listener.currentHint.clear()
      super.hide(ok)
    }
  }
}

private class InlayPopupButtonLook(val effectiveHoverColor: Color) : IdeaActionButtonLook() {
  val useStrokeVariants = ColorUtil.getColorDistance(effectiveHoverColor, JBColor.PanelBackground) > 50
                          && ColorUtil.getColorDistance(effectiveHoverColor, inlayToolbarStrokeColor) > 250

  override fun getStateBackground(component: JComponent?, state: Int): Color? {
    if (state == ActionButtonComponent.POPPED) {
      return effectiveHoverColor
    }
    return super.getStateBackground(component, state)
  }

  override fun paintIcon(g: Graphics, actionButton: ActionButtonComponent, icon: Icon, x: Int, y: Int) {
    val resultIcon = if (useStrokeVariants) toStrokeIcon(icon, inlayToolbarStrokeColor) else icon
    super.paintIcon(g, actionButton, resultIcon, x, y)
  }

  override fun paintLookBorder(g: Graphics, rect: Rectangle, color: Color) {
    //do nothing
  }
}
