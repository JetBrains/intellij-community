// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.connection.RemoteVmConnection
import org.jetbrains.debugger.connection.VmConnection
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.event.HyperlinkListener

interface MultiVmDebugProcess {
  val mainVm: Vm?
  val activeOrMainVm: Vm?
  val collectVMs: List<Vm>
    get() {
      val mainVm = mainVm ?: return emptyList()
      val result = mutableListOf<Vm>()
      fun addRecursively(vm: Vm) {
        if (vm.attachStateManager.isAttached) {
          result.add(vm)
          vm.childVMs.forEach { addRecursively(it) }
        }
      }
      addRecursively(mainVm)
      return result
    }
}

abstract class DebugProcessImpl<out C : VmConnection<*>>(session: XDebugSession,
                                                         val connection: C,
                                                         private val editorsProvider: XDebuggerEditorsProvider,
                                                         private val smartStepIntoHandler: XSmartStepIntoHandler<*>? = null,
                                                         protected val executionResult: ExecutionResult? = null) : XDebugProcess(session), MultiVmDebugProcess {
  protected val repeatStepInto: AtomicBoolean = AtomicBoolean()
  @Volatile var lastStep: StepAction? = null
  @Volatile protected var lastCallFrame: CallFrame? = null
  @Volatile protected var isForceStep: Boolean = false
  @Volatile protected var disableDoNotStepIntoLibraries: Boolean = false

  protected val urlToFileCache: ConcurrentMap<Url, VirtualFile> = ContainerUtil.newConcurrentMap<Url, VirtualFile>()

  var processBreakpointConditionsAtIdeSide: Boolean = false

  private val connectedListenerAdded = AtomicBoolean()
  private val breakpointsInitiated = AtomicBoolean()

  private val _breakpointHandlers: Array<XBreakpointHandler<*>> by lazy(LazyThreadSafetyMode.NONE) { createBreakpointHandlers() }

  protected val realProcessHandler: ProcessHandler?
    get() = executionResult?.processHandler

  override final fun getSmartStepIntoHandler(): XSmartStepIntoHandler<*>? = smartStepIntoHandler

  override final fun getBreakpointHandlers(): Array<out XBreakpointHandler<*>> = when (connection.state.status) {
    ConnectionStatus.DISCONNECTED, ConnectionStatus.DETACHED, ConnectionStatus.CONNECTION_FAILED -> XBreakpointHandler.EMPTY_ARRAY
    else -> _breakpointHandlers
  }

  override final fun getEditorsProvider(): XDebuggerEditorsProvider = editorsProvider

  val vm: Vm?
    get() = connection.vm

  override final val mainVm: Vm?
    get() = connection.vm

  override final val activeOrMainVm: Vm?
    get() = (session.suspendContext?.activeExecutionStack as? ExecutionStackView)?.suspendContext?.vm ?: mainVm

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

        else -> getSession().rebuildViews()
      }
    }
  }

  protected abstract fun createBreakpointHandlers(): Array<XBreakpointHandler<*>>

  private fun updateLastCallFrame(vm: Vm) {
    lastCallFrame = vm.suspendContextManager.context?.topFrame
  }

  override final fun checkCanPerformCommands(): Boolean = activeOrMainVm != null

  override final fun isValuesCustomSorted(): Boolean = true

  override final fun startStepOver(context: XSuspendContext?) {
    val vm = context.vm
    updateLastCallFrame(vm)
    continueVm(vm, StepAction.OVER)
  }

  val XSuspendContext?.vm: Vm
    get() = (this as? SuspendContextView)?.activeVm ?: mainVm!!

  override final fun startForceStepInto(context: XSuspendContext?) {
    isForceStep = true
    startStepInto(context)
  }

  override final fun startStepInto(context: XSuspendContext?) {
    val vm = context.vm
    updateLastCallFrame(vm)
    continueVm(vm, StepAction.IN)
  }

  override final fun startStepOut(context: XSuspendContext?) {
    val vm = context.vm
    if (isVmStepOutCorrect()) {
      lastCallFrame = null
    }
    else {
      updateLastCallFrame(vm)
    }
    continueVm(vm, StepAction.OUT)
  }

  // some VM (firefox for example) doesn't implement step out correctly, so, we need to fix it
  protected open fun isVmStepOutCorrect(): Boolean = true

  override fun resume(context: XSuspendContext?) {
    continueVm(context.vm, StepAction.CONTINUE)
  }

  open fun resume(vm: Vm) {
    continueVm(vm, StepAction.CONTINUE)
  }

  @Suppress("unused")
  @Deprecated("Pass vm explicitly", ReplaceWith("continueVm(vm!!, stepAction)"))
  protected open fun continueVm(stepAction: StepAction): Promise<*>? = continueVm(activeOrMainVm!!, stepAction)

  /**
   * You can override this method to avoid SuspendContextManager implementation, but it is not recommended.
   */
  protected open fun continueVm(vm: Vm, stepAction: StepAction): Promise<*>? {
    val suspendContextManager = vm.suspendContextManager
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
    return suspendContextManager.continueVm(stepAction)
  }

  protected fun setOverlay(context: SuspendContext<*>) {
    val vm = mainVm
    if (context.vm == vm) {
      vm.suspendContextManager.setOverlayMessage("Paused in debugger")
    }
  }

  override final fun startPausing() {
    activeOrMainVm!!.suspendContextManager.suspend()
      .onError(RejectErrorReporter(session, "Cannot pause"))
  }

  override final fun getCurrentStateMessage(): String = connection.state.message

  override final fun getCurrentStateHyperlinkListener(): HyperlinkListener? = connection.state.messageLinkListener

  override fun doGetProcessHandler(): ProcessHandler = executionResult?.processHandler ?: object : DefaultDebugProcessHandler() { override fun isSilentlyDestroyOnClose() = true }

  fun saveResolvedFile(url: Url, file: VirtualFile) {
    urlToFileCache.putIfAbsent(url, file)
  }

  // go plugin compatibility
  @Suppress("unused")
  open fun getLocationsForBreakpoint(breakpoint: XLineBreakpoint<*>): List<Location> = getLocationsForBreakpoint(activeOrMainVm!!, breakpoint)

  open fun getLocationsForBreakpoint(vm: Vm, breakpoint: XLineBreakpoint<*>): List<Location> = throw UnsupportedOperationException()

  override fun isLibraryFrameFilterSupported(): Boolean = true

  // todo make final (go plugin compatibility)
  override fun checkCanInitBreakpoints(): Boolean {
    if (connection.state.status == ConnectionStatus.CONNECTED) {
      return true
    }

    if (connectedListenerAdded.compareAndSet(false, true)) {
      connection.stateChanged {
        if (it.status == ConnectionStatus.CONNECTED) {
          initBreakpoints()
        }
      }
    }
    return false
  }

  protected fun initBreakpoints() {
    if (breakpointsInitiated.compareAndSet(false, true)) {
      doInitBreakpoints()
    }
  }

  protected open fun doInitBreakpoints() {
    mainVm?.let(::beforeInitBreakpoints)
    runReadAction { session.initBreakpoints() }
  }

  protected open fun beforeInitBreakpoints(vm: Vm) {
  }

  protected fun addChildVm(vm: Vm, childConnection: RemoteVmConnection<*>) {
    mainVm?.childVMs?.add(vm)
    childConnection.stateChanged {
      if (it.status == ConnectionStatus.CONNECTION_FAILED || it.status == ConnectionStatus.DISCONNECTED || it.status == ConnectionStatus.DETACHED) {
        mainVm?.childVMs?.remove(vm)
      }
    }

    mainVm?.debugListener?.childVmAdded(vm)
  }
}
