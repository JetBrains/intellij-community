// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger

import com.intellij.execution.DefaultExecutionResult
import com.intellij.execution.ExecutionResult
import com.intellij.execution.process.ProcessHandler
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.ui.MessageType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.BrowserHyperlinkListener
import com.intellij.util.Url
import com.intellij.util.io.socketConnection.ConnectionState
import com.intellij.util.io.socketConnection.ConnectionStatus
import com.intellij.xdebugger.DefaultDebugProcessHandler
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.connection.RemoteVmConnection
import org.jetbrains.debugger.connection.VmConnection
import java.util.concurrent.ConcurrentHashMap
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
  @Volatile var lastStep: StepAction? = null
  @Volatile protected var lastCallFrame: CallFrame? = null
  @Volatile protected var isForceStep: Boolean = false
  @Volatile protected var disableDoNotStepIntoLibraries: Boolean = false

  // todo: file resolving: check that urlToFileCache still needed
  protected val urlToFileCache: ConcurrentMap<Url, VirtualFile> = ConcurrentHashMap()

  private val connectedListenerAdded = AtomicBoolean()
  private val breakpointsInitiated = AtomicBoolean()

  private val _breakpointHandlers: Array<XBreakpointHandler<*>> by lazy(LazyThreadSafetyMode.NONE) { createBreakpointHandlers() }

  protected val realProcessHandler: ProcessHandler?
    get() = executionResult?.processHandler

  final override fun getSmartStepIntoHandler(): XSmartStepIntoHandler<*>? = smartStepIntoHandler

  final override fun getBreakpointHandlers(): Array<out XBreakpointHandler<*>> = when (connection.state.status) {
    ConnectionStatus.DISCONNECTED, ConnectionStatus.DETACHED, ConnectionStatus.CONNECTION_FAILED -> XBreakpointHandler.EMPTY_ARRAY
    else -> _breakpointHandlers
  }

  final override fun getEditorsProvider(): XDebuggerEditorsProvider = editorsProvider

  val vm: Vm?
    get() = connection.vm

  final override val mainVm: Vm?
    get() = connection.vm

  final override val activeOrMainVm: Vm?
    get() = (session.suspendContext?.activeExecutionStack as? ExecutionStackView)?.suspendContext?.vm ?: mainVm

  val childConnections: MutableList<VmConnection<*>> = mutableListOf()

  init {
    if (session is XDebugSessionImpl && executionResult is DefaultExecutionResult) {
      session.addRestartActions(*executionResult.restartActions)
    }
    connection.stateChanged {
      handleConnectionStateChanged(it)
    }
  }

  protected open fun handleConnectionStateChanged(state: ConnectionState) {
    when (state.status) {
      ConnectionStatus.DISCONNECTED, ConnectionStatus.DETACHED -> {
        if (state.status == ConnectionStatus.DETACHED) {
          if (realProcessHandler != null) {
            // here must we must use effective process handler
            processHandler.detachProcess()
          }
        }
        session.stop()
      }

      ConnectionStatus.CONNECTION_FAILED -> {
        session.reportMessage(state.message, MessageType.ERROR, BrowserHyperlinkListener.INSTANCE)
        session.stop()
      }

      else -> session.rebuildViews()
    }
  }

  protected abstract fun createBreakpointHandlers(): Array<XBreakpointHandler<*>>

  private fun updateLastCallFrame(vm: Vm) {
    lastCallFrame = vm.suspendContextManager.context?.topFrame
  }

  final override fun checkCanPerformCommands(): Boolean = activeOrMainVm != null

  override fun isValuesCustomSorted(): Boolean = true

  final override fun startStepOver(context: XSuspendContext?) {
    val vm = context.vm
    updateLastCallFrame(vm)
    continueVm(vm, StepAction.OVER)
  }

  val XSuspendContext?.vm: Vm
    get() = (this as? SuspendContextView)?.activeVm ?: mainVm!!

  final override fun startForceStepInto(context: XSuspendContext?) {
    isForceStep = true
    enableBlackboxing(false, context.vm)
    startStepInto(context)
  }

  final override fun startStepInto(context: XSuspendContext?) {
    val vm = context.vm
    updateLastCallFrame(vm)
    continueVm(vm, StepAction.IN)
  }

  final override fun startStepOut(context: XSuspendContext?) {
    val vm = context.vm
    updateLastCallFrame(vm)
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
      enableBlackboxing(true, vm)
    }
    else {
      lastStep = stepAction
    }
    return suspendContextManager.continueVm(stepAction)
  }

  protected open fun enableBlackboxing(state: Boolean, vm: Vm) {
    disableDoNotStepIntoLibraries = !state
  }

  protected fun setOverlay(context: SuspendContext<*>) {
    val vm = mainVm
    if (context.vm == vm) {
      vm.suspendContextManager.setOverlayMessage(ScriptDebuggerBundle.message("notification.content.paused.in.debugger"))
    }
  }

  final override fun startPausing() {
    activeOrMainVm!!.suspendContextManager.suspend()
      .onError(RejectErrorReporter(session, ScriptDebuggerBundle.message("notification.content.cannot.pause")))
  }

  final override fun getCurrentStateMessage(): String = connection.state.message

  final override fun getCurrentStateHyperlinkListener(): HyperlinkListener? = connection.state.messageLinkListener

  override fun doGetProcessHandler(): ProcessHandler = executionResult?.processHandler ?: object : DefaultDebugProcessHandler() { override fun isSilentlyDestroyOnClose() = true }

  fun saveResolvedFile(url: Url, file: VirtualFile) {
    urlToFileCache.putIfAbsent(url, file)
  }

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
        childConnections.remove(childConnection)
      }
    }
    childConnections.add(childConnection)
    mainVm?.debugListener?.childVmAdded(vm)
  }
}
