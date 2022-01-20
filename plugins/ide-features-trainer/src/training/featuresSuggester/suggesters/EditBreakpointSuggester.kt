package training.featuresSuggester.suggesters

import com.google.common.collect.EvictingQueue
import com.intellij.lang.Language
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.XSourcePosition.isOnTheSameLine
import training.featuresSuggester.*
import training.featuresSuggester.actions.Action
import training.featuresSuggester.actions.DebugSessionPausedAction
import training.util.WeakReferenceDelegator
import java.lang.ref.WeakReference
import java.util.*

class EditBreakpointSuggester : AbstractFeatureSuggester() {
  override val id: String = "Edit breakpoint"
  override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("edit.breakpoint.name")

  override val message = FeatureSuggesterBundle.message("edit.breakpoint.message")
  override val suggestingActionId = "com.intellij.xdebugger.impl.actions.EditBreakpointAction\$ContextAction"
  override val suggestingTipFileName = "BreakpointSpeedmenu.html"
  override val minSuggestingIntervalDays = 30

  override val languages = listOf(Language.ANY.id)

  private val numOfPausesToGetSuggestion = 8

  @Suppress("UnstableApiUsage")
  private val pausesOnBreakpointHistory: Queue<WeakReference<XSourcePosition>> =
    EvictingQueue.create(numOfPausesToGetSuggestion)
  private var previousSuggestionPosition: XSourcePosition? by WeakReferenceDelegator(null)

  override fun getSuggestion(action: Action): Suggestion {
    when (action) {
      is DebugSessionPausedAction -> {
        val breakpoint = findBreakpointOnPosition(action.project, action.position)
        if (breakpoint != null && breakpoint.conditionExpression == null) {
          pausesOnBreakpointHistory.add(WeakReference(action.position))
        }
        else {
          pausesOnBreakpointHistory.clear()
        }

        if (pausesOnBreakpointHistory.isAllOnTheSameLine() &&
            !isOnTheSameLine(pausesOnBreakpointHistory.lastOrNull()?.get(), previousSuggestionPosition)
        ) {
          previousSuggestionPosition = pausesOnBreakpointHistory.lastOrNull()?.get()
          pausesOnBreakpointHistory.clear()
          return TipSuggestion(message, id, suggestingTipFileName)
        }
      }
    }
    return NoSuggestion
  }

  private fun Queue<WeakReference<XSourcePosition>>.isAllOnTheSameLine(): Boolean {
    if (size < numOfPausesToGetSuggestion) return false
    val lastPos = last().get()
    return all { isOnTheSameLine(it.get(), lastPos) }
  }
}
