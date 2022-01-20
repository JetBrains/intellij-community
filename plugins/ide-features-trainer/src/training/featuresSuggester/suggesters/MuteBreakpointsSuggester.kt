package training.featuresSuggester.suggesters

import com.intellij.lang.Language
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.XSourcePosition.isOnTheSameLine
import training.featuresSuggester.FeatureSuggesterBundle
import training.featuresSuggester.NoSuggestion
import training.featuresSuggester.Suggestion
import training.featuresSuggester.actions.*
import training.featuresSuggester.findBreakpointOnPosition
import kotlin.math.abs

class MuteBreakpointsSuggester : AbstractFeatureSuggester() {
  override val id: String = "Mute breakpoints"
  override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("mute.breakpoints.name")

  override val message = FeatureSuggesterBundle.message("mute.breakpoints.message")
  override val suggestingActionId = "XDebugger.MuteBreakpoints"
  override val suggestingDocUrl = "https://www.jetbrains.com/help/idea/using-breakpoints.html#mute"
  override val minSuggestingIntervalDays = 30

  override val languages = listOf(Language.ANY.id)

  private val countOfRemovedBreakpointsToGetSuggestion = 3

  private object State {
    var lastBreakpointPosition: XSourcePosition? = null
    var lastPauseTimeMillis: Long = 0L
    var lastBreakpointRemovedTimeMillis: Long = 0L
    var curCountOfRemovedBreakpoints: Int = 0

    val isOutOfDate: Boolean
      get() = lastPauseTimeMillis != 0L &&
              lastBreakpointRemovedTimeMillis != 0L &&
              abs(lastBreakpointRemovedTimeMillis - lastPauseTimeMillis) > MAX_TIME_MILLIS_BETWEEN_ACTIONS

    fun applyPausedOnBreakpoint(position: XSourcePosition, timeMillis: Long) {
      lastBreakpointPosition = position
      lastPauseTimeMillis = timeMillis
      if (isOutOfDate) {
        reset()
      }
    }

    fun applyBreakpointRemoving(timeMillis: Long) {
      lastBreakpointPosition = null
      lastBreakpointRemovedTimeMillis = timeMillis
      curCountOfRemovedBreakpoints++
      if (isOutOfDate) {
        reset()
      }
    }

    fun reset() {
      lastBreakpointPosition = null
      lastPauseTimeMillis = 0L
      lastBreakpointRemovedTimeMillis = 0L
      curCountOfRemovedBreakpoints = 0
    }
  }

  override fun getSuggestion(action: Action): Suggestion {
    when (action) {
      is DebugSessionPausedAction -> {
        val breakpoint = findBreakpointOnPosition(action.project, action.position)
        if (State.lastBreakpointPosition == null && breakpoint != null) {
          State.applyPausedOnBreakpoint(action.position, action.timeMillis)
        }
        else {
          State.reset()
        }
      }
      is BreakpointRemovedAction -> {
        if (isOnTheSameLine(action.position, State.lastBreakpointPosition)) {
          State.applyBreakpointRemoving(action.timeMillis)
          if (State.curCountOfRemovedBreakpoints >= countOfRemovedBreakpointsToGetSuggestion) {
            State.reset()
            return createSuggestion()
          }
        }
        else {
          State.reset()
        }
      }
      is BreakpointAddedAction, is DebugProcessStartedAction, is DebugProcessStoppedAction -> {
        State.reset()
      }
    }
    return NoSuggestion
  }

  companion object {
    const val MAX_TIME_MILLIS_BETWEEN_ACTIONS: Long = 5000L
  }
}
