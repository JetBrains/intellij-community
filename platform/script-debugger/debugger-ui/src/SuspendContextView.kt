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

import com.intellij.icons.AllIcons
import com.intellij.openapi.diagnostic.logger
import com.intellij.ui.ColoredTextContainer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.frame.XExecutionStack
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.frame.XSuspendContext
import com.intellij.xdebugger.impl.frame.XDebuggerFramesList
import com.intellij.xdebugger.settings.XDebuggerSettingsManager
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise
import org.jetbrains.concurrency.resolvedPromise
import org.jetbrains.debugger.frame.CallFrameView
import org.jetbrains.debugger.values.StringValue
import java.awt.Color
import java.util.*

/**
 * Debugging several VMs simultaneously should be similar to debugging multi-threaded Java application when breakpoints suspend only one thread.
 * 1. When thread is paused and another thread reaches breakpoint, show notification about it with possibility to switch thread.
 * 2. Stepping and releasing affects only current thread.
 * 3. When another thread is selected in Frames view, it changes its icon from (0) to (v) and becomes current, i.e. step/release commands
 *    are applied to it.
 * 4. Stepping/releasing updates current thread icon and clears frame, but doesn't switch thread. To release other threads, user needs to
 *    select them firstly.
 */
abstract class SuspendContextView(protected val debugProcess: MultiVmDebugProcess,
                                  activeStack: ExecutionStackView,
                                  @Volatile var activeVm: Vm)
  : XSuspendContext() {

  private val stacks: MutableMap<Vm, ScriptExecutionStack> = Collections.synchronizedMap(LinkedHashMap<Vm, ScriptExecutionStack>())

  init {
    val mainVm = debugProcess.mainVm
    val vmList = debugProcess.collectVMs
    if (mainVm != null && !vmList.isEmpty()) {
      // main vm should go first
      vmList.forEach {
        val context = it.suspendContextManager.context

        val stack: ScriptExecutionStack =
          if (context == null) {
            RunningThreadExecutionStackView(it)
          }
          else if (context == activeStack.suspendContext) {
            activeStack
          }
          else {
            logger<SuspendContextView>().error("Paused VM was lost.")
            InactiveAtBreakpointExecutionStackView(it)
          }
        stacks[it] = stack
      }
    }
    else {
      stacks[activeVm] = activeStack
    }
  }

  override fun getActiveExecutionStack() = stacks[activeVm]

  override fun getExecutionStacks(): Array<out XExecutionStack> = stacks.values.toTypedArray()

  fun evaluateExpression(expression: String): Promise<String> {
    val activeStack = stacks[activeVm]!!
    val frame = activeStack.topFrame ?: return rejectedPromise("Top frame is null")
    if (frame !is CallFrameView) return rejectedPromise("Can't evaluate on non-paused thread")
    return evaluateExpression(frame.callFrame.evaluateContext, expression)
  }

  private fun evaluateExpression(evaluateContext: EvaluateContext, expression: String) = evaluateContext.evaluate(expression)
      .thenAsync {
        val value = it.value
        if (value is StringValue && value.isTruncated) {
          value.fullString
        }
        else {
          resolvedPromise(value.valueString!!)
        }
      }

  fun pauseInactiveThread(inactiveThread: ExecutionStackView) {
    stacks[inactiveThread.vm] = inactiveThread
  }

  fun hasPausedThreads(): Boolean {
    return stacks.values.any { it is ExecutionStackView }
  }

  fun resume(vm: Vm) {
    val prevStack = stacks[vm]
    if (prevStack is ExecutionStackView) {
      stacks[vm] = RunningThreadExecutionStackView(prevStack.vm)
    }
  }

  fun resumeCurrentThread() {
    resume(activeVm)
  }

  fun setActiveThread(selectedStackFrame: XStackFrame?): Boolean {
    if (selectedStackFrame !is CallFrameView) return false

    var selectedVm: Vm? = null
    for ((key, value) in stacks) {
      if (value is ExecutionStackView && value.topFrame?.vm == selectedStackFrame.vm) {
        selectedVm = key
        break
      }
    }

    val selectedVmStack = stacks[selectedVm]
    if (selectedVm != null && selectedVmStack is ExecutionStackView) {
      activeVm = selectedVm
      stacks[selectedVm] = selectedVmStack.copyWithIsCurrent(true)

      stacks.keys.forEach {
        val stack = stacks[it]
        if (it != selectedVm && stack is ExecutionStackView) {
          stacks[it] = stack.copyWithIsCurrent(false)
        }
      }

      return stacks[selectedVm] !== selectedVmStack
    }

    return false
  }
}

class RunningThreadExecutionStackView(vm: Vm) : ScriptExecutionStack(vm, vm.presentableName, AllIcons.Debugger.ThreadRunning) {
  override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {
    // add dependency to DebuggerBundle?
    container?.errorOccurred("Frames not available for unsuspended thread")
  }

  override fun getTopFrame(): XStackFrame? = null
}

class InactiveAtBreakpointExecutionStackView(vm: Vm) : ScriptExecutionStack(vm, vm.presentableName, AllIcons.Debugger.ThreadAtBreakpoint) {
  override fun getTopFrame(): XStackFrame? = null

  override fun computeStackFrames(firstFrameIndex: Int, container: XStackFrameContainer?) {}
}

abstract class ScriptExecutionStack(val vm: Vm, displayName: String, icon: javax.swing.Icon): XExecutionStack(displayName, icon) {
  override fun hashCode(): Int {
    return vm.hashCode()
  }

  override fun equals(other: Any?): Boolean {
    return other is ScriptExecutionStack && other.vm == vm
  }
}

// TODO should be AllIcons.Debugger.ThreadCurrent, but because of strange logic to add non-equal XExecutionStacks we can't update icon.
private fun getThreadIcon(isCurrent: Boolean) = AllIcons.Debugger.ThreadAtBreakpoint

class ExecutionStackView(val suspendContext: SuspendContext<*>,
                         internal val viewSupport: DebuggerViewSupport,
                         private val topFrameScript: Script?,
                         private val topFrameSourceInfo: SourceInfo? = null,
                         displayName: String = "",
                         isCurrent: Boolean = true)
  : ScriptExecutionStack(suspendContext.vm, displayName, getThreadIcon(isCurrent)) {

  private var topCallFrameView: CallFrameView? = null

  override fun getTopFrame(): CallFrameView? {
    val topCallFrame = suspendContext.topFrame
    if (topCallFrameView == null || topCallFrameView!!.callFrame != topCallFrame) {
      topCallFrameView = topCallFrame?.let { CallFrameView(it, viewSupport, topFrameScript, topFrameSourceInfo, vm = suspendContext.vm) }
    }
    return topCallFrameView
  }

  override fun computeStackFrames(firstFrameIndex: Int, container: XExecutionStack.XStackFrameContainer) {
    // WipSuspendContextManager set context to null on resume _before_ vm.getDebugListener().resumed() call() (in any case, XFramesView can queue event to EDT), so, IDE state could be outdated compare to VM (our) state
    suspendContext.frames
        .done(suspendContext) { frames ->
          val count = frames.size - firstFrameIndex
          val result: List<XStackFrame>
          if (count < 1) {
            result = emptyList()
          }
          else {
            result = ArrayList(count)
            for (i in firstFrameIndex until frames.size) {
              if (i == 0) {
                result.add(topFrame!!)
                continue
              }

              val frame = frames[i]
              val asyncFunctionName = frame.asyncFunctionName
              if (asyncFunctionName != null) {
                result.add(AsyncFramesHeader(asyncFunctionName))
              }
              // if script is null, it is native function (Object.forEach for example), so, skip it
              val script = suspendContext.vm.scriptManager.getScript(frame)
              if (script != null) {
                val sourceInfo = viewSupport.getSourceInfo(script, frame)
                val isInLibraryContent = sourceInfo != null && viewSupport.isInLibraryContent(sourceInfo, script)
                if (isInLibraryContent && !XDebuggerSettingsManager.getInstance().dataViewSettings.isShowLibraryStackFrames) {
                  continue
                }

                result.add(CallFrameView(frame, viewSupport, script, sourceInfo, isInLibraryContent, suspendContext.vm))
              }
            }
          }
          container.addStackFrames(result, true)
        }
  }

  fun copyWithIsCurrent(isCurrent: Boolean): ExecutionStackView {
    if (icon == getThreadIcon(isCurrent)) return this

    return ExecutionStackView(suspendContext, viewSupport, topFrameScript, topFrameSourceInfo, displayName, isCurrent)
  }
}

private val ASYNC_HEADER_ATTRIBUTES = SimpleTextAttributes(SimpleTextAttributes.STYLE_UNDERLINE or SimpleTextAttributes.STYLE_BOLD,
                                                           UIUtil.getInactiveTextColor())

private class AsyncFramesHeader(val asyncFunctionName: String) : XStackFrame(), XDebuggerFramesList.ItemWithCustomBackgroundColor {
  override fun customizePresentation(component: ColoredTextContainer) {
    component.append("Async call from $asyncFunctionName", ASYNC_HEADER_ATTRIBUTES)
  }

  override fun getBackgroundColor(): Color? = null
}