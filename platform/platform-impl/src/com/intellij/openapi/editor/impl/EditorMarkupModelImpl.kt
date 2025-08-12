// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "OVERRIDE_DEPRECATION", "ReplaceGetOrSet")

package com.intellij.openapi.editor.impl

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.hint.*
import com.intellij.icons.AllIcons
import com.intellij.ide.ActivityTracker
import com.intellij.ide.IdeEventQueue
import com.intellij.ide.actions.ActionsCollector
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.ActionUtil.performAction
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.actionSystem.remoting.ActionWithMergeId
import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.InternalUICustomization
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId
import com.intellij.openapi.editor.colors.ColorKey
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.event.*
import com.intellij.openapi.editor.ex.*
import com.intellij.openapi.editor.impl.inspector.InspectionsGroup
import com.intellij.openapi.editor.impl.inspector.RedesignedInspectionsManager
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.editor.markup.AnalyzerStatus.Companion.EMPTY
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.fileEditor.impl.EditorWindowHolder
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.JBPopupMenu
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.util.*
import com.intellij.openapi.util.registry.Registry.Companion.`is`
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.util.coroutines.flow.throttle
import com.intellij.ui.*
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBScrollBar
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.Alarm
import com.intellij.util.Processor
import com.intellij.util.cancelOnDispose
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.*
import com.intellij.util.ui.JBValue.UIInteger
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.*
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import java.util.*
import java.util.Queue
import javax.swing.*
import javax.swing.border.Border
import javax.swing.plaf.FontUIResource
import javax.swing.plaf.LabelUI
import kotlin.concurrent.Volatile
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
class EditorMarkupModelImpl internal constructor(private val editor: EditorImpl) :
  MarkupModelImpl(editor.document), EditorMarkupModel, CaretListener, BulkAwareDocumentListener.Simple, VisibleAreaListener {
  private fun getMinMarkHeight(): Int {
    return scale(minMarkHeight)
  }

  // null renderer means we should not show a traffic light icon
  private var errorStripeRenderer: ErrorStripeRenderer? = null
  private val resourcesDisposable = Disposer.newCheckedDisposable()
  private val statusUpdates = MergingUpdateQueue(javaClass.getName(), 50, true, MergingUpdateQueue.ANY_COMPONENT, resourcesDisposable)

  // query daemon status in BGT (because it's rather expensive and PSI-related) and then update the icon in EDT later
  private val trafficLightIconUpdateRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val errorStripeMarkersModel: ErrorStripeMarkersModel

  private var dimensionsAreValid = false
  private var myEditorScrollbarTop = -1
  private var myEditorTargetHeight = -1
  private var myEditorSourceHeight = -1
  private var myDirtyYPositions: ProperTextRange? = null
  private var tooltipRendererProvider: ErrorStripTooltipRendererProvider = BasicTooltipRendererProvider()

  private var minMarkHeight = 0 // height for horizontal, width for vertical stripes
  private val editorFragmentRenderer = EditorFragmentRenderer(editor)
  private val mouseMovementTracker = MouseMovementTracker()
  private var myRowAdjuster = 0
  private var myWheelAccumulator = 0
  private var myLastVisualLine = 0
  private var myCurrentHint: Reference<LightweightHint?>? = null
  private var myCurrentHintAnchorY = 0
  private var myKeepHint = false

  val statusToolbar: ActionToolbarImpl
  private var showToolbar = EditorSettingsExternalizable.getInstance().isShowInspectionWidget
  private var trafficLightVisible = true
  private val toolbarComponentListener: ComponentListener
  private var cachedToolbarBounds = Rectangle()
  private val smallIconLabel = JLabel()

  @Volatile
  private var analyzerStatus = EMPTY
  private var hasAnalyzed = false
  private var isAnalyzing = false
  private var showNavigation = false
  private var reportErrorStripeInconsistency = true
  private val trafficLightPopup: TrafficLightPopup
  private val statusTimer = Alarm(resourcesDisposable)
  private val extraActions: DefaultActionGroup
  private val extensionActions = HashMap<InspectionWidgetActionProvider, AnAction>()

  init {
    setMinMarkHeight(DaemonCodeAnalyzerSettings.getInstance().errorStripeMarkMinHeight)

    trafficLightPopup = TrafficLightPopup(editor, CompactViewAction())

    val nextErrorAction = createAction("GotoNextError", AllIcons.Actions.FindAndShowNextMatchesSmall)
    val prevErrorAction = createAction("GotoPreviousError", AllIcons.Actions.FindAndShowPrevMatchesSmall)

    class ExtraActionGroup : DefaultActionGroup(), ActionWithMergeId
    extraActions = ExtraActionGroup()
    populateInspectionWidgetActionsFromExtensions()

    val actions = StatusToolbarGroup(
      extraActions,
      InspectionsGroup({ analyzerStatus }, editor),
      TrafficLightAction(),
      NavigationGroup(prevErrorAction, nextErrorAction),
    )

    var editorButtonLook = InternalUICustomization.getInstance()?.getEditorToolbarButtonLook() ?: EditorToolbarButtonLook(editor)
    val statusToolbar = EditorInspectionsActionToolbar(actions, editor, editorButtonLook, nextErrorAction, prevErrorAction)
    this.statusToolbar = statusToolbar

    statusToolbar.setMiniMode(true)
    statusToolbar.setOrientation(SwingConstants.HORIZONTAL)
    statusToolbar.setCustomButtonLook(editorButtonLook)
    toolbarComponentListener = object : ComponentAdapter() {
      override fun componentResized(event: ComponentEvent) {
        val toolbar = event.component
        if (toolbar.getWidth() > 0 && toolbar.getHeight() > 0) {
          updateTrafficLightVisibility()
        }
      }
    }

    val toolbar = statusToolbar.getComponent()
    toolbar.setLayout(StatusComponentLayout())
    toolbar.addComponentListener(toolbarComponentListener)
    toolbar.setBorder(JBUI.Borders.empty(2))

    /*    if(RedesignedInspectionsManager.isAvailable()) {
      GotItTooltip tooltip = new GotItTooltip("redesigned.inspections.tooltip",
                                              "The perfect companion for on the go, training and sports education. Through an integrated straw, the bottle sends thirst quickly without beating. Thanks to the screw cap, the bottle is quickly filled, and it stays in place", resourcesDisposable);
      tooltip.withShowCount(1);
      tooltip.withHeader("Paw Patrol");
      tooltip.withIcon(AllIcons.General.BalloonInformation);
      tooltip.show(toolbar, GotItTooltip.BOTTOM_MIDDLE);
    }*/
    smallIconLabel.addMouseListener(object : MouseAdapter() {
      override fun mouseClicked(event: MouseEvent?) {
        trafficLightPopup.hidePopup()
        analyzerStatus.controller.toggleProblemsView()
      }

      override fun mouseEntered(event: MouseEvent) {
        trafficLightPopup.scheduleShow(event, analyzerStatus)
      }

      override fun mouseExited(event: MouseEvent?) {
        trafficLightPopup.scheduleHide()
      }
    })
    smallIconLabel.setOpaque(false)
    smallIconLabel.setBackground(JBColor.lazy { editor.colorsScheme.getDefaultBackground() })
    smallIconLabel.isVisible = false

    val statusPanel = NonOpaquePanel()
    statusPanel.isVisible = !editor.isOneLineMode
    statusPanel.setLayout(BoxLayout(statusPanel, BoxLayout.X_AXIS))
    statusPanel.add(toolbar)
    statusPanel.add(smallIconLabel)

    (editor.scrollPane as JBScrollPane).setStatusComponent(statusPanel)

    val connection = ApplicationManager.getApplication().getMessageBus().connect(resourcesDisposable)
    connection.subscribe<AnActionListener>(AnActionListener.TOPIC, object : AnActionListener {
      override fun beforeActionPerformed(action: AnAction, event: AnActionEvent) {
        if (HintManagerImpl.isActionToIgnore(action)) {
          return
        }
        trafficLightPopup.hidePopup()
      }
    })

    connection.subscribe(LafManagerListener.TOPIC, LafManagerListener { trafficLightPopup.updateUI() })
    connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
      override fun selectionChanged(event: FileEditorManagerEvent) {
        showToolbar = EditorSettingsExternalizable.getInstance().isShowInspectionWidget && analyzerStatus.controller.isToolbarEnabled

        updateTrafficLightVisibility()
      }
    })

    errorStripeMarkersModel = ErrorStripeMarkersModel(editor, resourcesDisposable)

    val project = editor.project
    @Suppress("IfThenToSafeAccess")
    if (project != null) {
      project.service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
        trafficLightIconUpdateRequests
          .throttle(50)
          .collectLatest {
            val errorStripeRenderer = errorStripeRenderer ?: return@collectLatest
            val newStatus = readAction { errorStripeRenderer.getStatus() }
            if (!newStatus.equalsTo(analyzerStatus)) {
              withContext(Dispatchers.EDT) {
                changeStatus(newStatus)
              }
            }
          }
      }.cancelOnDispose(resourcesDisposable)
    }
  }

  companion object {
    @JvmStatic
    fun fitLineToEditor(editor: EditorImpl, visualLine: Int): Int {
      val lineCount = editor.visibleLineCount
      var shift = 0
      if (visualLine >= lineCount - 1) {
        val sequence = editor.document.charsSequence
        shift = if (sequence.isEmpty()) 0 else if (sequence.get(sequence.length - 1) == '\n') 1 else 0
      }
      return max(0, min(lineCount - shift, visualLine))
    }

    @JvmField
    val DISABLE_CODE_LENS: Key<Boolean> = Key<Boolean>("DISABLE_CODE_LENS")
    private const val LEFT_RIGHT_INDENT = 5
    private const val INTER_GROUP_OFFSET = 6
    private const val QUICK_ANALYSIS_TIMEOUT_MS = 3000
    private val LOG = logger<EditorMarkupModelImpl>()

    private val ERROR_STRIPE_TOOLTIP_GROUP = TooltipGroup("ERROR_STRIPE_TOOLTIP_GROUP", 0)

    private val SCROLLBAR_WIDTH: JBValue = UIInteger("Editor.scrollBarWidth", 14)

    internal val ICON_TEXT_COLOR = ColorKey.createColorKey("ActionButton.iconTextForeground", UIUtil.getContextHelpForeground())

    private val thinGap: Int
      get() = scale(2)

    private val maxStripeSize: Int
      get() = scale(4)

    private val maxMacThumbWidth: Int
      get() = scale(10)

    internal val statusIconSize: Int
      get() = scale(18)

    private val WHOLE_DOCUMENT = ProperTextRange(0, 0)

    private val EXPANDED_STATUS = Key<List<StatusItem>>("EXPANDED_STATUS")
    private val TRANSLUCENT_STATE = Key<Boolean>("TRANSLUCENT_STATE")
  }

  override fun toString(): String = "EditorMarkupModel for $editor"

  override fun caretPositionChanged(event: CaretEvent) {
    updateTrafficLightVisibility()
  }

  override fun afterDocumentChange(document: Document) {
    trafficLightPopup.hidePopup()
    updateTrafficLightVisibility()
  }

  override fun visibleAreaChanged(e: VisibleAreaEvent) {
    updateTrafficLightVisibility()
  }

  private fun updateTrafficLightVisibility() {
    statusUpdates.queue(object : Update("visibility") {
      override suspend fun execute() {
        writeIntentReadAction {
          doUpdateTrafficLightVisibility()
        }
      }

      override fun run() {
        WriteIntentReadAction.run { doUpdateTrafficLightVisibility() }
      }
    })
  }

  private fun doUpdateTrafficLightVisibility() {
    if (trafficLightVisible) {
      if (RedesignedInspectionsManager.isAvailable()) {
        statusToolbar.updateActionsAsync()
      }

      if (showToolbar && editor.myView != null) {
        statusToolbar.setTargetComponent(editor.contentComponent)
        val pos = editor.caretModel.primaryCaret.getVisualPosition()
        var point = editor.visualPositionToXY(pos)
        point = SwingUtilities.convertPoint(editor.contentComponent, point, editor.scrollPane)

        val stComponent = statusToolbar.getComponent()
        if (stComponent.isVisible) {
          val bounds = SwingUtilities.convertRectangle(stComponent, stComponent.bounds, editor.scrollPane)

          if (!bounds.isEmpty && bounds.contains(point)) {
            cachedToolbarBounds = bounds
            stComponent.isVisible = false
            smallIconLabel.isVisible = true
          }
        }
        else if (!cachedToolbarBounds.contains(point)) {
          stComponent.isVisible = true
          smallIconLabel.isVisible = false
        }
      }
      else {
        statusToolbar.getComponent().isVisible = false
        smallIconLabel.isVisible = true
      }
    }
    else {
      statusToolbar.getComponent().isVisible = false
      smallIconLabel.isVisible = false
    }
  }

  private fun populateInspectionWidgetActionsFromExtensions() {
    for (extension in InspectionWidgetActionProvider.EP_NAME.extensionList) {
      val action = extension.createAction(editor) ?: continue
      extensionActions.put(extension, action)
      addInspectionWidgetAction(action, null)
    }

    InspectionWidgetActionProvider.EP_NAME.addExtensionPointListener(object : ExtensionPointListener<InspectionWidgetActionProvider> {
      override fun extensionAdded(extension: InspectionWidgetActionProvider, pluginDescriptor: PluginDescriptor) {
        ApplicationManager.getApplication().invokeLater {
          val action = extension.createAction(editor)
          if (action != null) {
            extensionActions.put(extension, action)
            addInspectionWidgetAction(action, null)
          }
        }
      }

      override fun extensionRemoved(extension: InspectionWidgetActionProvider, pluginDescriptor: PluginDescriptor) {
        ApplicationManager.getApplication().invokeLater {
          val action = extensionActions.remove(extension)
          if (action != null) {
            removeInspectionWidgetAction(action)
          }
        }
      }
    }, resourcesDisposable)
  }

  override fun addInspectionWidgetAction(action: AnAction, constraints: Constraints?) {
    if (constraints == null) {
      extraActions.add(action)
    }
    else {
      extraActions.add(action, constraints)
    }

    if (action is Disposable) {
      Disposer.register(resourcesDisposable, action)
    }
  }

  override fun removeInspectionWidgetAction(action: AnAction) {
    extraActions.remove(action)
    if (action is Disposable) {
      Disposer.dispose(action)
    }
  }

  private fun createAction(id: String, icon: Icon): AnAction {
    val delegate = ActionManager.getInstance().getAction(id)
    val result = object : MarkupModelDelegateAction(delegate) {
      override fun update(e: AnActionEvent) {
        if (RedesignedInspectionsManager.isAvailable()) {
          e.presentation.setEnabledAndVisible(false)
          return
        }
        e.presentation.setEnabledAndVisible(true)
        super.update(e)
      }
    }
    result.getTemplatePresentation().setIcon(icon)
    return result
  }

  private fun offsetToLine(offset: Int, document: Document): Int {
    if (offset < 0) {
      return 0
    }
    if (offset > document.textLength) {
      return editor.visibleLineCount
    }
    return editor.offsetToVisualLine(offset)
  }

  private fun repaintVerticalScrollBar() {
    editor.verticalScrollBar.repaint()
  }

  fun recalcEditorDimensions() {
    val scrollBar = editor.verticalScrollBar
    val scrollBarHeight = max(0, scrollBar.size.height)

    myEditorScrollbarTop = scrollBar.getDecScrollButtonHeight() /* + 1*/
    assert(myEditorScrollbarTop >= 0)
    val editorScrollbarBottom = scrollBar.getIncScrollButtonHeight()
    myEditorTargetHeight = scrollBarHeight - myEditorScrollbarTop - editorScrollbarBottom
    myEditorSourceHeight = editor.preferredHeight

    dimensionsAreValid = scrollBarHeight != 0
  }

  override fun setTrafficLightIconVisible(value: Boolean) {
    if (errorPanel == null) {
      return
    }

    if (value != trafficLightVisible) {
      trafficLightVisible = value
      updateTrafficLightVisibility()
    }
    repaint()
  }

  fun repaintTrafficLightIcon() {
    if (errorStripeRenderer != null) {
      trafficLightIconUpdateRequests.tryEmit(Unit)
    }
  }

  private fun changeStatus(newStatus: AnalyzerStatus) {
    ThreadingAssertions.assertEventDispatchThread()
    if (!isErrorStripeVisible || resourcesDisposable.isDisposed()) {
      return
    }
    statusTimer.cancelAllRequests()

    val analyzingType = newStatus.analyzingType
    val resetAnalyzingStatus = analyzerStatus.isTextStatus() && analyzerStatus.analyzingType == AnalyzingType.COMPLETE
    analyzerStatus = newStatus
    smallIconLabel.setIcon(analyzerStatus.icon)

    if (showToolbar != analyzerStatus.controller.isToolbarEnabled) {
      showToolbar = EditorSettingsExternalizable.getInstance().isShowInspectionWidget && analyzerStatus.controller.isToolbarEnabled
      updateTrafficLightVisibility()
    }

    val analyzing = analyzingType != AnalyzingType.COMPLETE
    hasAnalyzed = !resetAnalyzingStatus && (hasAnalyzed || (isAnalyzing && !analyzing))
    isAnalyzing = analyzing

    if (analyzingType != AnalyzingType.EMPTY) {
      showNavigation = analyzerStatus.showNavigation
    }
    else {
      statusTimer.addRequest({
        hasAnalyzed = false
        ActivityTracker.getInstance().inc()
      }, QUICK_ANALYSIS_TIMEOUT_MS)
    }

    trafficLightPopup.updateVisiblePopup(analyzerStatus)
    ActivityTracker.getInstance().inc()
  }

  // Used in Rider please do not drop it
  fun forcingUpdateStatusToolbar() {
    statusUpdates.queue(object : Update("forcingUpdate") {
      override fun run() {
        @Suppress("DEPRECATION")
        statusToolbar.updateActionsImmediately()
      }
    })
  }

  private val currentHint: LightweightHint?
    get() {
      val hint = (myCurrentHint ?: return null).get()
      if (hint == null || !hint.isVisible()) {
        myCurrentHint = null
        return null
      }
      return hint
    }

  // true if tooltip shown
  private fun showToolTipByMouseMove(e: MouseEvent): Boolean {
    ThreadingAssertions.assertEventDispatchThread()
    val currentHint = this.currentHint
    if (currentHint != null && (myKeepHint || mouseMovementTracker.isMovingTowards(e, getBoundsOnScreen(currentHint)))) {
      return true
    }
    val visualLine = getVisualLineByEvent(e)
    myLastVisualLine = visualLine
    val area = editor.scrollingModel.getVisibleArea()
    val visualY = editor.visualLineToY(visualLine)
    val isVisible = myWheelAccumulator == 0 && area.contains(area.x, visualY)

    if (!isVisible &&
        UISettings.getInstance().showEditorToolTip &&
        (true != editor.getUserData(DISABLE_CODE_LENS)) &&
        !UIUtil.uiParents(editor.component, false).filter(EditorWindowHolder::class.java).isEmpty()) {
      val rowRatio = visualLine.toFloat() / (editor.visibleLineCount - 1)
      val y = if (myRowAdjuster != 0) (rowRatio * editor.verticalScrollBar.getHeight()).toInt() else e.getY() + 1
      val highlighters = ArrayList<RangeHighlighterEx>()
      collectRangeHighlighters(this, visualLine, highlighters)
      collectRangeHighlighters(editor.filteredDocumentMarkupModel, visualLine, highlighters)
      editorFragmentRenderer.show(visualLine, highlighters, e.isAltDown, createHint(e.component, Point(0, y)))
      return true
    }

    val highlighters = getNearestHighlighters(e.getY() + 1)
    if (highlighters.isEmpty()) {
      return false
    }

    val y: Int
    val nearest = getNearestRangeHighlighter(e)
    if (nearest == null) {
      y = e.getY()
    }
    else {
      val range = offsetsToYPositions(nearest.getStartOffset(), nearest.getEndOffset())
      val eachStartY = range.startOffset
      val eachEndY = range.endOffset
      y = eachStartY + (eachEndY - eachStartY) / 2
    }
    if (currentHint != null && y == myCurrentHintAnchorY) {
      return true
    }

    ReadAction.nonBlocking<TooltipRenderer?> { tooltipRendererProvider.calcTooltipRenderer(highlighters) }
      .expireWhen { editor.isDisposed }
      .finishOnUiThread(ModalityState.nonModal()) { bigRenderer ->
        if (bigRenderer != null) {
          val hint = showTooltip(bigRenderer, createHint(e.component, Point(0, y + 1)).setForcePopup(true))
          myCurrentHint = WeakReference<LightweightHint?>(hint)
          myCurrentHintAnchorY = y
          myKeepHint = false
          mouseMovementTracker.reset()
        }
      }
      .submit(AppExecutorUtil.getAppExecutorService())
    return true
  }

  private fun getVisualLineByEvent(e: MouseEvent): Int {
    var y = e.getY()
    if (e.getSource() === editor.verticalScrollBar && y == editor.verticalScrollBar.getHeight() - 1) {
      y++
    }
    return fitLineToEditor(editor, editor.offsetToVisualLine(yPositionToOffset(y + myWheelAccumulator, true)))
  }

  private fun getOffset(visualLine: Int, startLine: Boolean): Int {
    return editor.visualPositionToOffset(VisualPosition(visualLine, if (startLine) 0 else Int.MAX_VALUE))
  }

  private fun collectRangeHighlighters(
    markupModel: MarkupModelEx,
    visualLine: Int,
    highlighters: MutableList<RangeHighlighterEx>,
  ) {
    val startOffset = getOffset(fitLineToEditor(editor, visualLine - EditorFragmentRenderer.PREVIEW_LINES), true)
    val endOffset = getOffset(fitLineToEditor(editor, visualLine + EditorFragmentRenderer.PREVIEW_LINES), false)
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, Processor { highlighter ->
      val tooltip = highlighter.getErrorStripeTooltip()
      if (tooltip != null &&
          !(tooltip is HighlightInfo && tooltip.type === HighlightInfoType.TODO) &&
          highlighter.getStartOffset() < endOffset &&
          highlighter.getEndOffset() > startOffset &&
          highlighter.getErrorStripeMarkColor(editor.colorsScheme) != null) {
        highlighters.add(highlighter)
      }
      true
    })
  }

  private fun getNearestRangeHighlighter(e: MouseEvent): RangeHighlighter? {
    val highlighters = getNearestHighlighters(e.getY())
    var nearestMarker: RangeHighlighter? = null
    var yPos = 0
    for (highlighter in highlighters) {
      val newYPos = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset()).startOffset
      if (nearestMarker == null || abs(yPos - e.getY()) > abs(newYPos - e.getY())) {
        nearestMarker = highlighter
        yPos = newYPos
      }
    }
    return nearestMarker
  }

  private fun getNearestHighlighters(y: Int): MutableSet<RangeHighlighter> {
    val highlighters = HashSet<RangeHighlighter>()
    addNearestHighlighters(this, y, highlighters)
    addNearestHighlighters(editor.filteredDocumentMarkupModel, y, highlighters)
    return highlighters
  }

  private fun addNearestHighlighters(
    markupModel: MarkupModelEx,
    scrollBarY: Int,
    result: MutableCollection<in RangeHighlighter>,
  ) {
    val startOffset = yPositionToOffset(scrollBarY - getMinMarkHeight(), true)
    val endOffset = yPositionToOffset(scrollBarY + getMinMarkHeight(), false)
    markupModel.processRangeHighlightersOverlappingWith(startOffset, endOffset, Processor { highlighter ->
      if (highlighter.getErrorStripeMarkColor(editor.colorsScheme) != null) {
        val range = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset())
        if (scrollBarY >= range.startOffset - getMinMarkHeight() * 2 &&
            scrollBarY <= range.endOffset + getMinMarkHeight() * 2
        ) {
          result.add(highlighter)
        }
      }
      true
    })
  }

  private fun doClick(e: MouseEvent) {
    val marker = getNearestRangeHighlighter(e)
    val offset: Int
    var logicalPositionToScroll: LogicalPosition? = null
    val editorPreviewHint = editorFragmentRenderer.editorPreviewHint
    if (marker == null) {
      if (editorPreviewHint != null) {
        logicalPositionToScroll = editor.visualToLogicalPosition(VisualPosition(editorFragmentRenderer.startVisualLine, 0))
        offset = editor.document.getLineStartOffset(logicalPositionToScroll.line)
      }
      else {
        return
      }
    }
    else {
      offset = marker.getStartOffset()
    }

    val doc: Document = editor.document
    if (doc.getLineCount() > 0 && editorPreviewHint == null) {
      // Necessary to expand folded block even if navigating just before one
      // Very useful when navigating to the first unused import statement.
      val lineEnd = doc.getLineEndOffset(doc.getLineNumber(offset))
      editor.caretModel.moveToOffset(lineEnd)
    }
    editor.caretModel.removeSecondaryCarets()
    editor.caretModel.moveToOffset(offset)
    editor.selectionModel.removeSelection()
    val scrollingModel = editor.scrollingModel
    scrollingModel.disableAnimation()
    if (logicalPositionToScroll != null) {
      val lineY = editor.logicalPositionToXY(logicalPositionToScroll).y
      val relativePopupOffset = editorFragmentRenderer.relativeY
      scrollingModel.scrollVertically(lineY - relativePopupOffset)
    }
    else {
      scrollingModel.scrollToCaret(ScrollType.CENTER)
    }
    scrollingModel.enableAnimation()
    if (marker != null) {
      errorStripeMarkersModel.fireErrorMarkerClicked(marker, e)
    }
  }

  override fun setErrorStripeVisible(value: Boolean) {
    if (value) {
      disposeErrorPanel()
      val panel = MyErrorPanel()
      editor.verticalScrollBar.setPersistentUI(panel)
    }
    else {
      editor.verticalScrollBar.setPersistentUI(JBScrollBar.createUI(null))
    }
    errorStripeMarkersModel.setActive(value)
  }

  private val errorPanel: MyErrorPanel?
    get() = editor.verticalScrollBar.getUI() as? MyErrorPanel

  override fun setErrorPanelPopupHandler(handler: PopupHandler) {
    ThreadingAssertions.assertEventDispatchThread()
    errorPanel?.setPopupHandler(handler)
  }

  override fun setErrorStripTooltipRendererProvider(provider: ErrorStripTooltipRendererProvider) {
    tooltipRendererProvider = provider
  }

  override fun getErrorStripTooltipRendererProvider(): ErrorStripTooltipRendererProvider = tooltipRendererProvider

  override fun getEditor(): Editor = editor

  override fun setErrorStripeRenderer(renderer: ErrorStripeRenderer?) {
    ThreadingAssertions.assertEventDispatchThread()
    (errorStripeRenderer as Disposable?)?.let {
      Disposer.dispose(it)
    }
    errorStripeRenderer = renderer
    if (renderer is Disposable) {
      Disposer.register(resourcesDisposable, renderer)
    }
    //try to not cancel tooltips here, since it is being called after every writeAction, even to the console
    //HintManager.getInstance().getTooltipController().cancelTooltips();
  }

  override fun getErrorStripeRenderer(): ErrorStripeRenderer? = errorStripeRenderer

  override fun dispose() {
    Disposer.dispose(resourcesDisposable)

    disposeErrorPanel()

    statusToolbar.getComponent().removeComponentListener(toolbarComponentListener)
    (editor.scrollPane as JBScrollPane).setStatusComponent(null)

    errorStripeRenderer = null
    tooltipRendererProvider = BasicTooltipRendererProvider()
    editorFragmentRenderer.clearHint()

    trafficLightPopup.hidePopup()
    extensionActions.clear()

    analyzerStatus = EMPTY

    super.dispose()
  }

  private fun disposeErrorPanel() {
    errorPanel?.uninstallListeners()
  }

  fun rebuild() {
    errorStripeMarkersModel.rebuild()
  }

  // startOffset == -1 || endOffset == -1 means whole document
  @JvmOverloads
  fun repaint(startOffset: Int = -1, endOffset: Int = -1) {
    val range = offsetsToYPositions(startOffset, endOffset)
    markDirtied(range)
    if (startOffset == -1 || endOffset == -1) {
      myDirtyYPositions = WHOLE_DOCUMENT
    }

    val bar = editor.verticalScrollBar
    bar.repaint(0, range.startOffset, bar.getWidth(), range.length + getMinMarkHeight())
  }

  private val isMirrored: Boolean
    get() = editor.isMirrored

  private fun transparent(): Boolean = !editor.shouldScrollBarBeOpaque()

  @DirtyUI
  private inner class MyErrorPanel : ButtonlessScrollBarUI(), MouseMotionListener, MouseListener, MouseWheelListener, UISettingsListener {
    private var handler: PopupHandler? = null
    private var cachedTrack: BufferedImage? = null
    private var cachedHeight = -1

    fun dropCache() {
      cachedTrack = null
      cachedHeight = -1
    }

    override fun alwaysShowTrack(): Boolean {
      if (scrollbar.getOrientation() == Adjustable.VERTICAL) {
        return !transparent()
      }
      return super.alwaysShowTrack()
    }

    override fun installUI(c: JComponent?) {
      super.installUI(c)
      dropCache()
    }

    override fun uninstallUI(c: JComponent) {
      super.uninstallUI(c)
      dropCache()
    }

    override fun installListeners() {
      super.installListeners()
      scrollbar.addMouseMotionListener(this)
      scrollbar.addMouseListener(this)
      scrollbar.addMouseWheelListener(this)
    }

    @Suppress("RedundantVisibilityModifier")
    public override fun uninstallListeners() {
      scrollbar.removeMouseMotionListener(this)
      scrollbar.removeMouseListener(this)
      super.uninstallListeners()
    }

    override fun uiSettingsChanged(uiSettings: UISettings) {
      if (!uiSettings.showEditorToolTip) {
        hideMyEditorPreviewHint()
      }
      setMinMarkHeight(DaemonCodeAnalyzerSettings.getInstance().errorStripeMarkMinHeight)
      repaintTrafficLightIcon()
      repaintVerticalScrollBar()

      trafficLightPopup.updateVisiblePopup(analyzerStatus)
    }

    override fun paintThumb(g: Graphics, c: JComponent, thumbBounds: Rectangle?) {
      if (isMacOverlayScrollbar) {
        if (!isMirrored) {
          super.paintThumb(g, c, thumbBounds)
        }
        else {
          val g2d = g as Graphics2D
          val old = g2d.transform
          val tx = AffineTransform.getScaleInstance(-1.0, 1.0)
          tx.translate(-c.getWidth().toDouble(), 0.0)
          g2d.transform(tx)
          super.paintThumb(g, c, thumbBounds)
          g2d.transform = old
        }
      }
      else {
        super.paintThumb(g, c, thumbBounds)
      }
    }

    override fun isThumbTranslucent(): Boolean = true

    override fun getThumbOffset(value: Int): Int {
      if (SystemInfoRt.isMac || `is`("editor.full.width.scrollbar")) {
        return getMinMarkHeight() + scale(2)
      }
      @Suppress("DEPRECATION")
      return super.getThumbOffset(value)
    }

    override fun isDark(): Boolean = editor.isDarkEnough

    override fun alwaysPaintThumb(): Boolean = true

    override fun getMacScrollBarBounds(baseBounds: Rectangle, thumb: Boolean): Rectangle {
      val bounds = super.getMacScrollBarBounds(baseBounds, thumb)
      bounds.width = min(bounds.width, maxMacThumbWidth)
      val b2 = bounds.width / 2
      bounds.x = thinGap + getMinMarkHeight() + SCROLLBAR_WIDTH.get() / 2 - b2
      return bounds
    }

    override fun getThickness(): Int = SCROLLBAR_WIDTH.get() + thinGap + getMinMarkHeight()

    override fun paintTrack(g: Graphics, c: JComponent, trackBounds: Rectangle) {
      if (editor.isDisposed) {
        return
      }
      if (transparent()) {
        EditorThreading.run { doPaintTrack(g, c, trackBounds) }
      }
      else {
        super.paintTrack(g, c, trackBounds)
      }
    }

    override fun doPaintTrack(g: Graphics, c: JComponent, bounds: Rectangle) {
      val clip = g.clipBounds.intersection(bounds)
      if (clip.height == 0) {
        return
      }

      val componentBounds = c.bounds
      val docRange = ProperTextRange.create(0, componentBounds.height)
      if (cachedTrack == null || cachedHeight != componentBounds.height) {
        cachedTrack = UIUtil.createImage(c, componentBounds.width, componentBounds.height, BufferedImage.TYPE_INT_ARGB)
        cachedHeight = componentBounds.height
        myDirtyYPositions = docRange
        dimensionsAreValid = false
        paintTrackBasement(cachedTrack!!.graphics, Rectangle(0, 0, componentBounds.width, componentBounds.height))
      }
      if (myDirtyYPositions === WHOLE_DOCUMENT) {
        myDirtyYPositions = docRange
      }
      if (myDirtyYPositions != null) {
        val imageGraphics = cachedTrack!!.createGraphics()

        myDirtyYPositions = myDirtyYPositions!!.intersection(docRange)
        if (myDirtyYPositions == null) myDirtyYPositions = docRange
        repaint(imageGraphics, componentBounds.width, myDirtyYPositions!!)
        myDirtyYPositions = null
      }

      StartupUiUtil.drawImage(g, cachedTrack!!)
    }

    fun paintTrackBasement(g: Graphics, bounds: Rectangle) {
      if (transparent()) {
        val g2 = g as Graphics2D
        g2.composite = AlphaComposite.getInstance(AlphaComposite.CLEAR)
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
        g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER)
      }
      else {
        g.color = editor.backgroundColor
        g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height)
      }
    }

    override fun adjustColor(c: Color?): Color {
      return if (isMacOverlayScrollbar) super.adjustColor(c) else EditorImpl.adjustThumbColor(super.adjustColor(c), isDark)
    }

    fun repaint(g: Graphics, gutterWidth: Int, yRange: ProperTextRange) {
      val clip = Rectangle(0, yRange.startOffset, gutterWidth, yRange.length + getMinMarkHeight())
      paintTrackBasement(g, clip)

      val startOffset = yPositionToOffset(clip.y - getMinMarkHeight(), true)
      val endOffset = yPositionToOffset(clip.y + clip.height, false)

      val oldClip = g.clip
      g.clipRect(clip.x, clip.y, clip.width, clip.height)

      drawErrorStripeMarkers(g, startOffset, endOffset)

      @Suppress("GraphicsSetClipInspection")
      g.clip = oldClip
    }

    fun drawErrorStripeMarkers(g: Graphics, startOffset: Int, endOffset: Int) {
      val thinEnds: Queue<PositionedStripe> = PriorityQueue(5, Comparator.comparingInt { o -> o.yEnd })
      val wideEnds: Queue<PositionedStripe> = PriorityQueue(5, Comparator.comparingInt { o -> o.yEnd })
      // sorted by layer
      val thinStripes = ArrayList<PositionedStripe>() // layer desc
      val wideStripes = ArrayList<PositionedStripe>() // layer desc
      val thinYStart = IntArray(1) // in range 0...yStart all spots are drawn
      val wideYStart = IntArray(1) // in range 0...yStart all spots are drawn

      val iterator = errorStripeMarkersModel.highlighterIterator(startOffset, endOffset)
      try {
        ContainerUtil.process(iterator, Processor { highlighter ->
          val isThin = highlighter.isThinErrorStripeMark()
          val yStart = if (isThin) thinYStart else wideYStart
          val stripes = if (isThin) thinStripes else wideStripes
          val ends = if (isThin) thinEnds else wideEnds

          val range = offsetsToYPositions(highlighter.getStartOffset(), highlighter.getEndOffset())
          val ys = range.startOffset
          var ye = range.endOffset
          if (ye - ys < getMinMarkHeight()) ye = ys + getMinMarkHeight()

          yStart[0] = drawStripesEndingBefore(ys, ends, stripes, g, yStart[0])

          val layer = highlighter.getLayer()

          var stripe: PositionedStripe? = null
          var i = 0
          while (i < stripes.size) {
            val s = stripes.get(i)
            if (s.layer == layer) {
              stripe = s
              break
            }
            if (s.layer < layer) {
              break
            }
            i++
          }
          val colorsScheme = editor.colorsScheme
          val color = highlighter.getErrorStripeMarkColor(colorsScheme)
          if (color == null) {
            if (reportErrorStripeInconsistency) {
              reportErrorStripeInconsistency = false
              LOG.error("Error stripe marker has no color. highlighter: $highlighter, color scheme: $colorsScheme (${colorsScheme.javaClass})")
            }
            return@Processor true
          }

          if (stripe == null) {
            // started new stripe, draw previous above
            if (i == 0 && yStart[0] != ys) {
              if (!stripes.isEmpty()) {
                val top = stripes.get(0)
                drawSpot(g, top.thin, yStart[0], ys, top.color)
              }
              yStart[0] = ys
            }
            stripe = PositionedStripe(color, ye, isThin, layer)
            stripes.add(i, stripe)
            ends.offer(stripe)
          }
          else {
            if (stripe.yEnd < ye) {
              if (color != stripe.color) {
                // paint previous stripe on this layer
                if (i == 0 && yStart[0] != ys) {
                  drawSpot(g, stripe.thin, yStart[0], ys, stripe.color)
                  yStart[0] = ys
                }
                stripe.color = color
              }

              // key changed, reinsert into queue
              ends.remove(stripe)
              stripe.yEnd = ye
              ends.offer(stripe)
            }
          }
          true
        })
      }
      finally {
        iterator.dispose()
      }

      drawStripesEndingBefore(Int.MAX_VALUE, thinEnds, thinStripes, g, thinYStart[0])
      drawStripesEndingBefore(Int.MAX_VALUE, wideEnds, wideStripes, g, wideYStart[0])
    }

    fun drawStripesEndingBefore(
      ys: Int,
      ends: Queue<out PositionedStripe?>,
      stripes: MutableList<PositionedStripe>,
      g: Graphics, yStart: Int,
    ): Int {
      var yStart = yStart
      while (!ends.isEmpty()) {
        val endingStripe: PositionedStripe? = ends.peek()
        if (endingStripe == null || endingStripe.yEnd > ys) break
        ends.remove()

        // check whether endingStripe got obscured in the range yStart...endingStripe.yEnd
        val i = stripes.indexOf(endingStripe)
        stripes.removeAt(i)
        if (i == 0) {
          // visible
          drawSpot(g, endingStripe.thin, yStart, endingStripe.yEnd, endingStripe.color)
          yStart = endingStripe.yEnd
        }
      }
      return yStart
    }

    fun drawSpot(g: Graphics, thinErrorStripeMark: Boolean, yStart: Int, yEnd: Int, color: Color) {
      var yStart = yStart
      var yEnd = yEnd
      val paintWidth: Int
      val x: Int
      if (thinErrorStripeMark) {
        paintWidth = getMinMarkHeight()
        x = if (isMirrored) thickness - paintWidth else 0
        if (yEnd - yStart < 6) {
          yStart -= 1
          yEnd += yEnd - yStart - 1
        }
      }
      else {
        x = if (isMirrored) 0 else getMinMarkHeight() + thinGap
        paintWidth = SCROLLBAR_WIDTH.get()
      }
      g.color = color
      g.fillRect(x, yStart, paintWidth, yEnd - yStart)
    }

    // mouse events
    override fun mouseClicked(e: MouseEvent) {
      CommandProcessor.getInstance().executeCommand(
        editor.project,
        { doMouseClicked(e) },
        EditorBundle.message("move.caret.command.name"),
        DocCommandGroupId.noneGroupId(document),
        UndoConfirmationPolicy.DEFAULT,
        document,
      )
    }

    override fun mousePressed(e: MouseEvent) {
    }

    override fun mouseReleased(e: MouseEvent) {
    }

    val width: Int
      get() = scrollbar.getWidth()

    fun doMouseClicked(e: MouseEvent) {
      @Suppress("DEPRECATION")
      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown {
        IdeFocusManager.getGlobalInstance().requestFocus(editor.contentComponent, true)
      }
      val lineCount = document.getLineCount() + editor.settings.getAdditionalLinesCount()
      if (lineCount == 0) {
        return
      }
      if (e.getX() > 0 && e.getX() <= this.width) {
        doClick(e)
      }
    }

    override fun mouseMoved(e: MouseEvent) {
      val lineCount = document.getLineCount() + editor.settings.getAdditionalLinesCount()
      if (lineCount == 0) {
        return
      }

      if (e.getX() > 0 && e.getX() <= this.width && showToolTipByMouseMove(e)) {
        UIUtil.setCursor(scrollbar, Cursor.getPredefinedCursor(Cursor.HAND_CURSOR))
        return
      }

      cancelMyToolTips(e, false)

      if (scrollbar.getCursor() == Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)) {
        scrollbar.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR))
      }
    }

    override fun mouseWheelMoved(e: MouseWheelEvent) {
      if (editorFragmentRenderer.editorPreviewHint == null) {
        // process wheel event by the parent scroll pane if no code lens
        MouseEventAdapter.redispatch(e, e.component.getParent())
        return
      }
      val units = e.unitsToScroll
      if (units == 0) return
      // Stop accumulating when the last or the first line has been reached as 'adjusted' position to show lens.
      if (myLastVisualLine < editor.visibleLineCount - 1 && units > 0 || myLastVisualLine > 0 && units < 0) {
        myWheelAccumulator += units
      }
      myRowAdjuster = myWheelAccumulator / editor.lineHeight
      showToolTipByMouseMove(e)
    }

    fun cancelMyToolTips(e: MouseEvent, checkIfShouldSurvive: Boolean) {
      hideMyEditorPreviewHint()
      val tooltipController = TooltipController.getInstance()
      if (!checkIfShouldSurvive || !tooltipController.shouldSurvive(e)) {
        tooltipController.cancelTooltip(ERROR_STRIPE_TOOLTIP_GROUP, e, true)
      }
    }

    override fun mouseEntered(e: MouseEvent) {
    }

    override fun mouseExited(e: MouseEvent) {
      hideMyEditorPreviewHint()
      val currentHint = currentHint
      if (currentHint != null && !myKeepHint) {
        closeHintOnMovingMouseAway(currentHint)
      }
    }

    fun closeHintOnMovingMouseAway(hint: LightweightHint) {
      val disposable = Disposer.newDisposable()
      IdeEventQueue.getInstance().addDispatcher({ e: AWTEvent? ->
        if (e!!.getID() == MouseEvent.MOUSE_PRESSED) {
          myKeepHint = true
          Disposer.dispose(disposable)
        }
        else if (e.getID() == MouseEvent.MOUSE_MOVED && !hint.isInsideHint(RelativePoint(e as MouseEvent))) {
          hint.hide()
          Disposer.dispose(disposable)
        }
        false
      }, disposable)
    }

    override fun mouseDragged(e: MouseEvent) {
      cancelMyToolTips(e, true)
    }

    fun setPopupHandler(handler: PopupHandler) {
      if (this@MyErrorPanel.handler != null) {
        scrollbar.removeMouseListener(this@MyErrorPanel.handler)
      }

      this@MyErrorPanel.handler = handler
      scrollbar.addMouseListener(handler)
    }
  }

  private fun hideMyEditorPreviewHint() {
    editorFragmentRenderer.hideHint()
    myRowAdjuster = 0
    myWheelAccumulator = 0
    myLastVisualLine = 0
  }

  private fun showTooltip(tooltipObject: TooltipRenderer, hintHint: HintHint): LightweightHint? {
    hideMyEditorPreviewHint()
    return TooltipController.getInstance().showTooltipByMouseMove(
      editor,
      hintHint.targetPoint,
      tooltipObject,
      editor.verticalScrollbarOrientation == EditorEx.VERTICAL_SCROLLBAR_RIGHT,
      ERROR_STRIPE_TOOLTIP_GROUP,
      hintHint,
    )
  }

  override fun addErrorMarkerListener(listener: ErrorStripeListener, parent: Disposable) {
    errorStripeMarkersModel.addErrorMarkerListener(listener, parent)
  }

  private fun markDirtied(yPositions: ProperTextRange) {
    if (myDirtyYPositions !== WHOLE_DOCUMENT) {
      val start = max(0, yPositions.startOffset - editor.lineHeight)
      val end = if (myEditorScrollbarTop + myEditorTargetHeight == 0)
        yPositions.endOffset + editor.lineHeight
      else min(myEditorScrollbarTop + myEditorTargetHeight, yPositions.endOffset + editor.lineHeight)
      val adj = ProperTextRange(start, max(end, start))

      myDirtyYPositions = if (myDirtyYPositions == null) adj else myDirtyYPositions!!.union(adj)
    }

    myEditorScrollbarTop = 0
    myEditorSourceHeight = 0
    myEditorTargetHeight = 0
    dimensionsAreValid = false
  }

  override fun setMinMarkHeight(minMarkHeight: Int) {
    this.minMarkHeight = min(minMarkHeight, maxStripeSize)
  }

  override fun isErrorStripeVisible(): Boolean = errorPanel != null

  private class BasicTooltipRendererProvider : ErrorStripTooltipRendererProvider {
    override fun calcTooltipRenderer(highlighters: MutableCollection<out RangeHighlighter>): TooltipRenderer? {
      ThreadingAssertions.assertBackgroundThread()
      var bigRenderer: LineTooltipRenderer? = null
      //do not show the same tooltip twice
      var tooltips: MutableSet<String?>? = null

      for (highlighter in highlighters) {
        val tooltipObject = highlighter.getErrorStripeTooltip()
        if (tooltipObject == null) continue

        @Suppress("HardCodedStringLiteral")
        val text = if (tooltipObject is HighlightInfo) tooltipObject.getToolTip() else tooltipObject.toString()
        if (text == null) {
          continue
        }

        if (tooltips == null) {
          tooltips = HashSet<String?>()
        }
        if (tooltips.add(text)) {
          if (bigRenderer == null) {
            bigRenderer = LineTooltipRenderer(text, arrayOf<Any>(highlighters))
          }
          else {
            bigRenderer.addBelow(text)
          }
        }
      }

      return bigRenderer
    }

    override fun calcTooltipRenderer(text: String): TooltipRenderer {
      return LineTooltipRenderer(text, arrayOf<Any>(text))
    }

    override fun calcTooltipRenderer(text: String, width: Int): TooltipRenderer {
      return LineTooltipRenderer(text, width, arrayOf<Any>(text))
    }
  }

  private fun offsetsToYPositions(start: Int, end: Int): ProperTextRange {
    if (!dimensionsAreValid) {
      recalcEditorDimensions()
    }
    val document: Document = editor.document
    val startLineNumber = if (end == -1) 0 else offsetToLine(start, document)
    val editorStartY = editor.visualLineToY(startLineNumber)
    val editorTargetHeight = max(0, myEditorTargetHeight)
    val startY: Int = myEditorScrollbarTop + if (myEditorSourceHeight < editorTargetHeight) {
      editorStartY
    }
    else {
      (editorStartY.toFloat() / myEditorSourceHeight * editorTargetHeight).toInt()
    }

    var endY: Int
    val endLineNumber = offsetToLine(end, document)
    if (end == -1 || start == -1) {
      endY = min(myEditorSourceHeight, editorTargetHeight)
    }
    else if (startLineNumber == endLineNumber) {
      endY = startY // both offsets are on the same line, no need to re-calc Y position
    }
    else if (myEditorSourceHeight < editorTargetHeight) {
      endY = myEditorScrollbarTop + editor.visualLineToY(endLineNumber)
    }
    else {
      val editorEndY = editor.visualLineToY(endLineNumber)
      endY = myEditorScrollbarTop + (editorEndY.toFloat() / myEditorSourceHeight * editorTargetHeight).toInt()
    }
    if (endY < startY) endY = startY
    return ProperTextRange(startY, endY)
  }

  private fun yPositionToOffset(y: Int, beginLine: Boolean): Int {
    if (!dimensionsAreValid) {
      recalcEditorDimensions()
    }
    val safeY = max(0, y - myEditorScrollbarTop)
    val editorY: Int
    if (myEditorSourceHeight < myEditorTargetHeight) {
      editorY = safeY
    }
    else {
      val fraction = max(0f, min(1f, safeY / myEditorTargetHeight.toFloat()))
      editorY = (fraction * myEditorSourceHeight).toInt()
    }
    val visual = editor.xyToVisualPosition(Point(0, editorY))
    val line = editor.visualToLogicalPosition(visual).line
    val document: Document = editor.document
    if (line < 0) {
      return 0
    }
    if (line >= document.getLineCount()) {
      return document.textLength
    }

    val foldingModel: FoldingModelEx = editor.foldingModel
    if (beginLine) {
      val offset = document.getLineStartOffset(line)
      val startCollapsed = foldingModel.getCollapsedRegionAtOffset(offset)
      return if (startCollapsed == null) offset else min(offset, startCollapsed.getStartOffset())
    }
    else {
      val offset = document.getLineEndOffset(line)
      val startCollapsed = foldingModel.getCollapsedRegionAtOffset(offset)
      return if (startCollapsed == null) offset else max(offset, startCollapsed.getEndOffset())
    }
  }

  @ApiStatus.Internal
  fun errorStripeMarkersModelAttributesChanged(highlighter: RangeHighlighterEx) {
    errorStripeMarkersModel.attributesChanged(highlighter, true)
  }

  private inner class TrafficLightAction : DumbAwareAction(), CustomComponentAction, ActionRemoteBehaviorSpecification.Frontend {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return TrafficLightButton(this, presentation, EditorToolbarButtonLook(editor), place, editor.colorsScheme)
    }

    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
      (component as TrafficLightButton).updateFromPresentation(presentation)
    }

    override fun actionPerformed(e: AnActionEvent) {
      trafficLightPopup.hidePopup()
      analyzerStatus.controller.toggleProblemsView()
    }

    override fun update(e: AnActionEvent) {
      val presentation = e.presentation

      if (RedesignedInspectionsManager.isAvailable()) {
        presentation.setEnabledAndVisible(false)
        return
      }

      val newStatus = analyzerStatus.expandedStatus
      val newIcon = analyzerStatus.icon

      presentation.setVisible(!analyzerStatus.isEmpty())

      if (!hasAnalyzed || analyzerStatus.analyzingType != AnalyzingType.EMPTY) {
        presentation.putClientProperty(EXPANDED_STATUS, newStatus.ifEmpty { listOf(StatusItem("", newIcon)) })
        presentation.putClientProperty(TRANSLUCENT_STATE, analyzerStatus.analyzingType != AnalyzingType.COMPLETE)
      }
      else {
        presentation.putClientProperty(TRANSLUCENT_STATE, true)
      }
    }
  }

  private inner class TrafficLightButton(
    action: AnAction,
    presentation: Presentation,
    buttonLook: ActionButtonLook,
    place: String,
    colorsScheme: EditorColorsScheme,
  ) : JPanel() {
    private var mousePressed = false
    private var mouseHover = false
    private val buttonLook: ActionButtonLook
    private val mouseListener: MouseListener
    private val colorsScheme: EditorColorsScheme
    private var items: List<StatusItem?>? = null
    private var translucent = false

    init {
      setLayout(GridBagLayout())
      setOpaque(false)

      this.buttonLook = buttonLook
      this.colorsScheme = colorsScheme

      mouseListener = object : MouseAdapter() {
        override fun mouseClicked(me: MouseEvent) {
          if (SwingUtilities.isLeftMouseButton(me)) {
            showInspectionHint(me)
          }
        }

        fun showInspectionHint(me: MouseEvent?) {
          val context = ActionToolbar.getDataContextFor(this@TrafficLightButton)
          val event = AnActionEvent.createEvent(context, presentation, place, ActionUiKind.TOOLBAR, me)
          val result = performAction(action, event)
          if (result.isPerformed) {
            ActionsCollector.getInstance().record(event.project, action, event, null)
            @Suppress("DEPRECATION")
            ActionToolbar.findToolbarBy(this@TrafficLightButton)?.updateActionsImmediately()
          }
        }

        fun showContextMenu(me: MouseEvent) {
          val group = DefaultActionGroup()
          /*
          TODO: show context menu by right click
          group.addAll(analyzerStatus.getController().getActions());
          group.add(new CompactViewAction());
          */
          if (0 < group.childrenCount) {
            JBPopupMenu.showByEvent(me, ActionPlaces.EDITOR_INSPECTIONS_TOOLBAR, group)
          }
        }

        override fun mousePressed(me: MouseEvent) {
          if (me.isPopupTrigger) showContextMenu(me)
          mousePressed = true
          repaint()
        }

        override fun mouseReleased(me: MouseEvent) {
          if (me.isPopupTrigger) showContextMenu(me)
          mousePressed = false
          repaint()
        }

        override fun mouseEntered(me: MouseEvent) {
          mouseHover = true
          trafficLightPopup.scheduleShow(me, analyzerStatus)
          repaint()
        }

        override fun mouseExited(me: MouseEvent?) {
          mouseHover = false
          trafficLightPopup.scheduleHide()
          repaint()
        }
      }

      setBorder(object : Border {
        override fun paintBorder(c: Component?, g: Graphics?, x: Int, y: Int, w: Int, h: Int) {}

        override fun isBorderOpaque(): Boolean = false

        override fun getBorderInsets(c: Component?): Insets {
          return if (showNavigation) JBUI.insets(2, 2, 2, 0) else JBUI.insets(2)
        }
      })
    }

    fun updateFromPresentation(presentation: Presentation) {
      val newTranslucent = true == presentation.getClientProperty(TRANSLUCENT_STATE)
      val newItems = presentation.getClientProperty(EXPANDED_STATUS)
      if (translucent == newTranslucent && items == newItems) {
        return
      }

      translucent = newTranslucent
      items = newItems
      updateContents(newItems ?: emptyList())
      revalidate()
      repaint()
    }

    override fun addNotify() {
      super.addNotify()
      addMouseListener(mouseListener)
    }

    override fun removeNotify() {
      removeMouseListener(mouseListener)
    }

    fun updateContents(status: List<StatusItem>) {
      removeAll()

      setEnabled(!status.isEmpty())
      isVisible = !status.isEmpty()

      val gc = GridBag().nextLine()
      if (status.size == 1 && StringUtil.isEmpty(status.get(0).text)) {
        add(createStyledLabel(null, status.get(0).icon, SwingConstants.CENTER),
            gc.next().weightx(1.0).weighty(1.0).fillCell())
      }
      else if (!status.isEmpty()) {
        val leftRightOffset = scale(LEFT_RIGHT_INDENT)
        add(Box.createHorizontalStrut(leftRightOffset), gc.next())

        var counter = 0
        for (item in status) {
          add(createStyledLabel(item.text, item.icon, SwingConstants.LEFT),
              gc.next().insetLeft(if (counter++ > 0) INTER_GROUP_OFFSET else 0).fillCell().weighty(1.0))
        }

        add(Box.createHorizontalStrut(leftRightOffset), gc.next())
      }
    }

    fun createStyledLabel(text: @Nls String?, icon: Icon?, alignment: Int): JLabel {
      val label: JLabel = object : JLabel(text, icon, alignment) {
        override fun paintComponent(graphics: Graphics) {
          val g2 = graphics.create() as Graphics2D
          try {
            val alpha = if (translucent) 0.5f else 1.0f
            g2.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)
            super.paintComponent(g2)
          }
          finally {
            g2.dispose()
          }
        }

        override fun setUI(ui: LabelUI?) {
          super.setUI(ui)

          if (!SystemInfo.isWindows) {
            var font = getFont()
            // allow resetting the font by UI
            font = FontUIResource(font.deriveFont(font.getStyle(), (font.getSize() - scale(2)).toFloat()))
            setFont(font)
          }
        }
      }

      label.setForeground(JBColor.lazy { (colorsScheme.getColor(ICON_TEXT_COLOR)) ?: ICON_TEXT_COLOR.defaultColor })
      label.setIconTextGap(scale(1))

      return label
    }

    override fun paintComponent(graphics: Graphics?) {
      val state = if (mousePressed) ActionButtonComponent.PUSHED else if (mouseHover) ActionButtonComponent.POPPED else ActionButtonComponent.NORMAL
      buttonLook.paintBackground(graphics, this, state)
    }

    override fun getPreferredSize(): Dimension {
      if (componentCount == 0) {
        return JBUI.emptySize()
      }

      val size = super.getPreferredSize()
      val i = getInsets()
      size.height = max(statusIconSize + i.top + i.bottom, size.height)
      size.width = max(statusIconSize + i.left + i.right, size.width)
      return size
    }
  }

  @ApiStatus.Internal
  class StatusComponentLayout : LayoutManager {
    private val actionButtons = ArrayList<Pair<Component?, String?>>()

    override fun addLayoutComponent(s: String?, component: Component?) {
      actionButtons.add(Pair(component, s))
    }

    override fun removeLayoutComponent(component: Component?) {
      for (i in actionButtons.indices) {
        if (component == actionButtons.get(i).first) {
          actionButtons.removeAt(i)
          break
        }
      }
    }

    override fun preferredLayoutSize(container: Container): Dimension {
      val size = JBUI.emptySize()

      for (c in actionButtons) {
        if (c.first!!.isVisible) {
          val prefSize = c.first!!.preferredSize
          size.height = max(size.height, prefSize.height)
        }
      }

      for (c in actionButtons) {
        if (c.first!!.isVisible) {
          val prefSize = c.first!!.preferredSize
          val i = (c.first as JComponent).getInsets()
          JBInsets.removeFrom(prefSize, i)

          if (ActionToolbar.SEPARATOR_CONSTRAINT == c.second) {
            size.width += prefSize.width + i.left + i.right
          }
          else {
            val maxBareHeight = size.height - i.top - i.bottom
            size.width += max(prefSize.width, maxBareHeight) + i.left + i.right
          }
        }
      }

      if (size.width > 0 && size.height > 0) {
        JBInsets.addTo(size, container.insets)
      }
      return size
    }

    override fun minimumLayoutSize(container: Container): Dimension = preferredLayoutSize(container)

    override fun layoutContainer(container: Container) {
      val prefSize = preferredLayoutSize(container)

      if (prefSize.width <= 0 || prefSize.height <= 0) {
        return
      }

      val i = container.insets
      JBInsets.removeFrom(prefSize, i)
      var offset = i.left

      for (c in actionButtons) {
        if (c.first!!.isVisible) {
          val cPrefSize = c.first!!.preferredSize

          if (c.first is TrafficLightButton) {
            c.first!!.setBounds(offset, i.top, cPrefSize.width, prefSize.height)
            offset += cPrefSize.width
          }
          else {
            val jcInsets = (c.first as JComponent).getInsets()
            JBInsets.removeFrom(cPrefSize, jcInsets)

            if (ActionToolbar.SEPARATOR_CONSTRAINT == c.second) {
              c.first!!.setBounds(offset, i.top, cPrefSize.width, prefSize.height)
              offset += cPrefSize.width
            }
            else {
              val maxBareHeight = prefSize.height - jcInsets.top - jcInsets.bottom
              val width = max(cPrefSize.width, maxBareHeight) + jcInsets.left + jcInsets.right
              c.first!!.setBounds(offset, i.top, width, prefSize.height)
              offset += width
            }
          }
        }
      }
    }
  }

  inner class CompactViewAction internal constructor() : ToggleAction(EditorBundle.message("iw.compact.view")) {
    override fun isSelected(e: AnActionEvent): Boolean = !showToolbar

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      showToolbar = !state
      EditorSettingsExternalizable.getInstance().setShowInspectionWidget(showToolbar)
      updateTrafficLightVisibility()
      ActionsCollector.getInstance().record(e.project, this, e, null)
    }

    override fun update(e: AnActionEvent) {
      super.update(e)
      e.presentation.setEnabled(analyzerStatus.controller.isToolbarEnabled)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun isDumbAware(): Boolean = true
  }

  private open inner class MarkupModelDelegateAction(delegate: AnAction) : AnActionWrapper(delegate) {
    override fun actionPerformed(e: AnActionEvent) {
      val focusManager = IdeFocusManager.getInstance(editor.project)

      @Suppress("removal", "DEPRECATION")
      val delegateEvent = AnActionEvent.createFromAnAction(delegate,
                                                           e.inputEvent,
                                                           ActionPlaces.EDITOR_INSPECTIONS_TOOLBAR,
                                                           editor.dataContext)

      if (focusManager.getFocusOwner() !== editor.contentComponent) {
        focusManager.requestFocus(editor.contentComponent, true).doWhenDone { delegate.actionPerformed(delegateEvent) }
      }
      else {
        delegate.actionPerformed(delegateEvent)
      }
    }
  }

  private inner class NavigationGroup(vararg actions: AnAction) : DefaultActionGroup(*actions), ActionRemoteBehaviorSpecification.Frontend {
    override fun getActionUpdateThread() = ActionUpdateThread.EDT

    override fun update(e: AnActionEvent) {
      e.presentation.setEnabledAndVisible(showNavigation)
    }
  }

  @ApiStatus.Internal
  class StatusToolbarGroup internal constructor(vararg actions: AnAction) : DefaultActionGroup(*actions)

  private fun getBoundsOnScreen(hint: LightweightHint): Rectangle {
    val component = hint.component
    val location = hint.getLocationOn(component)
    SwingUtilities.convertPointToScreen(location, component)
    return Rectangle(location, hint.size)
  }

  private fun createHint(component: Component?, point: Point?): HintHint {
    return HintHint(component, point)
      .setAwtTooltip(true)
      .setPreferredPosition(Balloon.Position.atLeft)
      .setBorderInsets(JBUI.insets(EditorFragmentRenderer.EDITOR_FRAGMENT_POPUP_BORDER))
      .setShowImmediately(true)
      .setAnimationEnabled(false)
      .setStatus(HintHint.Status.Info)
  }

  private class PositionedStripe(
    @JvmField var color: Color,
    @JvmField var yEnd: Int,
    @JvmField val thin: Boolean,
    @JvmField val layer: Int,
  )

}


