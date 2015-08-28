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
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.MultiMap
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import gnu.trove.THashSet
import org.jetbrains.util.concurrency
import org.jetbrains.util.concurrency.Promise
import org.jetbrains.util.concurrency.ResolvedPromise

public abstract class LineBreakpointManager(private val vm: Vm, private val debugProcess: DebugProcessImpl<*>) {
  private val ideToVmBreakpoint = MultiMap.createSmart<XLineBreakpoint<*>, Breakpoint>()
  protected val vmToIdeBreakpoint: MultiMap<Breakpoint, XLineBreakpoint<*>> = MultiMap.createSmart()

  private val runToLocationBreakpoints = THashSet<Breakpoint>()
  private val lock = Object()

  init {
    vm.getBreakpointManager().addBreakpointListener(object : BreakpointManager.BreakpointListener {
      override fun resolved(breakpoint: Breakpoint) {
        var breakpoints = synchronized (lock) { vmToIdeBreakpoint.get(breakpoint) }
        for (ideBreakpoint in breakpoints) {
          debugProcess.getSession().updateBreakpointPresentation(ideBreakpoint, AllIcons.Debugger.Db_verified_breakpoint, null)
        }
      }

      override fun errorOccurred(breakpoint: Breakpoint, errorMessage: String?) {
        if (isAnyFirstLineBreakpoint(breakpoint)) {
          return
        }

        if (synchronized (lock) { runToLocationBreakpoints.remove(breakpoint) }) {
          debugProcess.getSession().reportError("Cannot run to cursor: ${errorMessage!!}")
          return
        }

        var breakpoints = synchronized (lock) { vmToIdeBreakpoint.get(breakpoint) }
        for (ideBreakpoint in breakpoints) {
          debugProcess.getSession().updateBreakpointPresentation(ideBreakpoint, AllIcons.Debugger.Db_invalid_breakpoint, errorMessage)
        }
      }
    })
  }

  public open fun isAnyFirstLineBreakpoint(breakpoint: Breakpoint): Boolean = false

  public fun setBreakpoint(breakpoint: XLineBreakpoint<*>, onlySourceMappedBreakpoints: Boolean) {
    var target = synchronized (lock) { ideToVmBreakpoint.get(breakpoint) }
    if (target.isEmpty()) {
      setBreakpoint(breakpoint, debugProcess.getLocationsForBreakpoint(breakpoint, onlySourceMappedBreakpoints))
    }
    else {
      val breakpointManager = vm.getBreakpointManager()
      for (vmBreakpoint in target) {
        if (!vmBreakpoint.enabled) {
          vmBreakpoint.enabled = true
          breakpointManager.flush(vmBreakpoint).rejected { debugProcess.getSession().updateBreakpointPresentation(breakpoint, AllIcons.Debugger.Db_invalid_breakpoint, it.getMessage()) }
        }
      }
    }
  }

  public fun removeBreakpoint(breakpoint: XLineBreakpoint<*>, temporary: Boolean): concurrency.Promise<*> {
    val disable = temporary && vm.getBreakpointManager().getMuteMode() !== BreakpointManager.MUTE_MODE.NONE
    beforeBreakpointRemoved(breakpoint, disable)
    return doRemoveBreakpoint(breakpoint, disable)
  }

  protected open fun beforeBreakpointRemoved(breakpoint: XLineBreakpoint<*>, disable: Boolean) {
  }

  public fun doRemoveBreakpoint(breakpoint: XLineBreakpoint<*>, disable: Boolean): concurrency.Promise<*> {
    var vmBreakpoints: Collection<Breakpoint> = emptySet()
    synchronized (lock) {
      if (disable) {
        vmBreakpoints = ideToVmBreakpoint.get(breakpoint)
        if (!vmBreakpoints.isEmpty()) {
          val iterator = vmBreakpoints.iterator() as MutableIterator<Breakpoint>
          while (iterator.hasNext()) {
            val vmBreakpoint = iterator.next()
            if (vmToIdeBreakpoint.get(vmBreakpoint).size() > 1) {
              // we must not disable vm breakpoint - it is used for another ide breakpoints
              iterator.remove()
            }
          }
        }
      }
      else {
        vmBreakpoints = ideToVmBreakpoint.remove(breakpoint)
        if (!ContainerUtil.isEmpty(vmBreakpoints)) {
          for (vmBreakpoint in vmBreakpoints) {
            vmToIdeBreakpoint.remove(vmBreakpoint, breakpoint)
            if (vmToIdeBreakpoint.containsKey(vmBreakpoint)) {
              // we must not remove vm breakpoint - it is used for another ide breakpoints
              return ResolvedPromise()
            }
          }
        }
      }
    }

    if (ContainerUtil.isEmpty(vmBreakpoints)) {
      return ResolvedPromise()
    }

    val breakpointManager = vm.getBreakpointManager()
    val promises = SmartList<concurrency.Promise<*>>()
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
    return concurrency.Promise.all(promises)
  }

  public fun setBreakpoint(breakpoint: XLineBreakpoint<*>, locations: List<Location>) {
    if (locations.isEmpty()) {
      return
    }

    val vmBreakpoints = SmartList<Breakpoint>()
    for (location in locations) {
      vmBreakpoints.add(doSetBreakpoint(breakpoint, location, false))
    }
    synchronized (lock) {
      ideToVmBreakpoint.put(breakpoint, vmBreakpoints)
      for (vmBreakpoint in vmBreakpoints) {
        vmToIdeBreakpoint.putValue(vmBreakpoint, breakpoint)
      }
    }
  }

  protected fun doSetBreakpoint(breakpoint: XLineBreakpoint<*>?, location: Location, isTemporary: Boolean): Breakpoint {
    val breakpointManager = vm.getBreakpointManager()
    val target = createTarget(breakpoint, breakpointManager, location, isTemporary)
    val condition = breakpoint?.getConditionExpression()
    return breakpointManager.setBreakpoint(target, location.getLine(), location.getColumn(), condition?.getExpression(), Breakpoint.EMPTY_VALUE, true)
  }

  protected abstract fun createTarget(breakpoint: XLineBreakpoint<*>?, breakpointManager: BreakpointManager, location: Location, isTemporary: Boolean): BreakpointTarget

  public fun runToLocation(position: XSourcePosition) {
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

  public fun isRunToCursorBreakpoints(breakpoints: List<Breakpoint>): Boolean {
    synchronized (runToLocationBreakpoints) {
      return runToLocationBreakpoints.containsAll(breakpoints)
    }
  }

  public fun updateAllBreakpoints() {
    var array = synchronized (lock) { ideToVmBreakpoint.keySet().toTypedArray() }
    for (breakpoint in array) {
      removeBreakpoint(breakpoint, false)
      setBreakpoint(breakpoint, false)
    }
  }

  public fun removeAllBreakpoints(): Promise<*> {
    synchronized (lock) {
      ideToVmBreakpoint.clear()
      vmToIdeBreakpoint.clear()
      runToLocationBreakpoints.clear()
    }
    return vm.getBreakpointManager().removeAll()
  }

  public fun clearRunToLocationBreakpoints() {
    var breakpoints = synchronized (lock) {
      if (runToLocationBreakpoints.isEmpty()) {
        return@clearRunToLocationBreakpoints
      }
      var breakpoints = runToLocationBreakpoints.toArray<Breakpoint>(arrayOfNulls<Breakpoint>(runToLocationBreakpoints.size()))
      runToLocationBreakpoints.clear()
      breakpoints
    }

    val breakpointManager = vm.getBreakpointManager()
    for (breakpoint in breakpoints) {
      breakpointManager.remove(breakpoint)
    }
  }
}