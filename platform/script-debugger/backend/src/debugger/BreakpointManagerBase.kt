// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

import com.intellij.util.Url
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.nullPromise

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