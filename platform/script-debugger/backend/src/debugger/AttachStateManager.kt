// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger

import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.nullPromise

@ApiStatus.Internal
interface AttachStateManager {
  fun detach(): Promise<*> = nullPromise()

  val isAttached: Boolean
    get() = true
}