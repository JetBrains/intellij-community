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

import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.rejectedPromise
import org.jetbrains.concurrency.resolvedPromise
import java.util.concurrent.atomic.AtomicReference

abstract class SuspendContextManagerBase<T : SuspendContextBase<CALL_FRAME>, CALL_FRAME : CallFrame> : SuspendContextManager<CALL_FRAME> {
  val contextRef = AtomicReference<T>()

  protected val suspendCallback = AtomicReference<AsyncPromise<Void>>()

  protected abstract val debugListener: DebugEventListener

  fun setContext(newContext: T) {
    if (!contextRef.compareAndSet(null, newContext)) {
      throw IllegalStateException("Attempt to set context, but current suspend context is already exists")
    }
  }

  open fun updateContext(newContext: SuspendContext<*>) {
  }

  // dismiss context on resumed
  protected fun dismissContext() {
    contextRef.get()?.let {
      contextDismissed(it)
    }
  }

  protected fun dismissContextOnDone(promise: Promise<*>): Promise<*> {
    val context = contextOrFail
    promise.done { contextDismissed(context) }
    return promise
  }

  fun contextDismissed(context: T) {
    if (!contextRef.compareAndSet(context, null)) {
      throw IllegalStateException("Expected $context, but another suspend context exists")
    }
    context.valueManager.markObsolete()
    debugListener.resumed(context.vm)
  }

  override val context: SuspendContext<CALL_FRAME>?
    get() = contextRef.get()

  override val contextOrFail: T
    get() = contextRef.get() ?: throw IllegalStateException("No current suspend context")

  override fun suspend() = suspendCallback.get() ?: if (context == null) doSuspend() else resolvedPromise()

  protected abstract fun doSuspend(): Promise<*>

  override fun setOverlayMessage(message: String?) {
  }

  override fun restartFrame(callFrame: CALL_FRAME): Promise<Boolean> = restartFrame(callFrame, contextOrFail)

  protected open fun restartFrame(callFrame: CALL_FRAME, currentContext: T) = rejectedPromise<Boolean>("Unsupported")

  override fun canRestartFrame(callFrame: CallFrame) = false

  override val isRestartFrameSupported = false
}