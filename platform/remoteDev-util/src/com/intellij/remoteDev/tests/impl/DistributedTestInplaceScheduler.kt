package com.intellij.remoteDev.tests.impl

import com.jetbrains.rd.util.reactive.IScheduler
import com.jetbrains.rd.util.reactive.ExecutionOrder
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DistributedTestInplaceScheduler : IScheduler {
  override val executionOrder: ExecutionOrder
    get() = ExecutionOrder.OutOfOrder

  override val isActive: Boolean
    get() = true

  override fun flush() {
  }

  override fun queue(action: () -> Unit) {
    action()
  }
}