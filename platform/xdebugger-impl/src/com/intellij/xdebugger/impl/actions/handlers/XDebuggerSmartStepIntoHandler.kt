// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.actions.handlers

import com.intellij.codeInsight.highlighting.HighlightManager
import com.intellij.codeInsight.highlighting.HighlightManagerImpl
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.HintManagerImpl
import com.intellij.codeInsight.hint.HintUtil
import com.intellij.codeInsight.unwrap.ScopeHighlighter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.AppMode
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.ex.util.EditorActionAvailabilityHint
import com.intellij.openapi.editor.ex.util.addActionAvailabilityHint
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx.Companion.getInstanceEx
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.Hint
import com.intellij.ui.LightweightHint
import com.intellij.ui.ListActions
import com.intellij.ui.components.JBList
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ObjectUtils
import com.intellij.util.SmartList
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.actions.XDebuggerSuspendedActionHandler
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import com.intellij.xdebugger.stepping.XSmartStepIntoVariant
import com.intellij.xdebugger.ui.DebuggerColors
import fleet.util.indexOfOrNull
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import java.awt.Point
import java.awt.event.KeyEvent
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import kotlin.math.abs

@ApiStatus.Internal
open class XDebuggerSmartStepIntoHandler : XDebuggerSuspendedActionHandler() {
  override fun isEnabled(session: XDebugSession, dataContext: DataContext): Boolean {
    return super.isEnabled(session, dataContext) && session.getDebugProcess().smartStepIntoHandler != null
  }

  override fun perform(session: XDebugSession, dataContext: DataContext) {
    val handler = session.getDebugProcess().smartStepIntoHandler
    val position = session.getTopFramePosition()
    if (position != null && handler != null) {
      val editor = FileEditorManager.getInstance(session.getProject()).getSelectedEditor(position.getFile())
      if (editor is TextEditor) {
        doSmartStepInto(handler, position, session, editor.getEditor())
        return
      }
    }
    session.stepInto()
  }

  private fun <V : XSmartStepIntoVariant> doSmartStepInto(
    handler: XSmartStepIntoHandler<V>,
    position: XSourcePosition,
    session: XDebugSession,
    editor: Editor,
  ) {
    val stepData = editor.getUserData(SMART_STEP_INPLACE_DATA)
    if (stepData != null) {
      stepData.stepIntoCurrent()
    }
    else {
      computeVariants(handler, position)
        .onSuccess { variants ->
          UIUtil.invokeLaterIfNeeded {
            if (!handleSimpleCases(handler, variants, session)) {
              choose(handler, variants, position, session, editor)
            }
          }
        }
        .onError { UIUtil.invokeLaterIfNeeded { session.stepInto() } }
    }
  }

  protected open fun <V : XSmartStepIntoVariant> computeVariants(
    handler: XSmartStepIntoHandler<V>,
    position: XSourcePosition,
  ): Promise<List<V>> {
    return handler.computeSmartStepVariantsAsync(position)
  }

  protected open fun <V : XSmartStepIntoVariant> handleSimpleCases(
    handler: XSmartStepIntoHandler<V>,
    variants: List<V>,
    session: XDebugSession,
  ): Boolean {
    if (variants.isEmpty()) {
      handler.stepIntoEmpty(session)
      return true
    }
    else if (variants.size == 1) {
      session.smartStepInto(handler, variants[0])
      return true
    }
    return false
  }
}

@ApiStatus.Internal
class SmartStepData<V : XSmartStepIntoVariant>(
  private val myHandler: XSmartStepIntoHandler<V>,
  variants: List<V>,
  private val mySession: XDebugSession,
  private val myEditor: Editor,
) {
  enum class Direction {
    UP, DOWN, LEFT, RIGHT
  }

  internal val myVariants: List<VariantInfo> = variants
    .map { VariantInfo(it) }
    .sortedWith(Comparator
                  .comparingInt<VariantInfo> { v -> v.myVariant.highlightRange!!.startOffset }
                  .thenComparingInt { v -> v.myVariant.highlightRange!!.length })

  internal var myCurrentVariant: VariantInfo? = null
  internal val myHighlighters = mutableListOf<RangeHighlighter>()
  internal var myActionHintSyntheticHighlighter: RangeHighlighter? = null

  internal val DISTANCE_TO_CURRENT_COMPARATOR = Comparator.comparingInt<VariantInfo> {
    abs(it.myStartPoint.x - myCurrentVariant!!.myStartPoint.x)
  }

  private val previousVariant: VariantInfo?
    get() {
      val currentIndex = myVariants.indexOf(myCurrentVariant)
      val previousIndex = if (currentIndex > 0) currentIndex - 1 else myVariants.size - 1
      return myVariants[previousIndex]
    }

  private val nextVariant: VariantInfo?
    get() {
      val currentIndex = myVariants.indexOf(myCurrentVariant)
      val nextIndex = if (currentIndex < myVariants.size - 1) currentIndex + 1 else 0
      return myVariants[nextIndex]
    }

  internal fun selectNext(direction: Direction) {
    val currentLineY = myCurrentVariant!!.myStartPoint.y
    val next = when (direction) {
      Direction.LEFT -> this.previousVariant
      Direction.RIGHT -> this.nextVariant
      Direction.UP -> {
        val previousLineY = myVariants.stream().mapToInt { v -> v.myStartPoint.y }.filter { v -> v < currentLineY }.max().orElse(-1)
        myVariants.stream()
          .filter { v -> v.myStartPoint.y == previousLineY }
          .min(DISTANCE_TO_CURRENT_COMPARATOR)
          .orElseGet { this.previousVariant }
      }
      Direction.DOWN -> {
        val nextLineY = myVariants.stream().mapToInt { v -> v.myStartPoint.y }.filter { v: Int -> v > currentLineY }.min().orElse(-1)
        myVariants.stream()
          .filter { v -> v.myStartPoint.y == nextLineY }
          .min(DISTANCE_TO_CURRENT_COMPARATOR)
          .orElseGet { this.nextVariant }
      }
    }
    if (next != null) {
      select(next)
    }
  }

  internal fun select(variant: VariantInfo) {
    setCurrentVariantHighlighterAttributes(DebuggerColors.SMART_STEP_INTO_TARGET)
    myCurrentVariant = variant
    setCurrentVariantHighlighterAttributes(DebuggerColors.SMART_STEP_INTO_SELECTION)

    val description = variant.myVariant.description
    if (description != null && shouldShowElementDescription(myEditor)) {
      showHint<V>(myEditor, description, variant)
    }
  }

  private fun setCurrentVariantHighlighterAttributes(attributesKey: TextAttributesKey) {
    val index = myVariants.indexOfOrNull(myCurrentVariant) ?: return
    myHighlighters[index].setTextAttributesKey(attributesKey)
  }

  internal fun stepInto(variant: VariantInfo) {
    clear()
    mySession.smartStepInto(myHandler, variant.myVariant)
  }

  internal fun stepIntoCurrent() {
    stepInto(myCurrentVariant!!)
  }

  internal fun clear() {
    myEditor.putUserData(SMART_STEP_INPLACE_DATA, null)
    myEditor.putUserData(SMART_STEP_HINT_DATA, null)
    val highlightManager = HighlightManager.getInstance(mySession.getProject()) as HighlightManagerImpl
    highlightManager.hideHighlights(myEditor, HighlightManager.HIDE_BY_ESCAPE or HighlightManager.HIDE_BY_TEXT_CHANGE)
    // since we don't use HighlightManagerImpl to mark the highlighting with the hide flags it can't be used to remove it as well
    // just remove it manually
    if (myActionHintSyntheticHighlighter != null) {
      myEditor.getMarkupModel().removeHighlighter(myActionHintSyntheticHighlighter!!)
    }
  }

  internal inner class VariantInfo(val myVariant: V) {
    val myStartPoint: Point = myEditor.offsetToXY(myVariant.highlightRange!!.startOffset)
  }
}

@ApiStatus.Internal
abstract class SmartStepEditorActionHandler(protected val myOriginalHandler: EditorActionHandler) : EditorActionHandler() {
  protected override fun doExecute(editor: Editor, caret: Caret?, dataContext: DataContext) {
    val stepData = editor.getUserData(SMART_STEP_INPLACE_DATA)
    if (stepData != null) {
      myPerform(editor, caret, dataContext, stepData)
    }
    else {
      myOriginalHandler.execute(editor, caret, dataContext)
    }
  }

  protected override fun isEnabledForCaret(editor: Editor, caret: Caret, dataContext: DataContext): Boolean {
    return hasSmartStepDebugData(editor) || myOriginalHandler.isEnabled(editor, caret, dataContext)
  }

  protected fun hasSmartStepDebugData(editor: Editor): Boolean = editor.getUserData(SMART_STEP_INPLACE_DATA) != null

  protected abstract fun myPerform(editor: Editor, caret: Caret?, dataContext: DataContext, stepData: SmartStepData<*>)
}

private class UpHandler(original: EditorActionHandler) : SmartStepEditorActionHandler(original) {
  override fun myPerform(editor: Editor, caret: Caret?, dataContext: DataContext, stepData: SmartStepData<*>) {
    stepData.selectNext(SmartStepData.Direction.UP)
  }
}

private class DownHandler(original: EditorActionHandler) : SmartStepEditorActionHandler(original) {
  override fun myPerform(editor: Editor, caret: Caret?, dataContext: DataContext, stepData: SmartStepData<*>) {
    stepData.selectNext(SmartStepData.Direction.DOWN)
  }
}

private class LeftHandler(original: EditorActionHandler) : SmartStepEditorActionHandler(original) {
  override fun myPerform(editor: Editor, caret: Caret?, dataContext: DataContext, stepData: SmartStepData<*>) {
    stepData.selectNext(SmartStepData.Direction.LEFT)
  }
}

private class RightHandler(original: EditorActionHandler) : SmartStepEditorActionHandler(original) {
  override fun myPerform(editor: Editor, caret: Caret?, dataContext: DataContext, stepData: SmartStepData<*>) {
    stepData.selectNext(SmartStepData.Direction.RIGHT)
  }
}

private class EscHandler(original: EditorActionHandler) : SmartStepEditorActionHandler(original) {
  override fun myPerform(editor: Editor, caret: Caret?, dataContext: DataContext, stepData: SmartStepData<*>) {
    editor.putUserData(SMART_STEP_INPLACE_DATA, null)
    if (myOriginalHandler.isEnabled(editor, caret, dataContext)) {
      myOriginalHandler.execute(editor, caret, dataContext)
    }
  }
}

@ApiStatus.Internal
open class XDebugSmartStepIntoEnterHandler(original: EditorActionHandler) : SmartStepEditorActionHandler(original) {
  override fun myPerform(editor: Editor, caret: Caret?, dataContext: DataContext, stepData: SmartStepData<*>) {
    stepData.stepIntoCurrent()
  }
}

private val SHOW_AD = Ref<Boolean?>(true)
private val LOG = Logger.getInstance(XDebuggerSmartStepIntoHandler::class.java)
private const val COUNTER_PROPERTY = "debugger.smart.chooser.counter"

private fun <V : XSmartStepIntoVariant> choose(
  handler: XSmartStepIntoHandler<V>,
  variants: List<V>,
  position: XSourcePosition,
  session: XDebugSession,
  editor: Editor,
) {
  if (`is`("debugger.smart.step.inplace") && variants.all { it.highlightRange != null }) {
    try {
      inplaceChoose(handler, variants, session, editor)
      return
    }
    catch (e: Exception) {
      // in case of any exception fallback to popup
      LOG.error(e)
      val data = editor.getUserData(SMART_STEP_INPLACE_DATA)
      data?.clear()
    }
  }
  showPopup(handler, variants, position, session, editor)
}

private fun <V : XSmartStepIntoVariant> showPopup(
  handler: XSmartStepIntoHandler<V>,
  variants: List<V>,
  position: XSourcePosition,
  session: XDebugSession,
  editor: Editor,
) {
  val highlighter = ScopeHighlighter(editor)
  val popup = ListPopupImpl(session.getProject(), object : BaseListPopupStep<V>(handler.getPopupTitle(position), variants) {
    override fun getIconFor(aValue: V): Icon? {
      return aValue.icon
    }

    override fun getTextFor(value: V): String {
      return value.getText()
    }

    override fun onChosen(selectedValue: V, finalChoice: Boolean): PopupStep<*>? {
      session.smartStepInto(handler, selectedValue)
      highlighter.dropHighlight()
      return FINAL_CHOICE
    }

    override fun canceled() {
      highlighter.dropHighlight()
      super.canceled()
    }
  })

  DebuggerUIUtil.registerExtraHandleShortcuts(popup, SHOW_AD, XDebuggerActions.STEP_INTO, XDebuggerActions.SMART_STEP_INTO)
  UIUtil.maybeInstall(popup.list.getInputMap(JComponent.WHEN_FOCUSED),
                      ListActions.Down.ID,
                      KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0))

  popup.addListSelectionListener { e ->
    if (!e.valueIsAdjusting) {
      val selectedValue = ObjectUtils.doIfCast(e.getSource(), JBList::class.java) { it.getSelectedValue() }
      highlightVariant(selectedValue as? XSmartStepIntoVariant, highlighter)
    }
  }
  highlightVariant(variants.firstOrNull(), highlighter)
  DebuggerUIUtil.showPopupForEditorLine(popup, editor, position.getLine())
}

private fun highlightVariant(variant: XSmartStepIntoVariant?, highlighter: ScopeHighlighter) {
  val range = variant?.highlightRange ?: return
  highlighter.highlight(Pair.create(range, listOf(range)))
}

private fun <V : XSmartStepIntoVariant> inplaceChoose(
  handler: XSmartStepIntoHandler<V>,
  variants: List<V>,
  session: XDebugSession,
  editor: Editor,
) {
  val highlightManager = HighlightManager.getInstance(session.getProject())

  val data = SmartStepData(handler, variants, session, editor)

  val hyperlinkSupport = EditorHyperlinkSupport.get(editor)
  for (info in data.myVariants) {
    val range = info.myVariant.highlightRange
    if (range != null) {
      val highlighters = SmartList<RangeHighlighter>()
      highlightManager.addOccurrenceHighlight(editor, range.startOffset, range.endOffset,
                                              DebuggerColors.SMART_STEP_INTO_TARGET,
                                              HighlightManager.HIDE_BY_ESCAPE or HighlightManager.HIDE_BY_TEXT_CHANGE, highlighters)
      val highlighter = highlighters[0]
      hyperlinkSupport.createHyperlink(highlighter, HyperlinkInfo { data.stepInto(info) })
      data.myHighlighters.add(highlighter)
    }
  }

  val variantInfo = data.myVariants.firstOrNull { v -> v.myVariant === variants[0] }
  if (variantInfo != null) {
    data.select(variantInfo)
  }
  LOG.assertTrue(data.myCurrentVariant != null)
  editor.putUserData(SMART_STEP_INPLACE_DATA, data)
  // for the remote development scenario we have to add a fake invisible highlighter on the whole document with extra payload
  // that will be restored on the client and used to alternate actions availability
  // see com.intellij.openapi.editor.ex.util.EditorActionAvailabilityHintKt.addActionAvailabilityHint
  val highlighter = (editor.getMarkupModel() as MarkupModelEx)
    .addRangeHighlighterAndChangeAttributes(HighlighterColors.NO_HIGHLIGHTING, 0,
                                            editor.getDocument().textLength,
                                            HighlighterLayer.LAST,
                                            HighlighterTargetArea.EXACT_RANGE, false) { h ->
      // this hints should be added in this lambda in order to be serialized by RD markup machinery
      h.addActionAvailabilityHint(
        EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_ENTER, EditorActionAvailabilityHint.AvailabilityCondition.CaretInside),
        EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_TAB, EditorActionAvailabilityHint.AvailabilityCondition.CaretInside),
        EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_ESCAPE, EditorActionAvailabilityHint.AvailabilityCondition.CaretInside),
        EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_MOVE_CARET_UP,
                                     EditorActionAvailabilityHint.AvailabilityCondition.CaretInside),
        EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN,
                                     EditorActionAvailabilityHint.AvailabilityCondition.CaretInside),
        EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT,
                                     EditorActionAvailabilityHint.AvailabilityCondition.CaretInside),
        EditorActionAvailabilityHint(IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT,
                                     EditorActionAvailabilityHint.AvailabilityCondition.CaretInside))
    }
  data.myActionHintSyntheticHighlighter = highlighter

  session.updateExecutionPosition()
  if (AppMode.isRemoteDevHost()) {
    val virtualFile = editor.virtualFile
    // in the case of remote development the ordinary focus request doesn't work, we need to use FileEditorManagerEx api to focus the editor
    if (virtualFile != null) {
      getInstanceEx(session.getProject()).openFile(virtualFile, true, true)
    }
  }
  IdeFocusManager.getGlobalInstance().requestFocus(editor.getContentComponent(), true)
  showInfoHint(editor, data)

  session.addSessionListener(object : XDebugSessionListener {
    fun onAnyEvent() {
      session.removeSessionListener(this)
      UIUtil.invokeLaterIfNeeded {
        val stepData = editor.getUserData(SMART_STEP_INPLACE_DATA)
        stepData?.clear()
      }
    }

    override fun sessionPaused() {
      onAnyEvent()
    }

    override fun sessionResumed() {
      onAnyEvent()
    }

    override fun sessionStopped() {
      onAnyEvent()
    }

    override fun stackFrameChanged() {
      onAnyEvent()
    }

    override fun settingsChanged() {
      onAnyEvent()
    }
  })
}

private fun <V : XSmartStepIntoVariant> showInfoHint(editor: Editor, data: SmartStepData<V>) {
  val propertiesComponent = PropertiesComponent.getInstance()
  val counter = propertiesComponent.getInt(COUNTER_PROPERTY, 0)
  if (counter < 3) {
    val hint = showHint<V>(editor, XDebuggerBundle.message("message.smart.step"), data.myCurrentVariant!!)
    editor.putUserData(SMART_STEP_HINT_DATA, hint)
    propertiesComponent.setValue(COUNTER_PROPERTY, counter + 1, 0)
  }
}

private fun <V : XSmartStepIntoVariant> showHint(
  editor: Editor,
  message: @NlsContexts.HintText String,
  myCurrentVariant: SmartStepData<*>.VariantInfo,
): Hint {
  val hint = LightweightHint(HintUtil.createInformationLabel(message))
  val component = HintManagerImpl.getExternalComponent(editor)
  val convertedPoint = SwingUtilities.convertPoint(editor.getContentComponent(), myCurrentVariant.myStartPoint, component)
  HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, convertedPoint, HintManager.HIDE_BY_TEXT_CHANGE or
    HintManager.HIDE_BY_SCROLLING, 0, false, HintManager.ABOVE)
  return hint
}

private fun shouldShowElementDescription(editor: Editor): Boolean {
  val hint = editor.getUserData(SMART_STEP_HINT_DATA)
  return hint == null || !hint.isVisible()
}

private val SMART_STEP_INPLACE_DATA = Key.create<SmartStepData<*>?>("SMART_STEP_INPLACE_DATA")
private val SMART_STEP_HINT_DATA = Key.create<Hint?>("SMART_STEP_HINT_DATA")

