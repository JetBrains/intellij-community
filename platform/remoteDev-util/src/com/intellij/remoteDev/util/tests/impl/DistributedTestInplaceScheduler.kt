package com.intellij.remoteDev.util.tests.impl

import com.jetbrains.rd.util.reactive.IScheduler
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DistributedTestInplaceScheduler : IScheduler {
  override val isActive: Boolean
    get() = true

  override fun flush() {
  }

  override fun queue(action: () -> Unit) {
    action()
  }
}