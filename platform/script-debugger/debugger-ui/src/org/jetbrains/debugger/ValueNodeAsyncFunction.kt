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

import com.intellij.util.Consumer
import org.jetbrains.concurrency.Promise

abstract class ContextDependentAsyncResultConsumer<T>(private val context: SuspendContext) : Consumer<T> {
  override final fun consume(result: T) {
    val vm = context.valueManager.vm
    if (vm.attachStateManager.isAttached() && !vm.suspendContextManager.isContextObsolete(context)) {
      consume(result, vm)
    }
  }

  protected abstract fun consume(result: T, vm: Vm)
}


inline fun <T> Promise<T>.done(context: SuspendContext, crossinline handler: (result: T) -> Unit) = done(object : ContextDependentAsyncResultConsumer<T>(context) {
  override fun consume(result: T, vm: Vm) = handler(result)
})

inline fun Promise<*>.rejected(context: SuspendContext, crossinline handler: (error: Throwable) -> Unit) = rejected(object : ContextDependentAsyncResultConsumer<Throwable>(context) {
  override fun consume(result: Throwable, vm: Vm) = handler(result)
})