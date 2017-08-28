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

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.containers.ContainerUtil

abstract class VmBase(override val debugListener: DebugEventListener) : Vm, AttachStateManager, UserDataHolderBase() {
  override val evaluateContext by lazy(LazyThreadSafetyMode.NONE) { computeEvaluateContext() }

  override val attachStateManager: AttachStateManager = this

  protected open fun computeEvaluateContext(): EvaluateContext? = null

  override var captureAsyncStackTraces: Boolean
    get() = false
    set(value) { }

  override val childVMs: MutableList<Vm> = ContainerUtil.createConcurrentList()
}