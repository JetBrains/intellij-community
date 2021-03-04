package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.XSourcePosition.isOnTheSameLine
import com.intellij.xdebugger.breakpoints.XBreakpoint
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.actions.DebugSessionPausedAction
import org.jetbrains.plugins.feature.suggester.actionsLocalSummary
import org.jetbrains.plugins.feature.suggester.createTipSuggestion
import org.jetbrains.plugins.feature.suggester.findBreakpointOnPosition
import org.jetbrains.plugins.feature.suggester.history.ChangesHistory
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory
import org.jetbrains.plugins.feature.suggester.suggesters.lang.LanguageSupport
import java.util.concurrent.TimeUnit

class EditBreakpointSuggester : FeatureSuggester {
    companion object {
        const val POPUP_MESSAGE =
            "You may edit breakpoint and make it conditional instead of waiting needed iteration. Use right click on breakpoint gutter."
        const val SUGGESTING_ACTION_ID = "com.intellij.xdebugger.impl.actions.EditBreakpointAction\$ContextAction"
        const val SUGGESTING_TIP_FILENAME = "neue-BreakpointSpeedmenu.html"
        const val NUM_OF_PAUSES_TO_GET_SUGGESTION = 8
    }

    private val actionsSummary = actionsLocalSummary()
    override lateinit var langSupport: LanguageSupport

    private val pausesOnBreakpointHistory = ChangesHistory<XSourcePosition>(NUM_OF_PAUSES_TO_GET_SUGGESTION)
    private var previousSuggestionPosition: XSourcePosition? = null

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        when (val action = actions.lastOrNull()) {
            is DebugSessionPausedAction -> {
                val breakpoint = findBreakpointOnPosition(action.project, action.position)
                if (breakpoint != null && !breakpoint.isConditional) {
                    pausesOnBreakpointHistory.add(action.position)
                } else {
                    pausesOnBreakpointHistory.clear()
                }

                if (pausesOnBreakpointHistory.isAllOnTheSameLine() &&
                    !isOnTheSameLine(pausesOnBreakpointHistory.lastOrNull(), previousSuggestionPosition)
                ) {
                    previousSuggestionPosition = pausesOnBreakpointHistory.lastOrNull()
                    pausesOnBreakpointHistory.clear()
                    return createTipSuggestion(
                        POPUP_MESSAGE,
                        suggestingActionDisplayName,
                        SUGGESTING_TIP_FILENAME
                    )
                }
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

    private val XBreakpoint<*>.isConditional
        get() = conditionExpression != null

    private fun ChangesHistory<XSourcePosition>.isAllOnTheSameLine(): Boolean {
        if (size < NUM_OF_PAUSES_TO_GET_SUGGESTION) return false
        val lastPos = get(0)
        return asIterable().all { isOnTheSameLine(it, lastPos) }
    }

    override val id: String = "Edit breakpoint"

    override val suggestingActionDisplayName: String = "Edit breakpoint"
}
