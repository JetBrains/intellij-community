/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.util.containers.ContainerUtil
import org.jetbrains.concurrency.Promise

abstract class BreakpointBase<L : Any>(override val target: BreakpointTarget,
                                       override var line: Int,
                                       override val column: Int,
                                       condition: String?,
                                       enabled: Boolean) : Breakpoint {
  val actualLocations: MutableList<L> = ContainerUtil.createLockFreeCopyOnWriteList<L>()

  /**
   * Whether the breakpoint data have changed with respect
   * to the JavaScript VM data
   */
  protected @Volatile var dirty: Boolean = false

  override val isResolved: Boolean
    get() = !actualLocations.isEmpty()

  override var condition: String? = condition
    set(value) {
      if (field != value) {
        field = value
        dirty = true
      }
  }

  override var enabled: Boolean = enabled
    set(value) {
      if (value != field) {
        field = value
        dirty = true
      }
    }

  fun setActualLocations(value: List<L>?) {
    actualLocations.clear()
    if (!ContainerUtil.isEmpty(value)) {
      actualLocations.addAll(value!!)
    }
  }

  fun setActualLocation(value: L?) {
    actualLocations.clear()
    if (value != null) {
      actualLocations.add(value)
    }
  }

  abstract fun isVmRegistered(): Boolean

  override fun hashCode(): Int {
    var result = line
    result *= 31 + column
    result *= 31 + (if (enabled) 1 else 0)
    if (condition != null) {
      result *= 31 + condition!!.hashCode()
    }
    result *= 31 + target.hashCode()
    return result
  }

  abstract fun flush(breakpointManager: BreakpointManager): Promise<*>
}