package org.jetbrains.plugins.feature.suggester.listeners

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import org.jetbrains.plugins.feature.suggester.actions.Action
import org.jetbrains.plugins.feature.suggester.actions.BreakpointAddedAction
import org.jetbrains.plugins.feature.suggester.actions.BreakpointChangedAction
import org.jetbrains.plugins.feature.suggester.actions.BreakpointRemovedAction
import org.jetbrains.plugins.feature.suggester.handleAction

class BreakpointsListener(val project: Project) : XBreakpointListener<XBreakpoint<*>> {

    override fun breakpointAdded(breakpoint: XBreakpoint<*>) {
        handleBreakpointAction(breakpoint, ::BreakpointAddedAction)
    }

    override fun breakpointRemoved(breakpoint: XBreakpoint<*>) {
        handleBreakpointAction(breakpoint, ::BreakpointRemovedAction)
    }

    override fun breakpointChanged(breakpoint: XBreakpoint<*>) {
        handleBreakpointAction(breakpoint, ::BreakpointChangedAction)
    }

    private fun <T : Action> handleBreakpointAction(
        breakpoint: XBreakpoint<*>,
        actionConstructor: (XBreakpoint<*>, Project, Long) -> T
    ) {
        handleAction(
            project,
            actionConstructor(
                breakpoint,
                project,
                System.currentTimeMillis()
            )
        )
    }
}
