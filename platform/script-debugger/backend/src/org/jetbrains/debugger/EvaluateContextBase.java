package org.jetbrains.debugger

import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.values.ValueManager

abstract class EvaluateContextBase<VALUE_MANAGER : ValueManager<out Vm>>(val valueManager: VALUE_MANAGER) : EvaluateContext {
  override fun withValueManager(objectGroup: String) = this

  override fun releaseObjects() {
  }

  override fun refreshOnDone(promise: Promise<*>): Promise<*> = promise.then(valueManager.clearCachesTask)
}