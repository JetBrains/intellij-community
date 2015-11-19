package org.jetbrains.debugger

import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.resolvedPromise
import java.util.concurrent.atomic.AtomicReference

abstract class SuspendContextManagerBase<T : SuspendContextBase<*, *>, CALL_FRAME : CallFrame> : SuspendContextManager<CALL_FRAME> {
  val contextRef = AtomicReference<T>()

  protected val suspendCallback = AtomicReference<AsyncPromise<Void>>()

  protected abstract val debugListener: DebugEventListener

  fun setContext(newContext: T) {
    if (!contextRef.compareAndSet(null, newContext)) {
      throw IllegalStateException("Attempt to set context, but current suspend context is already exists")
    }
  }

  // dismiss context on resumed
  protected fun dismissContext() {
    val context = contextRef.get()
    if (context != null) {
      contextDismissed(context)
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
    debugListener.resumed()
  }

  override val context: SuspendContext?
    get() = contextRef.get()

  override val contextOrFail: T
    get() = contextRef.get() ?: throw IllegalStateException("No current suspend context")

  override fun suspend(): Promise<*> {
    val callback = suspendCallback.get()
    if (callback != null) {
      return callback
    }
    return if (context != null) resolvedPromise() else doSuspend()
  }

  protected abstract fun doSuspend(): Promise<*>

  override fun isContextObsolete(context: SuspendContext) = this.context !== context

  override fun setOverlayMessage(message: String?) {
  }

  override fun restartFrame(callFrame: CALL_FRAME): Promise<Boolean> = restartFrame(callFrame, contextOrFail)

  protected open fun restartFrame(callFrame: CALL_FRAME, currentContext: T): Promise<Boolean> {
    return Promise.reject<Boolean>("Unsupported")
  }

  override fun canRestartFrame(callFrame: CallFrame) = false

  override val isRestartFrameSupported = false
}