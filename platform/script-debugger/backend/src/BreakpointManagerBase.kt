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

import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.SmartList
import com.intellij.util.Url
import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.util.containers.ContainerUtil
import gnu.trove.TObjectHashingStrategy
import org.jetbrains.concurrency.*
import java.util.concurrent.ConcurrentMap

abstract class BreakpointManagerBase<T : BreakpointBase<*>> : BreakpointManager {
  override val breakpoints = ContainerUtil.newConcurrentSet<T>()

  protected val breakpointDuplicationByTarget: ConcurrentMap<T, T> = ConcurrentCollectionFactory.createMap<T, T>(object : TObjectHashingStrategy<T> {
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

  protected val dispatcher: EventDispatcher<BreakpointListener> = EventDispatcher.create(BreakpointListener::class.java)

  protected abstract fun createBreakpoint(target: BreakpointTarget, line: Int, column: Int, condition: String?, ignoreCount: Int, enabled: Boolean): T

  protected abstract fun doSetBreakpoint(target: BreakpointTarget, url: Url?, breakpoint: T): Promise<out Breakpoint>

  override fun setBreakpoint(target: BreakpointTarget, line: Int, column: Int, url: Url?, condition: String?, ignoreCount: Int, enabled: Boolean, promiseRef: Ref<Promise<out Breakpoint>>?): Breakpoint {
    val breakpoint = createBreakpoint(target, line, column, condition, ignoreCount, enabled)
    val existingBreakpoint = breakpointDuplicationByTarget.putIfAbsent(breakpoint, breakpoint)
    if (existingBreakpoint != null) {
      promiseRef?.set(resolvedPromise(breakpoint))
      return existingBreakpoint
    }

    breakpoints.add(breakpoint)
    if (enabled) {
      val promise = doSetBreakpoint(target, url, breakpoint)
        .rejected { dispatcher.multicaster.errorOccurred(breakpoint, it.message ?: it.toString()) }
      promiseRef?.set(promise)
    }
    else {
      promiseRef?.set(resolvedPromise(breakpoint))
    }
    return breakpoint
  }

  override final fun remove(breakpoint: Breakpoint): Promise<*> {
    @Suppress("UNCHECKED_CAST")
    val b = breakpoint as T
    val existed = breakpoints.remove(b)
    if (existed) {
      breakpointDuplicationByTarget.remove(b)
    }
    return if (!existed || !b.isVmRegistered()) nullPromise() else doClearBreakpoint(b)
  }

  override final fun removeAll(): Promise<*> {
    val list = breakpoints.toList()
    breakpoints.clear()
    breakpointDuplicationByTarget.clear()
    val promises = SmartList<Promise<*>>()
    for (b in list) {
      if (b.isVmRegistered()) {
        promises.add(doClearBreakpoint(b))
      }
    }
    return all(promises)
  }

  protected abstract fun doClearBreakpoint(breakpoint: T): Promise<*>

  override final fun addBreakpointListener(listener: BreakpointListener) {
    dispatcher.addListener(listener)
  }

  protected fun notifyBreakpointResolvedListener(breakpoint: T) {
    if (breakpoint.isResolved) {
      dispatcher.multicaster.resolved(breakpoint)
    }
  }

  @Suppress("UNCHECKED_CAST")
  override fun flush(breakpoint: Breakpoint) = (breakpoint as T).flush(this)

  override fun enableBreakpoints(enabled: Boolean): Promise<*> = rejectedPromise<Any?>("Unsupported")
}

class DummyBreakpointManager : BreakpointManager {
  override val breakpoints: Iterable<Breakpoint>
    get() = emptyList()

  override fun setBreakpoint(target: BreakpointTarget, line: Int, column: Int, url: Url?, condition: String?, ignoreCount: Int, enabled: Boolean, promiseRef: Ref<Promise<out Breakpoint>>?): Breakpoint {
    throw UnsupportedOperationException()
  }

  override fun remove(breakpoint: Breakpoint) = nullPromise()

  override fun addBreakpointListener(listener: BreakpointListener) {
  }

  override fun removeAll() = nullPromise()

  override fun flush(breakpoint: Breakpoint) = nullPromise()

  override fun enableBreakpoints(enabled: Boolean) = nullPromise()
}