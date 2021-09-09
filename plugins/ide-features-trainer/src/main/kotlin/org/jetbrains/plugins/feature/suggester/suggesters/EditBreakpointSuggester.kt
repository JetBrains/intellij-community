package org.jetbrains.plugins.feature.suggester.suggesters

import com.intellij.lang.Language
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.XSourcePosition.isOnTheSameLine
import org.jetbrains.plugins.feature.suggester.FeatureSuggesterBundle
import org.jetbrains.plugins.feature.suggester.NoSuggestion
import org.jetbrains.plugins.feature.suggester.Suggestion
import org.jetbrains.plugins.feature.suggester.TipSuggestion
import org.jetbrains.plugins.feature.suggester.actions.DebugSessionPausedAction
import org.jetbrains.plugins.feature.suggester.findBreakpointOnPosition
import org.jetbrains.plugins.feature.suggester.history.ChangesHistory
import org.jetbrains.plugins.feature.suggester.history.UserActionsHistory

class EditBreakpointSuggester : AbstractFeatureSuggester() {
    override val id: String = "Edit breakpoint"
    override val suggestingActionDisplayName: String = FeatureSuggesterBundle.message("edit.breakpoint.name")

    override val message = FeatureSuggesterBundle.message("edit.breakpoint.message")
    override val suggestingActionId = "com.intellij.xdebugger.impl.actions.EditBreakpointAction\$ContextAction"
    override val suggestingTipFileName = "BreakpointSpeedmenu.html"

    override val languages = listOf(Language.ANY.id)

    private val numOfPausesToGetSuggestion = 8
    private val pausesOnBreakpointHistory = ChangesHistory<XSourcePosition>(numOfPausesToGetSuggestion)
    private var previousSuggestionPosition: XSourcePosition? = null

    override fun getSuggestion(actions: UserActionsHistory): Suggestion {
        when (val action = actions.lastOrNull()) {
            is DebugSessionPausedAction -> {
                val breakpoint = findBreakpointOnPosition(action.project, action.position)
                if (breakpoint != null && breakpoint.conditionExpression == null) {
                    pausesOnBreakpointHistory.add(action.position)
                } else {
                    pausesOnBreakpointHistory.clear()
                }

                if (pausesOnBreakpointHistory.isAllOnTheSameLine() &&
                    !isOnTheSameLine(pausesOnBreakpointHistory.lastOrNull(), previousSuggestionPosition)
                ) {
                    previousSuggestionPosition = pausesOnBreakpointHistory.lastOrNull()
                    pausesOnBreakpointHistory.clear()
                    return TipSuggestion(message, id, suggestingTipFileName)
                }
            }
        }
        return NoSuggestion
    }

    private fun ChangesHistory<XSourcePosition>.isAllOnTheSameLine(): Boolean {
        if (size < numOfPausesToGetSuggestion) return false
        val lastPos = get(0)
        return asIterable().all { isOnTheSameLine(it, lastPos) }
    }
}
