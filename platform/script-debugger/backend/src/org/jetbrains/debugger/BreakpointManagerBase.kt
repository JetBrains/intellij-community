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

import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil
import gnu.trove.TObjectHashingStrategy
import org.jetbrains.util.concurrency.Promise
import org.jetbrains.util.concurrency.RejectedPromise
import org.jetbrains.util.concurrency.ResolvedPromise
import java.util.concurrent.ConcurrentMap

public abstract class BreakpointManagerBase<T : BreakpointBase<*>> : BreakpointManager {
  override val breakpoints: MutableSet<T> = ContainerUtil.newConcurrentSet<T>()

  protected val breakpointDuplicationByTarget: ConcurrentMap<T, T> = ContainerUtil.newConcurrentMap<T, T>(object : TObjectHashingStrategy<T> {
    override fun computeHashCode(b: T): Int {
      var result = b.line
      result *= 31 + b.column
      if (b.condition != null) {
        result *= 31 + b.condition!!.hashCode()
      }
      result *= 31 + b.target.hashCode()
      return result
    }

    override fun equals(b1: T, b2: T) = b1.target.javaClass == b2.target.javaClass && b1.target == b2.target && b1.line == b2.line && b1.column == b2.column && StringUtil.equals(b1.condition, b2.condition)
  })

  protected val dispatcher: EventDispatcher<BreakpointManager.BreakpointListener> = EventDispatcher.create(javaClass<BreakpointManager.BreakpointListener>())

  protected abstract fun createBreakpoint(target: BreakpointTarget, line: Int, column: Int, condition: String?, ignoreCount: Int, enabled: Boolean): T

  protected abstract fun doSetBreakpoint(target: BreakpointTarget, breakpoint: T): Promise<Breakpoint>

  override fun setBreakpoint(target: BreakpointTarget, line: Int, column: Int, condition: String?, ignoreCount: Int, enabled: Boolean): Breakpoint {
    val breakpoint = createBreakpoint(target, line, column, condition, ignoreCount, enabled)
    val existingBreakpoint = breakpointDuplicationByTarget.putIfAbsent(breakpoint, breakpoint)
    if (existingBreakpoint != null) {
      return existingBreakpoint
    }

    breakpoints.add(breakpoint)
    if (enabled) {
      doSetBreakpoint(target, breakpoint).rejected { dispatcher.getMulticaster().errorOccurred(breakpoint, it.getMessage() ?: it.toString()) }
    }
    return breakpoint
  }

  override fun remove(breakpoint: Breakpoint): Promise<*> {
    @suppress("UNCHECKED_CAST")
    val b = breakpoint as T
    val existed = breakpoints.remove(b)
    if (existed) {
      breakpointDuplicationByTarget.remove(b)
    }
    return if (!existed || !b.isVmRegistered()) ResolvedPromise() else doClearBreakpoint(b)
  }

  override fun removeAll(): Promise<*> {
    val list = breakpoints.toList()
    breakpoints.clear()
    breakpointDuplicationByTarget.clear()
    val promises = SmartList<Promise<*>>()
    for (b in list) {
      if (b.isVmRegistered()) {
        promises.add(doClearBreakpoint(b))
      }
    }
    return Promise.all(promises)
  }

  protected abstract fun doClearBreakpoint(breakpoint: T): Promise<*>

  override fun addBreakpointListener(listener: BreakpointManager.BreakpointListener) {
    dispatcher.addListener(listener)
  }

  protected fun notifyBreakpointResolvedListener(breakpoint: T) {
    if (breakpoint.isResolved) {
      dispatcher.getMulticaster().resolved(breakpoint)
    }
  }

  @suppress("UNCHECKED_CAST")
  override fun flush(breakpoint: Breakpoint) = (breakpoint as T).flush(this)

  override fun enableBreakpoints(enabled: Boolean): Promise<*> = RejectedPromise<Any?>("Unsupported")
}