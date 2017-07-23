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
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.putValue
import com.intellij.util.containers.remove
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import gnu.trove.THashMap
import gnu.trove.THashSet
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.all
import org.jetbrains.concurrency.nullPromise
import org.jetbrains.concurrency.resolvedPromise

private val IDE_TO_VM_BREAKPOINTS_KEY = Key.create<THashMap<XLineBreakpoint<*>, MutableList<Breakpoint>>>("ideToVmBreakpoints")

abstract class LineBreakpointManager(internal val debugProcess: DebugProcessImpl<*>) {
  protected val vmToIdeBreakpoints = THashMap<Breakpoint, MutableList<XLineBreakpoint<*>>>()

  private val runToLocationBreakpoints = THashSet<Breakpoint>()
  private val lock = Object()

  open fun isAnyFirstLineBreakpoint(breakpoint: Breakpoint) = false

  private val breakpointResolvedListenerAdded = ContainerUtil.createConcurrentWeakMap<Vm, Unit>()

  fun setBreakpoint(vm: Vm, breakpoint: XLineBreakpoint<*>) {
    val target = synchronized (lock) { IDE_TO_VM_BREAKPOINTS_KEY.get(vm)?.get(breakpoint) }
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

  fun removeBreakpoint(vm: Vm, breakpoint: XLineBreakpoint<*>, temporary: Boolean): Promise<*> {
    val disable = temporary && debugProcess.mainVm!!.breakpointManager.getMuteMode() !== BreakpointManager.MUTE_MODE.NONE
    beforeBreakpointRemoved(breakpoint, disable)
    return doRemoveBreakpoint(vm, breakpoint, disable)
  }

  protected open fun beforeBreakpointRemoved(breakpoint: XLineBreakpoint<*>, disable: Boolean) {
  }

  fun doRemoveBreakpoint(vm: Vm, breakpoint: XLineBreakpoint<*>, disable: Boolean = false): Promise<*> {
    var vmBreakpoints: Collection<Breakpoint> = emptySet()
    synchronized (lock) {
      if (disable) {
        val list = IDE_TO_VM_BREAKPOINTS_KEY.get(vm)?.get(breakpoint) ?: return nullPromise()
        val iterator = list.iterator()
        vmBreakpoints = list
        while (iterator.hasNext()) {
          val vmBreakpoint = iterator.next()
          if (vmToIdeBreakpoints[vmBreakpoint]?.any { it != breakpoint } ?: false) {
            // we must not disable vm breakpoint - it is used for another ide breakpoints
            iterator.remove()
          }
        }
      }
      else {
        vmBreakpoints = IDE_TO_VM_BREAKPOINTS_KEY.get(vm)?.remove(breakpoint) ?: return nullPromise()
        for (vmBreakpoint in vmBreakpoints) {
          vmToIdeBreakpoints.remove(vmBreakpoint, breakpoint)
          if (vmToIdeBreakpoints[vmBreakpoint]?.any { it != breakpoint } ?: false) {
            // we must not remove vm breakpoint - it is used for another ide breakpoints
            return nullPromise()
          }
        }
      }
    }

    if (vmBreakpoints.isEmpty()) {
      return nullPromise()
    }

    val breakpointManager = vm.breakpointManager
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
    return all(promises)
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
      var list = IDE_TO_VM_BREAKPOINTS_KEY.get(vm)
      if (list == null) {
        list = vm.putUserDataIfAbsent(IDE_TO_VM_BREAKPOINTS_KEY, THashMap())
      }
      list.put(breakpoint, vmBreakpoints)
      for (vmBreakpoint in vmBreakpoints) {
        vmToIdeBreakpoints.putValue(vmBreakpoint, breakpoint)
      }
    }
  }

  protected fun doSetBreakpoint(vm: Vm, breakpoint: XLineBreakpoint<*>?, location: Location, isTemporary: Boolean, promiseRef: Ref<Promise<out Breakpoint>>? = null): Breakpoint? {
    if (breakpointResolvedListenerAdded.put(vm, kotlin.Unit) == null) {
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

          synchronized (lock) { vmToIdeBreakpoints.get(breakpoint) }
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
                IDE_TO_VM_BREAKPOINTS_KEY.get(vm)?.remove(ideBreakpoint, breakpoint)
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

    val breakpointManager = vm.breakpointManager
    val target = createTarget(breakpoint, breakpointManager, location, isTemporary)
    checkDuplicates(target, location, breakpointManager)?.let {
      promiseRef?.set(resolvedPromise(it))
      return it
    }
    return breakpointManager.setBreakpoint(target, location.line, location.column, location.url, breakpoint?.conditionExpression?.expression, promiseRef = promiseRef)
  }

  protected abstract fun createTarget(breakpoint: XLineBreakpoint<*>?, breakpointManager: BreakpointManager, location: Location, isTemporary: Boolean): BreakpointTarget

  protected open fun checkDuplicates(newTarget: BreakpointTarget, location: Location, breakpointManager: BreakpointManager): Breakpoint? = null

  fun runToLocation(position: XSourcePosition, vm: Vm) {
    val addedBreakpoints = doRunToLocation(position)
    if (addedBreakpoints.isEmpty()) {
      return
    }

    synchronized (lock) {
      runToLocationBreakpoints.addAll(addedBreakpoints)
    }
    debugProcess.resume(vm)
  }

  protected abstract fun doRunToLocation(position: XSourcePosition): List<Breakpoint>

  fun isRunToCursorBreakpoint(breakpoint: Breakpoint) = synchronized (runToLocationBreakpoints) { runToLocationBreakpoints.contains(breakpoint) }

  fun updateAllBreakpoints(vm: Vm) {
    val array = synchronized (lock) { IDE_TO_VM_BREAKPOINTS_KEY.get(vm)?.keys?.toTypedArray() } ?: return
    for (breakpoint in array) {
      removeBreakpoint(vm, breakpoint, false)
      setBreakpoint(vm, breakpoint)
    }
  }

  fun removeAllBreakpoints(vm: Vm): Promise<*> {
    synchronized (lock) {
      IDE_TO_VM_BREAKPOINTS_KEY.get(vm)?.clear()
      vmToIdeBreakpoints.clear()
      runToLocationBreakpoints.clear()
    }
    return vm.breakpointManager.removeAll()
  }

  fun clearRunToLocationBreakpoints(vm: Vm) {
    val breakpoints = synchronized (lock) {
      if (runToLocationBreakpoints.isEmpty) {
        return@clearRunToLocationBreakpoints
      }
      val breakpoints = runToLocationBreakpoints.toArray<Breakpoint>(arrayOfNulls<Breakpoint>(runToLocationBreakpoints.size))
      runToLocationBreakpoints.clear()
      breakpoints
    }

    val breakpointManager = vm.breakpointManager
    for (breakpoint in breakpoints) {
      breakpointManager.remove(breakpoint)
    }
  }
}