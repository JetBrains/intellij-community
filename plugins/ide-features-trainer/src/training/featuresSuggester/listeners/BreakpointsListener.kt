package training.featuresSuggester.listeners

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointListener
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import training.featuresSuggester.actions.Action
import training.featuresSuggester.actions.BreakpointAddedAction
import training.featuresSuggester.actions.BreakpointChangedAction
import training.featuresSuggester.actions.BreakpointRemovedAction
import training.featuresSuggester.handleAction

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
    if (breakpoint.type is XLineBreakpointType<*>) {
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
}
