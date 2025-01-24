// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl.ad

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.platform.kernel.KernelService
import com.intellij.platform.kernel.withKernel
import com.intellij.util.concurrency.ThreadingAssertions
import com.jetbrains.rhizomedb.*
import fleet.kernel.*
import fleet.util.AtomicRef
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.annotations.ApiStatus.Experimental

@Experimental
internal object ThreadLocalRhizomeDB {
  private val pendingDbRef: AtomicRef<DB?> = AtomicRef()

  fun lastKnownOrPendingDb(): DB {
    val db = pendingDbRef.get()
    if (db != null) {
      return db
    }
    return lastKnownDb()
  }

  fun lastKnownDb(): DB {
    return unsafeKernel().lastKnownDb
  }

  fun setThreadLocalDb(db: DB) {
    val dbContext = DbContext<DB>(db, null)
    DbContext.threadLocal.set(dbContext)
  }

  fun <T> sharedChange(body: ChangeScope.() -> T): T {
    ThreadingAssertions.assertEventDispatchThread()
    @Suppress("RAW_RUN_BLOCKING") // I know I know >_<
    val res = runBlocking {
      withKernel {
        change {
          shared {
            body()
          }
        }
      }
    }
    setThreadLocalDb(lastKnownDb())
    return res
  }

  @Suppress("unused") // TODO: does not work, changeAsync.join() resumes too early in split mode
  private fun <T> superHackyNonBlockingChange1(body: () -> T): T {

    val db = DbContext.threadLocal.get()?.impl

    require(db is DB)

    val kernel = unsafeKernel()
    val middleware = kernel.middleware
    var result: T? = null
    var change: Change? = null
    val instructionList = buildList {
      change = db.change(defaultPart = 1) {
        middleware.run {
          performChange {
            DbContext.threadBound.ensureMutable {
              result = alter(impl.collectingInstructions(this@buildList::add)) {
                body()
              }
            }
          }
        }
      }
    }

    // changes made in EDT must be visible immediately
    val pendingDb = change!!.dbAfter
    setThreadLocalDb(pendingDb)
    pendingDbRef.set(pendingDb)

    val changeAsync: Deferred<Change> = kernel.changeAsync {
      instructionList.forEach(this::mutate)
    }

    MyAppService.getInstance().coroutineScope.launch {
      changeAsync.join() // <--
      pendingDbRef.compareAndSet(pendingDb, null) // it is safe as long as this method requires EDT
    }

    @Suppress("UNCHECKED_CAST")
    return result as T
  }

  @Suppress("OPT_IN_USAGE")
  private fun unsafeKernel(): Transactor {
    // unsafe because not sure whether the kernel is always ready/completed
    return KernelService.instance.kernelCoroutineScope.getCompleted().coroutineContext.transactor
  }
}

@Service(Level.APP)
private class MyAppService(val coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(): MyAppService {
      return ApplicationManager.getApplication().getService(MyAppService::class.java)
    }
  }
}
