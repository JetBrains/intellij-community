// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.codeInsight.actions.VcsFacade
import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.codeInsight.daemon.impl.IntentionsUIImpl
import com.intellij.codeInsight.hint.ClientHintManager
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.ide.DataManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.*
import com.intellij.openapi.actionSystem.impl.ToolbarUtils.createImmediatelyUpdatedToolbar
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.LogicalPosition
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.editor.event.EditorMouseMotionListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterComponentEx
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.editor.ex.util.EditorUtil
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.impl.view.IterationState
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.PsiManager
import com.intellij.ui.ColorUtil
import com.intellij.ui.HintHint
import com.intellij.ui.JBColor
import com.intellij.ui.LightweightHint
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.icons.toStrokeIcon
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.annotations.ApiStatus
import java.awt.*
import java.lang.ref.WeakReference
import javax.swing.*
import kotlin.math.min

private const val ACTION_BUTTON_GAP = 2
private const val SHIFT_FOR_VCS_MARKER = 10

class InlayRunToCursorEditorListener(private val project: Project, private val coroutineScope: CoroutineScope) : EditorMouseMotionListener, EditorMouseListener {
  companion object {
    @ApiStatus.Internal
    const val ACTION_BUTTON_SIZE: Int = 22

    @ApiStatus.Internal
    @JvmStatic
    // it is necessary to fit into 2-space tabulation in no line-marker case
    fun negativeInlayPanelShift(hasLineMarker: Boolean): Int = if (hasLineMarker) -2 else -8
    private fun minimalTextOffset(hasLineMarker: Boolean) = if (hasLineMarker) 20 else 16

    @JvmStatic
    val isInlayRunToCursorEnabled: Boolean get() = AdvancedSettings.getBoolean("debugger.inlay.run.to.cursor")
  }

  private var currentJob: Job? = null

  private var currentHint = WeakReference<RunToCursorHint?>(null)
  private var currentEditor = WeakReference<Editor?>(null)
  private var currentLineNumber = -1

  override fun mouseMoved(e: EditorMouseEvent) {
    showInlayRunToCursorIfNeeded(e.editor, e)
  }

  @RequiresEdt
  fun reshowInlayRunToCursor(editor: Editor) {
    currentEditor.clear()
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
      IntentionsUIImpl.SHOW_INTENTION_BULB_ON_ANOTHER_LINE[project] = 0
      return true
    }

    if (editor.getEditorKind() != EditorKind.MAIN_EDITOR && e != null) {
      return true
    }
    val runToCursorService = project.service<RunToCursorService>()
    if (!runToCursorService.shouldShowInlay()) {
      IntentionsUIImpl.SHOW_INTENTION_BULB_ON_ANOTHER_LINE[project] = 0
      return true
    }
    // It can be improved to dynamically detect the necessary offset
    IntentionsUIImpl.SHOW_INTENTION_BULB_ON_ANOTHER_LINE[project] = ACTION_BUTTON_SIZE + ACTION_BUTTON_GAP
    val (lineNumber, x) = if (e != null) {
      XDebuggerManagerImpl.getLineNumber(e) to (if (e.area == EditorMouseEventArea.EDITING_AREA) e.mouseEvent.x else 0)
    } else {
      val location = locationOnEditor(editor) ?: return false
      lineFromCurrentMouse(editor, location) to location.x
    }
    if (lineNumber < 0) {
      return true
    }
    if (x - editor.scrollingModel.horizontalScrollOffset > showInlayPopupWidth(editor)) {
      currentEditor.clear()
      currentLineNumber = -1
      return true
    }
    if (currentEditor.get() === editor && currentLineNumber == lineNumber) {
      return false
    }
    currentEditor = WeakReference(editor)
    currentLineNumber = lineNumber

    scheduleInlayRunToCursor(editor, lineNumber)
    return true
  }

  private fun showInlayPopupWidth(editor: Editor) = EditorUtil.getSpaceWidth(Font.PLAIN, editor) *
                                                    Registry.intValue("debugger.inlayRunToCursor.hover.area", 4)

  private fun locationOnEditor(editor: Editor): Point? {
    val location: Point = MouseInfo.getPointerInfo().location ?: return null
    SwingUtilities.convertPointFromScreen(location, editor.getContentComponent())

    val editorGutterComponentEx = editor.gutter as? EditorGutterComponentEx ?: return null
    if (!editor.getContentComponent().bounds.contains(location) && !editorGutterComponentEx.bounds.contains(location)) {
      return null
    }
    return location
  }

  private fun lineFromCurrentMouse(editor: Editor, location: Point): Int {
    val logicalPosition: LogicalPosition = editor.xyToLogicalPosition(location)
    if (logicalPosition.line >= editor.document.getLineCount()) {
      return -1
    }

    return logicalPosition.line
  }

  @RequiresEdt
  private fun scheduleInlayRunToCursor(editor: Editor, lineNumber: Int) {
    currentJob?.cancel()
    if (editor !is EditorImpl) return
    coroutineScope.launch(Dispatchers.EDT, CoroutineStart.UNDISPATCHED) {
      val job = coroutineContext.job
      job.cancelOnDispose(editor.disposable)
      currentJob = job
      scheduleInlayRunToCursorAsync(editor, lineNumber)
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
    return editor.offsetToXY(firstNonSpaceSymbol).let {
      Point(it.x - editor.scrollingModel.horizontalScrollOffset, it.y)
    }
  }

  @RequiresEdt
  private suspend fun scheduleInlayRunToCursorAsync(editor: Editor, lineNumber: Int) {
    val runToCursorService = project.service<RunToCursorService>()
    val firstNonSpacePos = getFirstNonSpacePos(editor, lineNumber) ?: return
    val lineY = editor.logicalPositionToXY(LogicalPosition(lineNumber, 0)).y
    val actionManager = serviceAsync<ActionManager>()
    val virtualFile = editor.virtualFile ?: return
    val hasVcsLineMarker = hasVcsLineMarker(editor, lineNumber)
    val isAtExecution = readAction {
      runToCursorService.isAtExecution(virtualFile, lineNumber)
    }
    if (isAtExecution) {
      showHint(editor, lineNumber, firstNonSpacePos, listOf(actionManager.getAction(XDebuggerActions.RESUME)), lineY, hasVcsLineMarker)
    }
    else {
      val actions = mutableListOf<AnAction>()
      if (runToCursorService.canRunToCursor(editor, lineNumber)) {
        actions.add(actionManager.getAction(IdeActions.ACTION_RUN_TO_CURSOR))
      }

      // TODO: RIDER-127831, Move RiderJumpToStatementAction to frontend, for fast update here
      if (!XDebugSessionProxy.useFeProxy()) {
        val extraActions = Utils.expandActionGroupSuspend(
                                        actionManager.getAction("XDebugger.RunToCursorInlayExtraActions") as DefaultActionGroup,
                                        PresentationFactory(),
                                        DataManager.getInstance().getDataContext(editor.contentComponent),
                                        ActionPlaces.EDITOR_HINT,
                                        ActionUiKind.NONE,
                                        false)
        actions.addAll(extraActions)
      }
      showHint(editor, lineNumber, firstNonSpacePos, actions, lineY, hasVcsLineMarker)
    }
  }

  private fun hasVcsLineMarker(editor: Editor, lineNumber: Int): Boolean {
    val document = editor.getDocument()

    val psiFile = FileDocumentManager.getInstance().getFile(document)?.let { virtualFile ->
      PsiManager.getInstance(project).findFile(virtualFile)
    } ?: return false

    val changedRangesInfo = VcsFacade.getInstance().getChangedRangesInfo(psiFile) ?: return false

    val lineStartOffset = document.getLineStartOffset(lineNumber)
    return changedRangesInfo.allChangedRanges.any {
      it.contains(lineStartOffset)
    }
  }

  @RequiresEdt
  private suspend fun showHint(editor: Editor, lineNumber: Int, firstNonSpacePos: Point, actions: List<AnAction>, lineY: Int, hasVcsLineMarker: Boolean) {
    if (actions.isEmpty()) return

    val rootPane = editor.getComponent().rootPane
    if (rootPane == null) {
      return
    }

    val editorGutterComponentEx = editor.gutter as? EditorGutterComponentEx ?: return

    val needShowOnGutter = firstNonSpacePos.x < JBUI.scale(minimalTextOffset(hasVcsLineMarker) + ACTION_BUTTON_SIZE * (actions.size - 1))

    var xPosition = JBUI.scale(negativeInlayPanelShift(hasVcsLineMarker))
    var actionsToShow = actions

    if (needShowOnGutter) {
      val breakpointInsertionZoneRightOffset = if (EditorSettingsExternalizable.getInstance().isLineNumbersShown && UISettings.getInstance().showBreakpointsOverLineNumbers)
        editorGutterComponentEx.lineNumberAreaOffset + editorGutterComponentEx.lineNumberAreaWidth
      else
        editorGutterComponentEx.whitespaceSeparatorOffset
      val numberOfActionsToShow = if (hasVcsLineMarker) {
        1
      }
      else {
        val freeSpace = editorGutterComponentEx.width +
                        JBUI.scale(negativeInlayPanelShift(hasVcsLineMarker)) - breakpointInsertionZoneRightOffset
        min(freeSpace / JBUI.scale(ACTION_BUTTON_SIZE), actions.size)
      }

      if (numberOfActionsToShow <= 0) {
        return
      }
      xPosition -= JBUI.scale(ACTION_BUTTON_SIZE) * numberOfActionsToShow
      if (hasVcsLineMarker) xPosition -= JBUI.scale(SHIFT_FOR_VCS_MARKER)
      actionsToShow = actions.take(numberOfActionsToShow)
    }

    val group = DefaultActionGroup(actionsToShow)

    if (needShowOnGutter && isGutterComponentOverlapped(editor, editorGutterComponentEx, xPosition, lineY, lineNumber, actionsToShow.size)) {
      return
    }

    val editorContentComponent = editor.contentComponent
    val position = SwingUtilities.convertPoint(
      editorContentComponent,
      Point(xPosition + editor.scrollingModel.horizontalScrollOffset, lineY + (editor.lineHeight - JBUI.scale(ACTION_BUTTON_SIZE)) / 2),
      rootPane.layeredPane
    )

    // Make sure that the previous popup before will be hidden before we check for overlay
    currentHint.get()?.hide()

    // Check that there is no some floating tool window overlaying our editor
    if (isOutOfVisibleEditor(rootPane, position.x, position.y, JBUI.scale(ACTION_BUTTON_SIZE), editorGutterComponentEx)) return

    val initIsCompleted = Mutex(true)
    val targetComponent = ToolbarUtils.createTargetComponent(editor) { sink ->
      sink[XDebuggerUtilImpl.LINE_NUMBER] = lineNumber
    }
    val toolbarImpl = createImmediatelyUpdatedToolbar(group, ActionPlaces.EDITOR_HINT, targetComponent, true) {
      initIsCompleted.unlock()
    } as ActionToolbarImpl
    toolbarImpl.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY)
    toolbarImpl.setActionButtonBorder(JBUI.Borders.empty(0, ACTION_BUTTON_GAP))
    toolbarImpl.setNeedCheckHoverOnLayout(true)
    toolbarImpl.setBorder(null)
    toolbarImpl.setOpaque(false)
    toolbarImpl.setCustomButtonLook(InlayPopupButtonLook {
      ApplicationManager.getApplication().runReadAction(Computable {
        calculateEffectiveHoverColorAndStroke(needShowOnGutter, editor, lineNumber)
      })
    })
    val justPanel: JPanel = NonOpaquePanel()
    justPanel.preferredSize = JBDimension((2 * ACTION_BUTTON_GAP + ACTION_BUTTON_SIZE) * group.childrenCount, ACTION_BUTTON_SIZE)
    justPanel.add(toolbarImpl.component)
    val hint = RunToCursorHint(justPanel, this)
    initIsCompleted.lock()

    val clientHintManager = ClientHintManager.getCurrentInstance()

    val flags = HintManager.HIDE_BY_ANY_KEY or HintManager.HIDE_BY_TEXT_CHANGE or HintManager.UPDATE_BY_SCROLLING or
      HintManager.HIDE_IF_OUT_OF_EDITOR or HintManager.DONT_CONSUME_ESCAPE

    val hintInfo = HintManagerImpl.createHintHint(editor, position, hint, HintManager.RIGHT)
    clientHintManager.showEditorHint(hint, editor, hintInfo, position, flags, 0, true) { }
  }

  private fun isGutterComponentOverlapped(editor: Editor, editorGutterComponentEx: EditorGutterComponentEx, xPosition: Int, lineY: Int, lineNumber: Int, actionsToShowNumber: Int): Boolean {
    val visualLine = editor.logicalToVisualPosition(LogicalPosition(lineNumber, 0)).line
    val renderersAndRectangles = editorGutterComponentEx.getGutterRenderersAndRectangles(visualLine)

    val xStart = editorGutterComponentEx.width + xPosition
    val toolbarWidth = JBUI.scale(ACTION_BUTTON_SIZE) * actionsToShowNumber
    val toolbarRectangle = Rectangle(xStart, lineY, toolbarWidth, JBUI.scale(ACTION_BUTTON_SIZE))
    for (rectangle: Rectangle in renderersAndRectangles.map { it.second }) {
      if (rectangle.intersects(toolbarRectangle)) {
        return true
      }
    }

    val foldingAnchor = editorGutterComponentEx.findFoldingAnchorAt(editorGutterComponentEx.foldingAreaOffset + 1, lineY + 1)
    if (foldingAnchor != null && foldingAnchor.document.getLineNumber(foldingAnchor.startOffset) == lineNumber) {
      return true
    }
    return false
  }

  private fun calculateEffectiveHoverColorAndStroke(needShowOnGutter: Boolean, editor: Editor, lineNumber: Int): Pair<Color, Color?> {
    val hoverColor: Color = editor.colorsScheme.getAttributes(DefaultLanguageHighlighterColors.INLINE_PARAMETER_HINT).backgroundColor
                            ?: JBColor.PanelBackground
    return if (needShowOnGutter) {
      hoverColor to null
    }
    else {
      val textAttributesForLineStart = getEditorTextAttributesForTheLineStart(editor, lineNumber)
      val backgroundColor = textAttributesForLineStart?.backgroundColor
      if (backgroundColor != null) {
        val resultBackground = ColorUtil.alphaBlending(
          ColorUtil.withAlpha(hoverColor, HintRenderer.BACKGROUND_ALPHA.toDouble()), backgroundColor)
        resultBackground to textAttributesForLineStart.foregroundColor
      }
      else {
        hoverColor to null
      }
    }
  }

  private fun isOutOfVisibleEditor(rootPane: JRootPane, x: Int, y: Int, h: Int, editorContentComponent: JComponent): Boolean {
    return SwingUtilities.getDeepestComponentAt(rootPane.layeredPane, x, y) != editorContentComponent ||
          SwingUtilities.getDeepestComponentAt(rootPane.layeredPane, x, y + h) != editorContentComponent
  }

  private fun getEditorTextAttributesForTheLineStart(editor: Editor, lineNumber: Int): TextAttributes? {
    val lineStartOffset = editor.getDocument().getLineStartOffset(lineNumber)
    val editorEx = editor as? EditorEx ?: return null
    val iterationState = IterationState(editorEx, lineStartOffset, lineStartOffset + 1, null, false, false, true, false)
    return iterationState.mergedAttributes
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

private class InlayPopupButtonLook(val effectiveHoverColorAndStroke: () -> Pair<Color, Color?>) : IdeaActionButtonLook() {
  override fun getStateBackground(component: JComponent?, state: Int): Color? {
    if (state == ActionButtonComponent.POPPED) {
      return effectiveHoverColorAndStroke().first
    }
    return super.getStateBackground(component, state)
  }

  override fun paintIcon(g: Graphics, actionButton: ActionButtonComponent, icon: Icon, x: Int, y: Int) {
    val (effectiveHoverColor, strokeColor) = effectiveHoverColorAndStroke()
    val useStrokeVariants = strokeColor != null &&
                            ColorUtil.getColorDistance(effectiveHoverColor, JBColor.PanelBackground) > 50
                            && ColorUtil.getColorDistance(effectiveHoverColor, strokeColor) > 250
    val resultIcon = if (useStrokeVariants) toStrokeIcon(icon, strokeColor!!) else icon
    super.paintIcon(g, actionButton, resultIcon, x, y)
  }

  override fun paintLookBorder(g: Graphics, rect: Rectangle, color: Color) {
    //do nothing
  }
}
