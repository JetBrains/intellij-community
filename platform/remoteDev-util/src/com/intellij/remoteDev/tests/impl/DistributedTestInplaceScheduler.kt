// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.remoteDev.tests.impl

import com.jetbrains.rd.util.reactive.ExecutionOrder
import com.jetbrains.rd.util.reactive.IScheduler
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