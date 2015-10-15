package org.jetbrains.rpc

import org.jetbrains.concurrency.Promise

abstract class MessageManagerBase {
  @Volatile protected var closed = false

  protected fun rejectIfClosed(callback: RequestCallback<*>): Boolean {
    if (closed) {
      callback.onError(Promise.createError("Connection closed"))
      return true
    }
    return false
  }

  fun closed() {
    closed = true
  }

  companion object {
    protected fun rejectCallback(callback: RequestCallback<*>) {
      callback.onError(Promise.createError("Connection closed"))
    }
  }
}