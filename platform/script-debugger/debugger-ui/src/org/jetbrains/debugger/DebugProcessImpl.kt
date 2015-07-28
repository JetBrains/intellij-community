/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Url
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.socketConnection.ConnectionStatus
import com.intellij.util.io.socketConnection.SocketConnectionListener
import com.intellij.xdebugger.DefaultDebugProcessHandler
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.breakpoints.XBreakpoint
import com.intellij.xdebugger.breakpoints.XBreakpointHandler
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.stepping.XSmartStepIntoHandler
import org.jetbrains.debugger.connection.VmConnection
import org.jetbrains.debugger.frame.SuspendContextImpl
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.event.HyperlinkListener
import kotlin.properties.Delegates

public abstract class DebugProcessImpl<C : VmConnection<*>>(session: XDebugSession,
                                                             public val connection: C,
                                                             private val editorsProvider: XDebuggerEditorsProvider,
                                                             private val smartStepIntoHandler: XSmartStepIntoHandler<*>?,
                                                             protected val executionResult: ExecutionResult?) : XDebugProcess(session) {
  protected val repeatStepInto: AtomicBoolean = AtomicBoolean()
  volatile protected var lastStep: StepAction? = null
  volatile protected var lastCallFrame: CallFrame? = null
  volatile protected var isForceStep: Boolean = false
  volatile protected var disableDoNotStepIntoLibraries: Boolean = false
    private set

  protected val urlToFileCache: ConcurrentMap<Url, VirtualFile> = ContainerUtil.newConcurrentMap<Url, VirtualFile>()

  private var processBreakpointConditionsAtIdeSide = false

  private val _breakpointHandlers: Array<XBreakpointHandler<*>> by Delegates.lazy { createBreakpointHandlers() }

  init {
    connection.addListener(object : SocketConnectionListener {
      override fun statusChanged(status: ConnectionStatus) {
        if (status === ConnectionStatus.DISCONNECTED || status === ConnectionStatus.DETACHED) {
          if (status === ConnectionStatus.DETACHED) {
            if (getRealProcessHandler() != null) {
              // here must we must use effective process handler
              getProcessHandler().detachProcess()
            }
          }
          getSession().stop()
        }
        else {
          getSession().rebuildViews()
        }
      }
    })
  }

  protected final fun getRealProcessHandler(): ProcessHandler? = executionResult?.getProcessHandler()

  override final fun getSmartStepIntoHandler() = smartStepIntoHandler

  override final fun getBreakpointHandlers() = _breakpointHandlers

  override final fun getEditorsProvider() = editorsProvider

  public fun setProcessBreakpointConditionsAtIdeSide(value: Boolean) {
    processBreakpointConditionsAtIdeSide = value
  }

  public fun getVm(): Vm? = connection.getVm()

  protected abstract fun createBreakpointHandlers(): Array<XBreakpointHandler<*>>

  private fun updateLastCallFrame() {
    lastCallFrame = getVm()?.getSuspendContextManager()?.getContext()?.getTopFrame()
  }

  override final fun checkCanPerformCommands() = getVm() != null

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
    continueVm(StepAction.IN)
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
  protected open fun isVmStepOutCorrect(): Boolean = true

  override final fun resume() {
    continueVm(StepAction.CONTINUE)
  }

  protected final fun continueVm(stepAction: StepAction) {
    val suspendContextManager = getVm()!!.getSuspendContextManager()
    if (stepAction === StepAction.CONTINUE) {
      if (suspendContextManager.getContext() == null) {
        // on resumed we ask session to resume, and session then call our "resume", but we have already resumed, so, we don't need to send "continue" message
        return
      }

      lastStep = null
      lastCallFrame = null
      urlToFileCache.clear()
      disableDoNotStepIntoLibraries = false
    }
    else {
      lastStep = stepAction
    }
    suspendContextManager.continueVm(stepAction, 1)
  }

  protected final fun setOverlay() {
    getVm()!!.getSuspendContextManager().setOverlayMessage("Paused in debugger")
  }

  protected final fun processBreakpoint(suspendContext: SuspendContext, breakpoint: XBreakpoint<*>, xSuspendContext: SuspendContextImpl) {
    val condition = breakpoint.getConditionExpression()?.getExpression()
    if (!processBreakpointConditionsAtIdeSide || condition == null) {
      processBreakpointLogExpressionAndSuspend(breakpoint, xSuspendContext, suspendContext)
    }
    else {
      xSuspendContext.evaluateExpression(condition)
        .done(object : ContextDependentAsyncResultConsumer<String>(suspendContext) {
          override fun consume(evaluationResult: String, vm: Vm) {
            if ("false" == evaluationResult) {
              resume()
            }
            else {
              processBreakpointLogExpressionAndSuspend(breakpoint, xSuspendContext, suspendContext)
            }
          }
        })
        .rejected(object : ContextDependentAsyncResultConsumer<Throwable>(suspendContext) {
          override fun consume(failure: Throwable, vm: Vm) {
            processBreakpointLogExpressionAndSuspend(breakpoint, xSuspendContext, suspendContext)
          }
        })
    }
  }

  private fun processBreakpointLogExpressionAndSuspend(breakpoint: XBreakpoint<*>, xSuspendContext: SuspendContextImpl, suspendContext: SuspendContext) {
    val logExpression = breakpoint.getLogExpressionObject()?.getExpression()
    if (logExpression == null) {
      breakpointReached(breakpoint, null, xSuspendContext)
    }
    else {
      xSuspendContext.evaluateExpression(logExpression)
        .done(object : ContextDependentAsyncResultConsumer<String>(suspendContext) {
          override fun consume(logResult: String, vm: Vm) {
            breakpointReached(breakpoint, logResult, xSuspendContext)
          }
        })
        .rejected(object : ContextDependentAsyncResultConsumer<Throwable>(suspendContext) {
          override fun consume(logResult: Throwable, vm: Vm) {
            breakpointReached(breakpoint, "Failed to evaluate expression: " + logExpression, xSuspendContext)
          }
        })
    }
  }

  private fun breakpointReached(breakpoint: XBreakpoint<*>, evaluatedLogExpression: String?, suspendContext: XSuspendContext) {
    if (getSession().breakpointReached(breakpoint, evaluatedLogExpression, suspendContext)) {
      setOverlay()
    }
    else {
      resume()
    }
  }

  override final fun startPausing() {
    connection.getVm().getSuspendContextManager().suspend().rejected(RejectErrorReporter(getSession(), "Cannot pause"))
  }

  override final fun getCurrentStateMessage() = connection.getState().getMessage()

  override final fun getCurrentStateHyperlinkListener() = connection.getState().getMessageLinkListener()

  override fun doGetProcessHandler() = executionResult?.getProcessHandler() ?: object : DefaultDebugProcessHandler() { override fun isSilentlyDestroyOnClose() = true }

  public fun saveResolvedFile(url: Url, file: VirtualFile) {
    urlToFileCache.putIfAbsent(url, file)
  }
}