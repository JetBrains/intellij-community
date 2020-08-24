package org.jetbrains.plugins.feature.suggester.suggesters

import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.BreakpointAddedAction
import org.jetbrains.plugins.feature.suggester.actions.BreakpointRemovedAction
import org.jetbrains.plugins.feature.suggester.actions.DebugSessionPausedAction
import org.jetbrains.plugins.feature.suggester.actions.EditorFocusGainedAction
import org.jetbrains.plugins.feature.suggester.actionsLocalSummary
import org.jetbrains.plugins.feature.suggester.createDocumentationSuggestion
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.FeatureSuggester.Companion.createMessageWithShortcut
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport
import java.util.concurrent.TimeUnit

class RunToCursorSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE = "You may use run to cursor instead of placing and removing breakpoints."
        const val SUGGESTING_ACTION_ID = "RunToCursor"
        const val SUGGESTING_DOC_URL =
            "https://www.jetbrains.com/help/idea/stepping-through-the-program.html#run-to-cursor"
    }

    private class DebuggingData {
        var addedBreakpointLine: Int = -1
        var isPausedOnBreakpoint: Boolean = false

        val isBreakpointAdded: Boolean
            get() = addedBreakpointLine != -1
    }

    private val actionsSummary = actionsLocalSummary()
    override lateinit var langSupport: LanguageSupport

    private var debuggingData: DebuggingData? = null

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        when (val action = actions.lastOrNull()) {
            is EditorFocusGainedAction -> return NoSuggestion
            is DebugSessionPausedAction -> {
                if (debuggingData == null || !debuggingData!!.isBreakpointAdded) {
                    debuggingData = DebuggingData()
                } else if (action.curLine == debuggingData!!.addedBreakpointLine) {
                    debuggingData!!.isPausedOnBreakpoint = true
                } else {
                    reset()
                }
            }
            is BreakpointAddedAction -> {
                if (debuggingData == null) return NoSuggestion
                if (!debuggingData!!.isBreakpointAdded) {
                    val line = action.line ?: return NoSuggestion
                    debuggingData!!.addedBreakpointLine = line
                } else {
                    reset()
                }
            }
            is BreakpointRemovedAction -> {
                if (debuggingData?.isPausedOnBreakpoint == true) {
                    val line = action.line ?: return NoSuggestion
                    if (line == debuggingData!!.addedBreakpointLine) {
                        reset()
                        return createDocumentationSuggestion(
                            createMessageWithShortcut(SUGGESTING_ACTION_ID, POPUP_MESSAGE),
                            suggestingActionDisplayName,
                            SUGGESTING_DOC_URL
                        )
                    }
                }
                reset()
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

    private fun reset() {
        debuggingData = null
    }

    override val id: String = "Run to cursor"

    override val suggestingActionDisplayName: String = "Run to cursor"
}