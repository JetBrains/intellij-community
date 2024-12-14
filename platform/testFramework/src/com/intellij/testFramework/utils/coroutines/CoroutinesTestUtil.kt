// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.utils.coroutines

import com.intellij.openapi.progress.runBlockingMaybeCancellable
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.yield

/**
 * The methods block execution while coroutines in the corresponding job are not done.
 * Usually it is required to get the proper result if your refactoring starts a coroutine outside the general execution e.g. adding imports
 */
@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun waitCoroutinesBlocking(cs: CoroutineScope) {
  waitCoroutinesBlocking(cs, -1)
}

@RequiresBackgroundThread(generateAssertion = false)
@RequiresBlockingContext
fun waitCoroutinesBlocking(cs: CoroutineScope, timeoutMs: Long) {
  runBlockingMaybeCancellable {
    val job = cs.coroutineContext.job
    val start = System.currentTimeMillis()
    while (true) {
      runInEdtAndWait { UIUtil.dispatchAllInvocationEvents() }
      yield()
      delay(1) //prevent too frequent pooling, otherwise may load cpu with billions of context switches

      if (timeoutMs != -1L && System.currentTimeMillis() - start > timeoutMs) break
      val jobs = job.children.toList()
      if (jobs.isEmpty()) break
    }
  }
}
