package training.featuresSuggester.listeners

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XSourcePosition
import training.featuresSuggester.actions.Action
import training.featuresSuggester.actions.BeforeDebugSessionResumedAction
import training.featuresSuggester.actions.DebugSessionPausedAction
import training.featuresSuggester.actions.DebugSessionResumedAction
import training.featuresSuggester.handleAction

class DebugSessionListener(private val session: XDebugSession) : XDebugSessionListener {

  override fun sessionPaused() = runInEdt {
    handleDebugSessionAction(::DebugSessionPausedAction)
  }

  override fun sessionResumed() = runInEdt {
    handleDebugSessionAction(::DebugSessionResumedAction)
  }

  override fun beforeSessionResume() = runInEdt {
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
