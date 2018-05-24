// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.EventDispatcher
import com.intellij.util.SmartList
import com.intellij.util.Url
import com.intellij.util.containers.ContainerUtil
import gnu.trove.TObjectHashingStrategy
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.all
import org.jetbrains.concurrency.nullPromise
import org.jetbrains.concurrency.rejectedPromise
import java.util.concurrent.ConcurrentMap

abstract class BreakpointManagerBase<T : BreakpointBase<*>> : BreakpointManager {
  override val breakpoints: MutableSet<T> = ContainerUtil.newConcurrentSet<T>()

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

    override fun equals(b1: T, b2: T) =
      b1.target.javaClass == b2.target.javaClass &&
      b1.target == b2.target &&
      b1.line == b2.line &&
      b1.column == b2.column &&
      StringUtil.equals(b1.condition, b2.condition)
  })

  protected val dispatcher: EventDispatcher<BreakpointListener> = EventDispatcher.create(BreakpointListener::class.java)

  protected abstract fun createBreakpoint(target: BreakpointTarget, line: Int, column: Int, condition: String?, ignoreCount: Int, enabled: Boolean): T

  protected abstract fun doSetBreakpoint(target: BreakpointTarget, url: Url?, breakpoint: T): Promise<out Breakpoint>

  override fun setBreakpoint(target: BreakpointTarget,
                             line: Int,
                             column: Int,
                             url: Url?,
                             condition: String?,
                             ignoreCount: Int): BreakpointManager.SetBreakpointResult {
    val breakpoint = createBreakpoint(target, line, column, condition, ignoreCount, true)
    val existingBreakpoint = breakpointDuplicationByTarget.putIfAbsent(breakpoint, breakpoint)
    if (existingBreakpoint != null) {
      return BreakpointManager.BreakpointExist(existingBreakpoint)
    }

    breakpoints.add(breakpoint)
    val promise = doSetBreakpoint(target, url, breakpoint)
      .onError { dispatcher.multicaster.errorOccurred(breakpoint, it.message ?: it.toString()) }
    return BreakpointManager.BreakpointCreated(breakpoint, promise)
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
    return promises.all()
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
  override fun flush(breakpoint: Breakpoint): Promise<*> = (breakpoint as T).flush(this)

  override fun enableBreakpoints(enabled: Boolean): Promise<*> = rejectedPromise<Any?>("Unsupported")

  override fun setBreakOnFirstStatement() {
  }

  override fun isBreakOnFirstStatement(context: SuspendContext<*>): Boolean = false
}

// used in goland
@Suppress("unused")
class DummyBreakpointManager : BreakpointManager {
  override val breakpoints: Iterable<Breakpoint>
    get() = emptyList()

  override fun setBreakpoint(target: BreakpointTarget, line: Int, column: Int, url: Url?, condition: String?, ignoreCount: Int): BreakpointManager.SetBreakpointResult {
    throw UnsupportedOperationException()
  }

  override fun remove(breakpoint: Breakpoint): Promise<*> = nullPromise()

  override fun addBreakpointListener(listener: BreakpointListener) {
  }

  override fun removeAll(): Promise<*> = nullPromise()

  override fun flush(breakpoint: Breakpoint): Promise<*> = nullPromise()

  override fun enableBreakpoints(enabled: Boolean): Promise<*> = nullPromise()

  override fun setBreakOnFirstStatement() {
  }

  override fun isBreakOnFirstStatement(context: SuspendContext<*>): Boolean = false
}