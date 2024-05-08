// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.coroutines

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.yield

/**
 * The methods block execution while coroutines in the corresponding job are not done.
 * Usually it is required to get the proper result if your refactoring starts a coroutine outside the general execution e.g. adding imports
 */
@RequiresEdt
fun waitCoroutinesBlocking(cs: CoroutineScope) {
  runBlockingMaybeCancellable {
    val job = cs.coroutineContext.job
    while (true) {
      UIUtil.dispatchAllInvocationEvents()
      yield()

      val jobs = job.children.toList()
      if (jobs.isEmpty()) break

      UIUtil.dispatchAllInvocationEvents()
      yield()
    }
  }
}
