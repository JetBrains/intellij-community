// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.platform.kernel.KernelService
import com.jetbrains.rhizomedb.asOf
import fleet.kernel.DbSource
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * Allows reading from the current DB state.
 * It may be useful when you don't know whether function will be called in EDT context (where DB state is propagated)
 * or in some background thread, where DB state is not propagated by default.
 *
 * Avoid using this API if your code is called in EDT.
 *
 * NB: This is a delicate API! Use with caution, since using [f] may be called on the "old" DB state where some latest changes are not applied
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun <T> withCurrentDb(f: () -> T): T {
  return asOf(KernelService.instance.kernelCoroutineScope.getCompleted().coroutineContext[DbSource.ContextElement]!!.dbSource.latest) {
    f()
  }
}