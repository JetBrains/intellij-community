package training.featuresSuggester.actions

import com.intellij.lang.Language
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XBreakpoint

sealed class DebugAction : Action() {
  override val language: Language
    get() = Language.ANY
}

// -------------------------------------Debug Process Actions-----------------------------------------------------------
data class DebugProcessStartedAction(
  override val project: Project,
  override val timeMillis: Long
) : DebugAction()

data class DebugProcessStoppedAction(
  override val project: Project,
  override val timeMillis: Long
) : DebugAction()

// -------------------------------------Debug Session Actions-------------------------------------
abstract class DebugSessionAction : DebugAction() {
  abstract val position: XSourcePosition

  val curLine: Int
    get() = position.line

  val curOffset: Int
    get() = position.offset

  val curFile: VirtualFile
    get() = position.file
}

data class DebugSessionPausedAction(
  override val position: XSourcePosition,
  override val project: Project,
  override val timeMillis: Long
) : DebugSessionAction()

data class DebugSessionResumedAction(
  override val position: XSourcePosition,
  override val project: Project,
  override val timeMillis: Long
) : DebugSessionAction()

data class BeforeDebugSessionResumedAction(
  override val position: XSourcePosition,
  override val project: Project,
  override val timeMillis: Long
) : DebugSessionAction()

// -------------------------------------Breakpoint Actions--------------------------------------------------------------
abstract class BreakpointAction : DebugAction() {
  abstract val breakpoint: XBreakpoint<*>

  val position: XSourcePosition?
    get() = breakpoint.sourcePosition
}

data class BreakpointAddedAction(
  override val breakpoint: XBreakpoint<*>,
  override val project: Project,
  override val timeMillis: Long
) : BreakpointAction()

data class BreakpointRemovedAction(
  override val breakpoint: XBreakpoint<*>,
  override val project: Project,
  override val timeMillis: Long
) : BreakpointAction()

data class BreakpointChangedAction(
  override val breakpoint: XBreakpoint<*>,
  override val project: Project,
  override val timeMillis: Long
) : BreakpointAction()
