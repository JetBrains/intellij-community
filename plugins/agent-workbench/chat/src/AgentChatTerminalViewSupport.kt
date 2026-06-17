// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.ide.DataManager
import com.intellij.ide.OccurenceNavigator
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.application.UI
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorGutterFreePainterAreaState
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.openapi.editor.ex.ErrorStripeEvent
import com.intellij.openapi.editor.ex.ErrorStripeListener
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts.Tooltip
import com.intellij.terminal.actions.TerminalActionUtil
import com.intellij.terminal.frontend.view.TerminalView
import com.intellij.terminal.frontend.view.TerminalViewSessionState
import com.intellij.terminal.frontend.view.activeOutputModel
import com.intellij.ui.JBColor
import com.intellij.util.asDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.plugins.terminal.view.TerminalContentChangeEvent
import org.jetbrains.plugins.terminal.view.TerminalLineIndex
import org.jetbrains.plugins.terminal.view.TerminalOutputModel
import org.jetbrains.plugins.terminal.view.TerminalOutputModelListener
import org.jetbrains.plugins.terminal.view.TerminalOutputModelSnapshot
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

@ApiStatus.Internal
const val AGENT_CHAT_PROPOSED_PLAN_NAVIGATION_REGISTRY_KEY: String = "agent.workbench.semantic.proposed.plan.navigation"

private const val AGENT_CHAT_SEMANTIC_REGION_REBUILD_DEBOUNCE_MS: Long = 100

private val AGENT_CHAT_SEMANTIC_REGION_KEY: Key<AgentChatSemanticRegion> =
  Key.create("AgentWorkbench.Chat.SemanticRegion")
private val AGENT_CHAT_PROPOSED_PLAN_STRIPE_COLOR = JBColor(0x5A9955, 0x78C070)
private val AGENT_CHAT_UPDATED_PLAN_STRIPE_COLOR = JBColor(0x4A8DB7, 0x6CB3D9)

@ApiStatus.Internal
data class AgentChatActiveTerminalSnapshot(
  val model: TerminalOutputModel,
  val snapshot: TerminalOutputModelSnapshot,
)

@ApiStatus.Internal
suspend fun captureActiveTerminalSnapshot(
  terminalView: TerminalView,
  sessionState: StateFlow<TerminalViewSessionState>,
  onUnavailable: () -> Unit,
): AgentChatActiveTerminalSnapshot? {
  return withContext(Dispatchers.UI) {
    if (sessionState.value == TerminalViewSessionState.Terminated) {
      onUnavailable()
      null
    }
    else {
      val editor = resolveTerminalEditor(terminalView)
      if (editor == null || editor.isDisposed) {
        onUnavailable()
        null
      }
      else {
        val model = terminalView.activeOutputModel()
        AgentChatActiveTerminalSnapshot(model = model, snapshot = model.takeSnapshot())
      }
    }
  }
}

@ApiStatus.Internal
fun terminalOutputModelChangeFlow(terminalView: TerminalView): Flow<Unit> = callbackFlow {
  val scope = this
  withContext(Dispatchers.UI) {
    val listenerDisposable = scope.asDisposable()
    val listener = object : TerminalOutputModelListener {
      override fun afterContentChanged(event: TerminalContentChangeEvent) {
        if (!scope.isActive || event.isTypeAhead) {
          return
        }
        scope.trySend(Unit)
      }
    }
    terminalView.outputModels.regular.addListener(listenerDisposable, listener)
    terminalView.outputModels.alternative.addListener(listenerDisposable, listener)
  }
  awaitClose()
}

@ApiStatus.Internal
fun resolveTerminalEditor(terminalView: TerminalView): Editor? {
  val dataContext = DataManager.getInstance().getDataContext(terminalView.component)
  return TerminalActionUtil.EDITOR_KEY.getData(dataContext)
}

@ApiStatus.Internal
fun TerminalOutputModelSnapshot.lineText(line: TerminalLineIndex): String {
  return getText(getStartOfLine(line), getEndOfLine(line)).toString()
}

@ApiStatus.Internal
enum class AgentChatSemanticRegionKind {
  PROPOSED_PLAN,
  UPDATED_PLAN,
}

internal enum class AgentChatSemanticNavigationDirection {
  PREVIOUS,
  NEXT,
}

@ApiStatus.Internal
data class AgentChatSemanticRegion(
  val id: String,
  val kind: AgentChatSemanticRegionKind,
  val summary: String,
  val startOffset: Int,
  val endOffset: Int,
  val startLine: Int,
  val endLine: Int,
)

@ApiStatus.Internal
fun interface AgentChatSemanticRegionDetector {
  fun detect(snapshot: TerminalOutputModelSnapshot): List<AgentChatSemanticRegion>
}

@ApiStatus.Internal
interface AgentChatDisposableController {
  fun dispose()
}

internal class AgentChatSemanticRegionController(
  private val terminalView: TerminalView,
  private val sessionState: StateFlow<TerminalViewSessionState>,
  private val detector: AgentChatSemanticRegionDetector,
  parentScope: CoroutineScope,
) : AgentChatDisposableController {
  private val state = AgentChatSemanticRegionState()
  private val navigator = AgentChatSemanticRegionNavigator { state }
  private val rebuildJob: Job
  private val activeModelJob: Job
  private val terminationJob: Job
  private var errorMarkerListenerEditor: Editor? = null
  private var errorMarkerListenerDisposable: com.intellij.openapi.Disposable? = null
  @Volatile
  private var hasNavigableRegions: Boolean = false

  private val errorMarkerListener = object : ErrorStripeListener {
    override fun errorMarkerClicked(e: ErrorStripeEvent) {
      state.navigateTo(e.highlighter.getUserData(AGENT_CHAT_SEMANTIC_REGION_KEY))
    }
  }

  init {
    rebuildJob = parentScope.launch {
      rebuildActiveEditorRegions()
      agentChatSemanticRegionRebuildFlow(terminalView)
        .conflate()
        .collect {
          rebuildActiveEditorRegions()
          delay(AGENT_CHAT_SEMANTIC_REGION_REBUILD_DEBOUNCE_MS.milliseconds)
        }
    }
    activeModelJob = parentScope.launch {
      terminalView.outputModels.active
        .drop(1)
        .collect {
          rebuildActiveEditorRegions()
        }
    }
    terminationJob = parentScope.launch {
      sessionState
        .filter { it == TerminalViewSessionState.Terminated }
        .collect {
          withContext(Dispatchers.UI) {
            removeErrorMarkerListener()
            state.clear()
            hasNavigableRegions = false
          }
        }
    }
  }

  fun occurrenceNavigator(): OccurenceNavigator = navigator

  fun canNavigate(direction: AgentChatSemanticNavigationDirection): Boolean {
    return when (direction) {
      AgentChatSemanticNavigationDirection.PREVIOUS -> hasNavigableRegions
      AgentChatSemanticNavigationDirection.NEXT -> hasNavigableRegions
    }
  }

  fun navigate(direction: AgentChatSemanticNavigationDirection): Boolean {
    return when (direction) {
      AgentChatSemanticNavigationDirection.PREVIOUS -> navigator.goPreviousOccurence()
      AgentChatSemanticNavigationDirection.NEXT -> navigator.goNextOccurence()
    } != null
  }

  override fun dispose() {
    rebuildJob.cancel()
    activeModelJob.cancel()
    terminationJob.cancel()
    removeErrorMarkerListener()
    state.clear()
    hasNavigableRegions = false
  }

  private suspend fun rebuildActiveEditorRegions() {
    val request = captureActiveTerminalSnapshot(terminalView, sessionState) {
      removeErrorMarkerListener()
      state.clear()
      hasNavigableRegions = false
    } ?: return

    val regions = withContext(Dispatchers.Default) {
      detector.detect(request.snapshot)
    }

    withContext(Dispatchers.UI) {
      if (sessionState.value == TerminalViewSessionState.Terminated) {
        removeErrorMarkerListener()
        state.clear()
        hasNavigableRegions = false
        return@withContext
      }

      val activeModel = terminalView.activeOutputModel()
      if (activeModel !== request.model || activeModel.modificationStamp != request.snapshot.modificationStamp) {
        return@withContext
      }

      val editor = resolveTerminalEditor(terminalView)
      if (editor == null || editor.isDisposed) {
        removeErrorMarkerListener()
        state.clear()
        hasNavigableRegions = false
        return@withContext
      }

      installErrorMarkerListenerIfNeeded(editor)
      state.apply(editor, regions)
      hasNavigableRegions = state.hasRegions()
    }
  }

  private fun installErrorMarkerListenerIfNeeded(editor: Editor) {
    if (errorMarkerListenerEditor === editor) {
      return
    }
    removeErrorMarkerListener()
    val markupModel = editor.markupModel as? EditorMarkupModel ?: return
    val disposable = Disposer.newDisposable()
    markupModel.addErrorMarkerListener(errorMarkerListener, disposable)
    errorMarkerListenerEditor = editor
    errorMarkerListenerDisposable = disposable
  }

  private fun removeErrorMarkerListener() {
    errorMarkerListenerEditor = null
    errorMarkerListenerDisposable?.let(Disposer::dispose)
    errorMarkerListenerDisposable = null
  }
}

internal class AgentChatSemanticRegionState {
  private var currentEditor: Editor? = null
  private var currentRegions: List<AgentChatSemanticRegion> = emptyList()
  private val activeHighlighters: MutableList<RangeHighlighter> = mutableListOf()

  fun apply(editor: Editor, regions: List<AgentChatSemanticRegion>) {
    if (editor.isDisposed) {
      clear()
      return
    }

    if (currentEditor !== editor) {
      clear()
    }

    currentEditor = editor
    currentRegions = regions.sortedBy(AgentChatSemanticRegion::startOffset)
    removeHighlighters()
    (editor.markupModel as? EditorMarkupModel)?.setErrorStripeVisible(regions.isNotEmpty())
    (editor as? EditorEx)?.gutterComponentEx?.setRightFreePaintersAreaState(
      if (regions.isEmpty()) EditorGutterFreePainterAreaState.HIDE else EditorGutterFreePainterAreaState.SHOW,
    )
    for (region in currentRegions) {
      val highlighter = editor.markupModel.addRangeHighlighter(
        region.startOffset,
        region.endOffset,
        HighlighterLayer.ADDITIONAL_SYNTAX,
        null,
        HighlighterTargetArea.LINES_IN_RANGE,
      )
      val stripeColor = when (region.kind) {
        AgentChatSemanticRegionKind.PROPOSED_PLAN -> AGENT_CHAT_PROPOSED_PLAN_STRIPE_COLOR
        AgentChatSemanticRegionKind.UPDATED_PLAN -> AGENT_CHAT_UPDATED_PLAN_STRIPE_COLOR
      }
      highlighter.setErrorStripeMarkColor(stripeColor)
      highlighter.setThinErrorStripeMark(true)
      highlighter.errorStripeTooltip = buildRegionTooltip(region)
      highlighter.putUserData(AGENT_CHAT_SEMANTIC_REGION_KEY, region)
      activeHighlighters += highlighter
    }
  }

  fun clear() {
    val editor = currentEditor
    currentEditor = null
    currentRegions = emptyList()
    removeHighlighters()
    if (editor != null && !editor.isDisposed) {
      (editor.markupModel as? EditorMarkupModel)?.setErrorStripeVisible(false)
      (editor as? EditorEx)?.gutterComponentEx?.setRightFreePaintersAreaState(EditorGutterFreePainterAreaState.HIDE)
    }
  }

  fun hasRegions(): Boolean = currentRegions.isNotEmpty() && currentEditor?.isDisposed != true

  fun navigate(direction: AgentChatSemanticNavigationDirection): OccurenceNavigator.OccurenceInfo? {
    val editor = currentEditor ?: return null
    if (editor.isDisposed || currentRegions.isEmpty()) {
      return null
    }
    val caretOffset = editor.caretModel.offset
    val targetIndex = when (direction) {
      AgentChatSemanticNavigationDirection.NEXT -> currentRegions.indexOfFirst { it.startOffset > caretOffset }
                                                     .takeIf { it >= 0 } ?: 0
      AgentChatSemanticNavigationDirection.PREVIOUS -> currentRegions.indexOfLast { it.startOffset < caretOffset }
                                                         .takeIf { it >= 0 } ?: currentRegions.lastIndex
    }
    return navigateTo(currentRegions[targetIndex])
  }

  fun navigateTo(region: AgentChatSemanticRegion?): OccurenceNavigator.OccurenceInfo? {
    val targetRegion = region ?: return null
    val editor = currentEditor ?: return null
    if (editor.isDisposed) {
      return null
    }
    val targetOffset = targetRegion.startOffset.coerceIn(0, editor.document.textLength)
    editor.caretModel.moveToOffset(targetOffset)
    editor.selectionModel.removeSelection()
    editor.scrollingModel.scrollToCaret(ScrollType.CENTER)
    val index = currentRegions.indexOfFirst { it.id == targetRegion.id }
    if (index < 0) {
      return null
    }
    return OccurenceNavigator.OccurenceInfo.position(index + 1, currentRegions.size)
  }

  private fun removeHighlighters() {
    activeHighlighters.forEach { highlighter ->
      if (highlighter.isValid) {
        highlighter.dispose()
      }
    }
    activeHighlighters.clear()
  }
}

internal class AgentChatSemanticRegionNavigator(
  private val stateProvider: () -> AgentChatSemanticRegionState,
) : OccurenceNavigator {
  override fun hasNextOccurence(): Boolean = stateProvider().hasRegions()

  override fun hasPreviousOccurence(): Boolean = stateProvider().hasRegions()

  override fun goNextOccurence(): OccurenceNavigator.OccurenceInfo? {
    return stateProvider().navigate(AgentChatSemanticNavigationDirection.NEXT)
  }

  override fun goPreviousOccurence(): OccurenceNavigator.OccurenceInfo? {
    return stateProvider().navigate(AgentChatSemanticNavigationDirection.PREVIOUS)
  }

  override fun getNextOccurenceActionName(): String {
    return AgentChatBundle.message("action.AgentWorkbenchSessions.EditorTab.NextProposedPlan.text")
  }

  override fun getPreviousOccurenceActionName(): String {
    return AgentChatBundle.message("action.AgentWorkbenchSessions.EditorTab.PreviousProposedPlan.text")
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}

private fun buildRegionTooltip(region: AgentChatSemanticRegion): @Tooltip String {
  return when (region.kind) {
    AgentChatSemanticRegionKind.PROPOSED_PLAN -> AgentChatBundle.message("chat.semantic.region.proposed.plan.tooltip", region.summary)
    AgentChatSemanticRegionKind.UPDATED_PLAN -> AgentChatBundle.message("chat.semantic.region.updated.plan.tooltip", region.summary)
  }
}

private fun agentChatSemanticRegionRebuildFlow(terminalView: TerminalView): Flow<Unit> = terminalOutputModelChangeFlow(terminalView)
