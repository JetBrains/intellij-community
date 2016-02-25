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
import com.intellij.openapi.util.Ref
import com.intellij.util.SmartList
import com.intellij.util.containers.putValue
import com.intellij.util.containers.remove
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.util.concurrent.atomic.AtomicBoolean

abstract class LineBreakpointManager(internal val debugProcess: DebugProcessImpl<*>) {
  private val ideToVmBreakpoints = THashMap<XLineBreakpoint<*>, MutableList<Breakpoint>>()
  protected val vmToIdeBreakpoints = THashMap<Breakpoint, MutableList<XLineBreakpoint<*>>>()

  private val runToLocationBreakpoints = THashSet<Breakpoint>()
  private val lock = Object()

  open fun isAnyFirstLineBreakpoint(breakpoint: Breakpoint) = false

  private val breakpointResolvedListenerAdded = AtomicBoolean()

  fun setBreakpoint(vm: Vm, breakpoint: XLineBreakpoint<*>) {
    val target = synchronized (lock) { ideToVmBreakpoints.get(breakpoint) }
    if (target == null) {
      setBreakpoint(vm, breakpoint, debugProcess.getLocationsForBreakpoint(vm, breakpoint))
    }
    else {
      val breakpointManager = vm.breakpointManager
      for (vmBreakpoint in target) {
        if (!vmBreakpoint.enabled) {
          vmBreakpoint.enabled = true
          breakpointManager.flush(vmBreakpoint)
            .rejected { debugProcess.session.updateBreakpointPresentation(breakpoint, AllIcons.Debugger.Db_invalid_breakpoint, it.message) }
        }
      }
    }
  }

  fun removeBreakpoint(breakpoint: XLineBreakpoint<*>, temporary: Boolean): Promise<*> {
    val disable = temporary && debugProcess.vm!!.breakpointManager.getMuteMode() !== BreakpointManager.MUTE_MODE.NONE
    beforeBreakpointRemoved(breakpoint, disable)
    return doRemoveBreakpoint(breakpoint, disable)
  }

  protected open fun beforeBreakpointRemoved(breakpoint: XLineBreakpoint<*>, disable: Boolean) {
  }

  fun doRemoveBreakpoint(breakpoint: XLineBreakpoint<*>, disable: Boolean = false): Promise<*> {
    var vmBreakpoints: Collection<Breakpoint> = emptySet()
    synchronized (lock) {
      if (disable) {
        val list = ideToVmBreakpoints.get(breakpoint) ?: return resolvedPromise()
        val iterator = list.iterator()
        vmBreakpoints = list
        while (iterator.hasNext()) {
          val vmBreakpoint = iterator.next()
          if ((vmToIdeBreakpoints.get(vmBreakpoint)?.size ?: -1) > 1) {
            // we must not disable vm breakpoint - it is used for another ide breakpoints
            iterator.remove()
          }
        }
      }
      else {
        vmBreakpoints = ideToVmBreakpoints.remove(breakpoint) ?: return resolvedPromise()
        if (!vmBreakpoints.isEmpty()) {
          for (vmBreakpoint in vmBreakpoints) {
            vmToIdeBreakpoints.remove(vmBreakpoint, breakpoint)
            if (vmToIdeBreakpoints.containsKey(vmBreakpoint)) {
              // we must not remove vm breakpoint - it is used for another ide breakpoints
              return resolvedPromise()
            }
          }
        }
      }
    }

    if (vmBreakpoints.isEmpty()) {
      return resolvedPromise()
    }

    val breakpointManager = debugProcess.vm!!.breakpointManager
    val promises = SmartList<Promise<*>>()
    if (disable) {
      for (vmBreakpoint in vmBreakpoints) {
        vmBreakpoint.enabled = false
        promises.add(breakpointManager.flush(vmBreakpoint))
      }
    }
    else {
      for (vmBreakpoint in vmBreakpoints) {
        promises.add(breakpointManager.remove(vmBreakpoint))
      }
    }
    return Promise.all(promises)
  }

  fun setBreakpoint(vm: Vm, breakpoint: XLineBreakpoint<*>, locations: List<Location>, promiseRef: Ref<Promise<out Breakpoint>>? = null) {
    if (locations.isEmpty()) {
      return
    }

    val vmBreakpoints = SmartList<Breakpoint>()
    for (location in locations) {
      doSetBreakpoint(vm, breakpoint, location, false, promiseRef)?.let { vmBreakpoints.add(it) }
    }
    synchronized (lock) {
      ideToVmBreakpoints.put(breakpoint, vmBreakpoints)
      for (vmBreakpoint in vmBreakpoints) {
        vmToIdeBreakpoints.putValue(vmBreakpoint, breakpoint)
      }
    }
  }

  protected fun doSetBreakpoint(vm: Vm, breakpoint: XLineBreakpoint<*>?, location: Location, isTemporary: Boolean, promiseRef: Ref<Promise<out Breakpoint>>? = null): Breakpoint? {
    if (breakpointResolvedListenerAdded.compareAndSet(false, true)) {
      vm.breakpointManager.addBreakpointListener(object : BreakpointListener {
        override fun resolved(breakpoint: Breakpoint) {
          synchronized (lock) { vmToIdeBreakpoints[breakpoint] }?.let {
            for (ideBreakpoint in it) {
              debugProcess.session.updateBreakpointPresentation(ideBreakpoint, AllIcons.Debugger.Db_verified_breakpoint, null)
            }
          }
        }

        override fun errorOccurred(breakpoint: Breakpoint, errorMessage: String?) {
          if (isAnyFirstLineBreakpoint(breakpoint)) {
            return
          }

          if (synchronized (lock) { runToLocationBreakpoints.remove(breakpoint) }) {
            debugProcess.session.reportError("Cannot run to cursor: $errorMessage")
            return
          }

          synchronized (lock) { vmToIdeBreakpoints[breakpoint] }
            ?.let {
              for (ideBreakpoint in it) {
                debugProcess.session.updateBreakpointPresentation(ideBreakpoint, AllIcons.Debugger.Db_invalid_breakpoint, errorMessage)
              }
            }
        }

        override fun nonProvisionalBreakpointRemoved(breakpoint: Breakpoint) {
          synchronized (lock) {
            vmToIdeBreakpoints.remove(breakpoint)?.let {
              for (ideBreakpoint in it) {
                ideToVmBreakpoints.remove(ideBreakpoint, breakpoint)
              }
              it
            }
          }
            ?.let {
              for (ideBreakpoint in it) {
                setBreakpoint(vm, ideBreakpoint, debugProcess.getLocationsForBreakpoint(vm, ideBreakpoint))
              }
            }
        }
      })
    }

    val breakpointManager = debugProcess.vm?.breakpointManager ?: return null
    val target = createTarget(breakpoint, breakpointManager, location, isTemporary)
    checkDuplicates(target, location, breakpointManager)?.let {
      promiseRef?.set(resolvedPromise(it))
      return it
    }
    return breakpointManager.setBreakpoint(target, location.line, location.column, location.url, breakpoint?.conditionExpression?.expression, promiseRef = promiseRef)
  }

  protected abstract fun createTarget(breakpoint: XLineBreakpoint<*>?, breakpointManager: BreakpointManager, location: Location, isTemporary: Boolean): BreakpointTarget

  protected open fun checkDuplicates(newTarget: BreakpointTarget, location: Location, breakpointManager: BreakpointManager): Breakpoint? = null

  fun runToLocation(position: XSourcePosition) {
    val addedBreakpoints = doRunToLocation(position)
    if (addedBreakpoints.isEmpty()) {
      return
    }

    synchronized (lock) {
      runToLocationBreakpoints.addAll(addedBreakpoints)
    }
    debugProcess.resume()
  }

  protected abstract fun doRunToLocation(position: XSourcePosition): List<Breakpoint>

  fun isRunToCursorBreakpoints(breakpoints: List<Breakpoint>): Boolean {
    synchronized (runToLocationBreakpoints) {
      return runToLocationBreakpoints.containsAll(breakpoints)
    }
  }

  fun updateAllBreakpoints() {
    var array = synchronized (lock) { ideToVmBreakpoints.keys.toTypedArray() }
    for (breakpoint in array) {
      removeBreakpoint(breakpoint, false)
      debugProcess.vm?.let { setBreakpoint(it, breakpoint) }
    }
  }

  fun removeAllBreakpoints(): Promise<*> {
    synchronized (lock) {
      ideToVmBreakpoints.clear()
      vmToIdeBreakpoints.clear()
      runToLocationBreakpoints.clear()
    }
    return debugProcess.vm!!.breakpointManager.removeAll()
  }

  fun clearRunToLocationBreakpoints(vm: Vm) {
    var breakpoints = synchronized (lock) {
      if (runToLocationBreakpoints.isEmpty) {
        return@clearRunToLocationBreakpoints
      }
      var breakpoints = runToLocationBreakpoints.toArray<Breakpoint>(arrayOfNulls<Breakpoint>(runToLocationBreakpoints.size))
      runToLocationBreakpoints.clear()
      breakpoints
    }

    val breakpointManager = vm.breakpointManager
    for (breakpoint in breakpoints) {
      breakpointManager.remove(breakpoint)
    }
  }
}