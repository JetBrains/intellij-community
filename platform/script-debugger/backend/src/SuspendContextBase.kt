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

import org.jetbrains.debugger.values.ValueManager
import org.jetbrains.debugger.values.VmAwareValueManager

abstract class SuspendContextBase<VM: Vm, VALUE_MANAGER : ValueManager, F : CallFrame>(override final val valueManager: VALUE_MANAGER, protected val explicitPaused: Boolean) : SuspendContext<F> {
  override val state: SuspendState
    get() = if (exceptionData == null) (if (explicitPaused) SuspendState.PAUSED else SuspendState.NORMAL) else SuspendState.EXCEPTION

  override val script: Script?
    get() {
      val topFrame = topFrame
      return if (topFrame == null || valueManager !is VmAwareValueManager<*>) null else valueManager.vm.scriptManager.getScript(topFrame)
    }

  override val workerId: String? = null
}