package org.jetbrains.plugins.feature.suggester.listeners

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XSourcePosition
import org.jetbrains.plugins.feature.suggester.actions.Action
import org.jetbrains.plugins.feature.suggester.actions.BeforeDebugSessionResumedAction
import org.jetbrains.plugins.feature.suggester.actions.DebugSessionPausedAction
import org.jetbrains.plugins.feature.suggester.actions.DebugSessionResumedAction
import org.jetbrains.plugins.feature.suggester.handleAction

class DebugSessionListener(private val session: XDebugSession) : XDebugSessionListener {

    override fun sessionPaused() {
        handleDebugSessionAction(::DebugSessionPausedAction)
    }

    override fun sessionResumed() {
        handleDebugSessionAction(::DebugSessionResumedAction)
    }

    override fun beforeSessionResume() {
        handleDebugSessionAction(::BeforeDebugSessionResumedAction)
    }

    private fun <T : Action> handleDebugSessionAction(actionConstructor: (XSourcePosition, Project, Long) -> T) {
        val currentPosition = session.currentPosition ?: return
        handleAction(
            session.project,
            actionConstructor(
                currentPosition,
                session.project,
                System.currentTimeMillis()
            )
        )
    }
}