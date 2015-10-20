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

import com.intellij.icons.AllIcons
import com.intellij.util.SmartList
import com.intellij.util.containers.MultiMap
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.util.concurrent.atomic.AtomicBoolean

abstract class LineBreakpointManager(private val debugProcess: DebugProcessImpl<*>) {
  private val ideToVmBreakpoints = THashMap<XLineBreakpoint<*>, MutableList<Breakpoint>>()
  protected val vmToIdeBreakpoint: MultiMap<Breakpoint, XLineBreakpoint<*>> = MultiMap.createSmart()

  private val runToLocationBreakpoints = THashSet<Breakpoint>()
  private val lock = Object()

  private val breakpointManager: BreakpointManager
    get() = debugProcess.vm!!.breakpointManager

  open fun isAnyFirstLineBreakpoint(breakpoint: Breakpoint) = false

  private val breakpointResolvedListenerAdded = AtomicBoolean()

  fun setBreakpoint(breakpoint: XLineBreakpoint<*>, onlySourceMappedBreakpoints: Boolean) {
    var target = synchronized (lock) { ideToVmBreakpoints.get(breakpoint) }
    if (target == null) {
      setBreakpoint(breakpoint, debugProcess.getLocationsForBreakpoint(breakpoint, onlySourceMappedBreakpoints))
    }
    else {
      val breakpointManager = breakpointManager
      for (vmBreakpoint in target) {
        if (!vmBreakpoint.enabled) {
          vmBreakpoint.enabled = true
          breakpointManager.flush(vmBreakpoint).rejected { debugProcess.session.updateBreakpointPresentation(breakpoint, AllIcons.Debugger.Db_invalid_breakpoint, it.getMessage()) }
        }
      }
    }
  }

  fun removeBreakpoint(breakpoint: XLineBreakpoint<*>, temporary: Boolean): Promise<*> {
    val disable = temporary && breakpointManager.getMuteMode() !== BreakpointManager.MUTE_MODE.NONE
    beforeBreakpointRemoved(breakpoint, disable)
    return doRemoveBreakpoint(breakpoint, disable)
  }

  protected open fun beforeBreakpointRemoved(breakpoint: XLineBreakpoint<*>, disable: Boolean) {
  }

  fun doRemoveBreakpoint(breakpoint: XLineBreakpoint<*>, disable: Boolean): Promise<*> {
    var vmBreakpoints: Collection<Breakpoint> = emptySet()
    synchronized (lock) {
      if (disable) {
        val list = ideToVmBreakpoints.get(breakpoint) ?: return resolvedPromise()
        val iterator = list.iterator()
        vmBreakpoints = list
        while (iterator.hasNext()) {
          val vmBreakpoint = iterator.next()
          if (vmToIdeBreakpoint.get(vmBreakpoint).size() > 1) {
            // we must not disable vm breakpoint - it is used for another ide breakpoints
            iterator.remove()
          }
        }
      }
      else {
        vmBreakpoints = ideToVmBreakpoints.remove(breakpoint) ?: return resolvedPromise()
        if (!vmBreakpoints.isEmpty()) {
          for (vmBreakpoint in vmBreakpoints) {
            vmToIdeBreakpoint.remove(vmBreakpoint, breakpoint)
            if (vmToIdeBreakpoint.containsKey(vmBreakpoint)) {
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

    val breakpointManager = breakpointManager
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

  fun setBreakpoint(breakpoint: XLineBreakpoint<*>, locations: List<Location>) {
    if (locations.isEmpty()) {
      return
    }

    val vmBreakpoints = SmartList<Breakpoint>()
    for (location in locations) {
      vmBreakpoints.add(doSetBreakpoint(breakpoint, location, false))
    }
    synchronized (lock) {
      ideToVmBreakpoints.put(breakpoint, vmBreakpoints)
      for (vmBreakpoint in vmBreakpoints) {
        vmToIdeBreakpoint.putValue(vmBreakpoint, breakpoint)
      }
    }
  }

  protected fun doSetBreakpoint(breakpoint: XLineBreakpoint<*>?, location: Location, isTemporary: Boolean): Breakpoint {
    if (breakpointResolvedListenerAdded.compareAndSet(false, true)) {
      breakpointManager.addBreakpointListener(object : BreakpointListener {
        override fun resolved(breakpoint: Breakpoint) {
          var breakpoints = synchronized (lock) { vmToIdeBreakpoint.get(breakpoint) }
          for (ideBreakpoint in breakpoints) {
            debugProcess.session.updateBreakpointPresentation(ideBreakpoint, AllIcons.Debugger.Db_verified_breakpoint, null)
          }
        }

        override fun errorOccurred(breakpoint: Breakpoint, errorMessage: String?) {
          if (isAnyFirstLineBreakpoint(breakpoint)) {
            return
          }

          if (synchronized (lock) { runToLocationBreakpoints.remove(breakpoint) }) {
            debugProcess.session.reportError("Cannot run to cursor: ${errorMessage!!}")
            return
          }

          var breakpoints = synchronized (lock) { vmToIdeBreakpoint.get(breakpoint) }
          for (ideBreakpoint in breakpoints) {
            debugProcess.session.updateBreakpointPresentation(ideBreakpoint, AllIcons.Debugger.Db_invalid_breakpoint, errorMessage)
          }
        }
      })
    }

    val breakpointManager = breakpointManager
    val target = createTarget(breakpoint, breakpointManager, location, isTemporary)
    val condition = breakpoint?.conditionExpression
    return breakpointManager.setBreakpoint(target, location.line, location.column, condition?.expression, Breakpoint.EMPTY_VALUE, true)
  }

  protected abstract fun createTarget(breakpoint: XLineBreakpoint<*>?, breakpointManager: BreakpointManager, location: Location, isTemporary: Boolean): BreakpointTarget

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
    var array = synchronized (lock) { ideToVmBreakpoints.keySet().toTypedArray() }
    for (breakpoint in array) {
      removeBreakpoint(breakpoint, false)
      setBreakpoint(breakpoint, false)
    }
  }

  fun removeAllBreakpoints(): org.jetbrains.concurrency.Promise<*> {
    synchronized (lock) {
      ideToVmBreakpoints.clear()
      vmToIdeBreakpoint.clear()
      runToLocationBreakpoints.clear()
    }
    return breakpointManager.removeAll()
  }

  fun clearRunToLocationBreakpoints() {
    var breakpoints = synchronized (lock) {
      if (runToLocationBreakpoints.isEmpty) {
        return@clearRunToLocationBreakpoints
      }
      var breakpoints = runToLocationBreakpoints.toArray<Breakpoint>(arrayOfNulls<Breakpoint>(runToLocationBreakpoints.size()))
      runToLocationBreakpoints.clear()
      breakpoints
    }

    val breakpointManager = breakpointManager
    for (breakpoint in breakpoints) {
      breakpointManager.remove(breakpoint)
    }
  }
}