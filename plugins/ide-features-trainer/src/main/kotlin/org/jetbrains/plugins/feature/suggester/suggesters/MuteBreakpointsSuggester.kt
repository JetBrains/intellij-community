package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.XSourcePosition.isOnTheSameLine
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.BreakpointAddedAction
import org.jetbrains.plugins.feature.suggester.actions.BreakpointRemovedAction
import org.jetbrains.plugins.feature.suggester.actions.DebugProcessStartedAction
import org.jetbrains.plugins.feature.suggester.actions.DebugProcessStoppedAction
import org.jetbrains.plugins.feature.suggester.actions.DebugSessionPausedAction
import org.jetbrains.plugins.feature.suggester.actionsLocalSummary
import org.jetbrains.plugins.feature.suggester.createDocumentationSuggestion
import org.jetbrains.plugins.feature.suggester.findBreakpointOnPosition
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class MuteBreakpointsSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE =
            "You may mute all breakpoints instead of deleting them."
        const val SUGGESTING_ACTION_ID = "XDebugger.MuteBreakpoints"
        const val SUGGESTING_DOC_URL = "https://www.jetbrains.com/help/idea/using-breakpoints.html#mute"
        const val COUNT_OF_REMOVED_BREAKPOINTS_TO_GET_SUGGESTION = 3
        const val MAX_TIME_MILLIS_BETWEEN_ACTIONS: Long = 5000L
    }

    private val actionsSummary = actionsLocalSummary()
    override lateinit var langSupport: LanguageSupport

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

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        when (val action = actions.lastOrNull()) {
            is DebugSessionPausedAction -> {
                val breakpoint = findBreakpointOnPosition(action.project, action.position)
                if (State.lastBreakpointPosition == null && breakpoint != null) {
                    State.applyPausedOnBreakpoint(action.position, action.timeMillis)
                } else {
                    State.reset()
                }
            }
            is BreakpointRemovedAction -> {
                if (isOnTheSameLine(action.position, State.lastBreakpointPosition)) {
                    State.applyBreakpointRemoving(action.timeMillis)
                    if (State.curCountOfRemovedBreakpoints >= COUNT_OF_REMOVED_BREAKPOINTS_TO_GET_SUGGESTION) {
                        State.reset()
                        return createDocumentationSuggestion(
                            createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
                            suggestingActionDisplayName,
                            SUGGESTING_DOC_URL
                        )
                    }
                } else {
                    State.reset()
                }
            }
            is BreakpointAddedAction, is DebugProcessStartedAction, is DebugProcessStoppedAction -> {
                State.reset()
            }
        }
        return NoSuggestion
    }

    override fun isSuggestionNeeded(minNotificationIntervalDays: Int): Boolean {
        return super.isSuggestionNeeded(
            actionsSummary,
            SUGGESTING_ACTION_ID,
            TimeUnit.DAYS.toMillis(minNotificationIntervalDays.toLong())
        )
    }

    override val id: String = "Mute breakpoints"

    override val suggestingActionDisplayName: String = "Mute breakpoints"
}
