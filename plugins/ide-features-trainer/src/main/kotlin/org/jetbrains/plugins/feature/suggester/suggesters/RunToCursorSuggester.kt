package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.XSourcePosition.isOnTheSameLine
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.BreakpointAddedAction
import org.jetbrains.plugins.feature.suggester.actions.BreakpointRemovedAction
import org.jetbrains.plugins.feature.suggester.actions.DebugSessionPausedAction
import org.jetbrains.plugins.feature.suggester.actionsLocalSummary
import org.jetbrains.plugins.feature.suggester.createDocumentationSuggestion
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport

class RunToCursorSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "You may use run to cursor instead of placing and removing breakpoints."
        const val SUGGESTING_ACTION_ID = "RunToCursor"
        const val SUGGESTING_DOC_URL =
            "https://www.jetbrains.com/help/idea/stepping-through-the-program.html#run-to-cursor"
        const val MAX_TIME_MILLIS_BETWEEN_ACTIONS: Long = 5000L
    }

    private object State {
        var debugSessionPaused: Boolean = false
        var addedBreakpointPosition: XSourcePosition? = null
        var breakpointAddedTimeMillis: Long = 0L
        var isPausedOnBreakpoint: Boolean = false

        val isBreakpointAdded: Boolean
            get() = addedBreakpointPosition != null

        fun isOutOfDate(breakpointRemovedTimeMillis: Long): Boolean {
            return breakpointRemovedTimeMillis - breakpointAddedTimeMillis > MAX_TIME_MILLIS_BETWEEN_ACTIONS
        }

        fun reset() {
            debugSessionPaused = false
            addedBreakpointPosition = null
            breakpointAddedTimeMillis = 0L
            isPausedOnBreakpoint = false
        }
    }

    @Suppress("UnusedPrivateMember")
    private val actionsSummary = actionsLocalSummary()
    override lateinit var langSupport: LanguageSupport

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        when (val action = actions.lastOrNull()) {
            is DebugSessionPausedAction -> {
                if (State.debugSessionPaused && isOnTheSameLine(action.position, State.addedBreakpointPosition)) {
                    State.isPausedOnBreakpoint = true
                } else {
                    State.reset()
                    State.debugSessionPaused = true
                }
            }
            is BreakpointAddedAction -> {
                if (!State.isBreakpointAdded) {
                    State.apply {
                        addedBreakpointPosition = action.position
                        breakpointAddedTimeMillis = action.timeMillis
                    }
                } else {
                    State.reset()
                }
            }
            is BreakpointRemovedAction -> {
                if (State.isPausedOnBreakpoint &&
                    isOnTheSameLine(action.position, State.addedBreakpointPosition) &&
                    !State.isOutOfDate(action.timeMillis)
                ) {
                    State.reset()
                    return createDocumentationSuggestion(
                        createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
                        suggestingActionDisplayName,
                        SUGGESTING_DOC_URL
                    )
                }
                State.reset()
            }
        }

        return NoSuggestion
    }

    override fun isSuggestionNeeded(minNotificationIntervalDays: Int): Boolean {
//        return super.isSuggestionNeeded(
//            actionsSummary,
//            SUGGESTING_ACTION_ID,
//            TimeUnit.DAYS.toMillis(minNotificationIntervalDays.toLong())
//        )
        return false // todo: edit this method when RunToCursor action statistics will be fixed.
    }

    override val id: String = "Run to cursor"

    override val suggestingActionDisplayName: String = "Run to cursor"
}
