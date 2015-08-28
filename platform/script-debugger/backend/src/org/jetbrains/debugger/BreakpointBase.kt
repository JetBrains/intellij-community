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
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.util.concurrency.Promise

public abstract class BreakpointBase<L : Any>(override val target: BreakpointTarget,
                                        override var line: Int,
                                        public val column: Int,
                                        condition: String?,
                                        enabled: Boolean) : Breakpoint {
  public val actualLocations: MutableList<L> = ContainerUtil.createLockFreeCopyOnWriteList<L>()

  /**
   * Whether the breakpoint data have changed with respect
   * to the JavaScript VM data
   */
  protected volatile var dirty: Boolean = false

  override val isResolved: Boolean
    get() = !actualLocations.isEmpty()

  override var condition: String? = condition
    set(value: String?) {
      if (condition != value) {
        condition = value
        dirty = true
      }
  }

  override var enabled: Boolean = enabled
    set(value: Boolean) {
      if (value != enabled) {
        enabled = value
        dirty = true
      }
    }

  public fun setActualLocations(value: List<L>?) {
    actualLocations.clear()
    if (!ContainerUtil.isEmpty(value)) {
      actualLocations.addAll(value!!)
    }
  }

  public fun setActualLocation(value: L?) {
    actualLocations.clear()
    if (value != null) {
      actualLocations.add(value)
    }
  }

  public abstract fun isVmRegistered(): Boolean

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

  public abstract fun flush(breakpointManager: BreakpointManager): Promise<*>
}