// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad.util

import com.intellij.platform.kernel.KernelService
import com.jetbrains.rhizomedb.DB
import com.jetbrains.rhizomedb.DbContext
import fleet.kernel.Transactor
import fleet.kernel.lastKnownDb
import fleet.kernel.transactor


internal object ThreadLocalRhizomeDB {
  fun lastKnownDb(): DB {
    return unsafeKernel().lastKnownDb
  }

  fun setThreadLocalDb(db: DB) {
    val dbContext = DbContext<DB>(db, null)
    DbContext.threadLocal.set(dbContext)
  }

  @Suppress("OPT_IN_USAGE")
  private fun unsafeKernel(): Transactor {
    // unsafe because not sure whether the kernel is always ready/completed
    return KernelService.instance.kernelCoroutineScope.getCompleted().coroutineContext.transactor
  }
}
