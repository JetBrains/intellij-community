package training.featuresSuggester.listeners

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebugSessionListener
import com.intellij.xdebugger.XDebuggerManagerListener
import training.featuresSuggester.actions.Action
import training.featuresSuggester.actions.DebugProcessStartedAction
import training.featuresSuggester.actions.DebugProcessStoppedAction
import training.featuresSuggester.handleAction

class DebuggerListener(val project: Project) : XDebuggerManagerListener {
  private var curSessionListener: XDebugSessionListener? = null

  override fun processStarted(debugProcess: XDebugProcess) {
    handleDebugAction(::DebugProcessStartedAction)
  }

  override fun processStopped(debugProcess: XDebugProcess) {
    handleDebugAction(::DebugProcessStoppedAction)
  }

  private fun <T : Action> handleDebugAction(actionConstructor: (Project, Long) -> T) {
    handleAction(
      project,
      actionConstructor(project, System.currentTimeMillis())
    )
  }

  override fun currentSessionChanged(previousSession: XDebugSession?, currentSession: XDebugSession?) {
    if (previousSession != null && curSessionListener != null) {
      previousSession.removeSessionListener(curSessionListener!!)
      curSessionListener = null
    }
    if (currentSession != null) {
      curSessionListener = DebugSessionListener(currentSession)
      currentSession.addSessionListener(curSessionListener!!)
    }
  }
}
