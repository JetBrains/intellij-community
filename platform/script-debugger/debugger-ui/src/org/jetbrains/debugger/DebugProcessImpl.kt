/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.debugger

import com.intellij.execution.ExecutionResult
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.socketConnection.ConnectionStatus
import com.intellij.xdebugger.DefaultDebugProcessHandler
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XBreakpointType
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.connection.VmConnection
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean

abstract class DebugProcessImpl<C : VmConnection<*>>(session: XDebugSession,
                                                     val connection: C,
                                                     private val editorsProvider: XDebuggerEditorsProvider,
                                                     private val smartStepIntoHandler: XSmartStepIntoHandler<*>? = null,
                                                     protected val executionResult: ExecutionResult? = null) : XDebugProcess(session) {
  protected val repeatStepInto: AtomicBoolean = AtomicBoolean()
  @Volatile var lastStep: StepAction? = null
  @Volatile protected var lastCallFrame: CallFrame? = null
  @Volatile protected var isForceStep = false
  @Volatile protected var disableDoNotStepIntoLibraries = false

  protected val urlToFileCache: ConcurrentMap<Url, VirtualFile> = ContainerUtil.newConcurrentMap<Url, VirtualFile>()

  var processBreakpointConditionsAtIdeSide: Boolean = false

  private val connectedListenerAdded = AtomicBoolean()
  private val breakpointsInitiated = AtomicBoolean()

  private val _breakpointHandlers: Array<XBreakpointHandler<*>> by lazy(LazyThreadSafetyMode.NONE) { createBreakpointHandlers() }

  init {
    connection.stateChanged {
      when (it.status) {
        ConnectionStatus.DISCONNECTED, ConnectionStatus.DETACHED -> {
          if (it.status == ConnectionStatus.DETACHED) {
            if (realProcessHandler != null) {
              // here must we must use effective process handler
              processHandler.detachProcess()
            }
          }
          getSession().stop()
        }
        ConnectionStatus.CONNECTION_FAILED -> {
          getSession().reportError(it.message)
          getSession().stop()
        }
        else -> {
          getSession().rebuildViews()
        }
      }
    }
  }

  protected final val realProcessHandler: ProcessHandler?
    get() = executionResult?.processHandler

  override final fun getSmartStepIntoHandler() = smartStepIntoHandler

  override final fun getBreakpointHandlers() = when (connection.state.status) {
    ConnectionStatus.DISCONNECTED, ConnectionStatus.DETACHED, ConnectionStatus.CONNECTION_FAILED -> XBreakpointHandler.EMPTY_ARRAY
    else -> _breakpointHandlers
  }

  override final fun getEditorsProvider() = editorsProvider

  val vm: Vm?
    get() = connection.vm

  protected abstract fun createBreakpointHandlers(): Array<XBreakpointHandler<*>>

  private fun updateLastCallFrame() {
    lastCallFrame = vm?.suspendContextManager?.context?.topFrame
  }

  override final fun checkCanPerformCommands() = vm != null

  override final fun isValuesCustomSorted() = true

  override final fun startStepOver() {
    updateLastCallFrame()
    continueVm(StepAction.OVER)
  }

  override final fun startForceStepInto() {
    isForceStep = true
    startStepInto()
  }

  override final fun startStepInto() {
    updateLastCallFrame()
    continueVm(if (vm!!.captureAsyncStackTraces) StepAction.IN_ASYNC else StepAction.IN)
  }

  override final fun startStepOut() {
    if (isVmStepOutCorrect()) {
      lastCallFrame = null
    }
    else {
      updateLastCallFrame()
    }
    continueVm(StepAction.OUT)
  }

  // some VM (firefox for example) doesn't implement step out correctly, so, we need to fix it
  protected open fun isVmStepOutCorrect() = true

  override fun resume() {
    continueVm(StepAction.CONTINUE)
  }

  /**
   * You can override this method to avoid SuspendContextManager implementation, but it is not recommended.
   */
  protected open fun continueVm(stepAction: StepAction): Promise<*>? {
    val suspendContextManager = vm!!.suspendContextManager
    if (stepAction === StepAction.CONTINUE) {
      if (suspendContextManager.context == null) {
        // on resumed we ask session to resume, and session then call our "resume", but we have already resumed, so, we don't need to send "continue" message
        return null
      }

      lastStep = null
      lastCallFrame = null
      urlToFileCache.clear()
      disableDoNotStepIntoLibraries = false
    }
    else {
      lastStep = stepAction
    }
    return suspendContextManager.continueVm(stepAction, 1)
  }

  protected fun setOverlay(context: SuspendContext<*>) {
    if (context.workerId == null) {
      vm!!.suspendContextManager.setOverlayMessage("Paused in debugger")
    }
  }

  override final fun startPausing() {
    connection.vm!!.suspendContextManager.suspend().rejected(RejectErrorReporter(session, "Cannot pause"))
  }

  override final fun getCurrentStateMessage() = connection.state.message

  override final fun getCurrentStateHyperlinkListener() = connection.state.messageLinkListener

  override fun doGetProcessHandler() = executionResult?.processHandler ?: object : DefaultDebugProcessHandler() { override fun isSilentlyDestroyOnClose() = true }

  fun saveResolvedFile(url: Url, file: VirtualFile) {
    urlToFileCache.putIfAbsent(url, file)
  }

  open fun getLocationsForBreakpoint(vm: Vm, breakpoint: XLineBreakpoint<*>): List<Location> = throw UnsupportedOperationException()

  override fun isLibraryFrameFilterSupported() = true

  // todo make final (go plugin compatibility)
  override fun checkCanInitBreakpoints(): Boolean {
    if (connection.state.status == ConnectionStatus.CONNECTED) {
      // breakpointsInitiated could be set in another thread and at this point work (init breakpoints) could be not yet performed
      return initBreakpoints(false)
    }

    if (connectedListenerAdded.compareAndSet(false, true)) {
      connection.stateChanged {
        if (it.status == ConnectionStatus.CONNECTED) {
          initBreakpoints(true)
        }
      }
    }
    return false
  }

  protected fun initBreakpoints(setBreakpoints: Boolean): Boolean {
    if (breakpointsInitiated.compareAndSet(false, true)) {
      doInitBreakpoints(setBreakpoints)
      return true
    }
    else {
      return false
    }
  }

  protected open fun doInitBreakpoints(setBreakpoints: Boolean) {
    if (setBreakpoints) {
      beforeInitBreakpoints(vm!!)
      runReadAction { session.initBreakpoints() }
    }
  }

  protected open fun beforeInitBreakpoints(vm: Vm) {
  }

  protected fun addChildVm(vm: Vm) {
    beforeInitBreakpoints(vm)

    val breakpointManager = XDebuggerManager.getInstance(session.project).breakpointManager
    @Suppress("UNCHECKED_CAST")
    for (breakpointHandler in breakpointHandlers) {
      if (breakpointHandler is LineBreakpointHandler) {
        val breakpoints = runReadAction { breakpointManager.getBreakpoints(breakpointHandler.breakpointTypeClass) }
        for (breakpoint in breakpoints) {
          breakpointHandler.manager.setBreakpoint(vm, breakpoint)
        }
      }
    }
  }
}

@Suppress("UNCHECKED_CAST")
class LineBreakpointHandler(breakpointTypeClass: Class<out XBreakpointType<out XLineBreakpoint<*>, *>>, internal val manager: LineBreakpointManager)
    : XBreakpointHandler<XLineBreakpoint<*>>(breakpointTypeClass as Class<out XBreakpointType<XLineBreakpoint<*>, *>>) {
  override fun registerBreakpoint(breakpoint: XLineBreakpoint<*>) {
    manager.setBreakpoint(manager.debugProcess.vm!!, breakpoint)
  }

  override fun unregisterBreakpoint(breakpoint: XLineBreakpoint<*>, temporary: Boolean) {
    manager.removeBreakpoint(breakpoint, temporary)
  }
}