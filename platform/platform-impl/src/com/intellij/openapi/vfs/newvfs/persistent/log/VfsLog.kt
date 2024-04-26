// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log

import com.intellij.openapi.vfs.newvfs.persistent.VfsRecoveryUtils
import com.intellij.openapi.vfs.newvfs.persistent.intercept.ConnectionInterceptor
import com.intellij.util.SystemProperties
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration

/**
 * VfsLog tracks every modification operation done to files of PersistentFS and persists them in a separate storage,
 * allows to query the resulting operations log.
 */
@ApiStatus.Experimental
interface VfsLog {
  /**
   * Guarantees that there is no concurrent compaction running until [VfsLogQueryContext] is closed.
   * In case there is a concurrent compaction running, it will be automatically requested to cancel, and this method will block until
   * compaction is finished.
   * @see [tryQuery]
   * @see [VfsLogQueryContext]
   */
  fun query(): VfsLogQueryContext

  /**
   * A non-blocking alternative for [query].
   * @return null if [VfsLogQueryContext] guarantees can not be met at the moment
   */
  fun tryQuery(): VfsLogQueryContext?

  fun isCompactionRunning(): Boolean

  companion object {
    private val LOG_VFS_OPERATIONS_ENABLED: Boolean =
      SystemProperties.getBooleanProperty(
        "idea.vfs.log-vfs-operations.enabled",
        // recovery via VfsLog does not work, because indexing has migrated to fast attributes, which are not yet tracked
        false
        /*try {
          ApplicationManager.getApplication().isEAP &&
          !ApplicationManager.getApplication().isUnitTestMode &&
          !ApplicationManagerEx.isInStressTest() &&
          !ApplicationManagerEx.isInIntegrationTest()
        }
        catch (e: NullPointerException) { // ApplicationManager may be not initialized in some tests
          logger<VfsLog>().info("ApplicationManager is not available", e)
          false
        }*/
      )

    @JvmStatic
    val isVfsTrackingEnabled: Boolean get() = LOG_VFS_OPERATIONS_ENABLED
  }
}

@ApiStatus.Internal
interface VfsLogEx : VfsLog {
  val connectionInterceptors: List<ConnectionInterceptor>
  val applicationVFileEventsTracker: ApplicationVFileEventsTracker

  override fun tryQuery(): VfsLogQueryContextEx?
  override fun query(): VfsLogQueryContextEx

  fun getOperationWriteContext(): VfsLogOperationTrackingContext

  fun acquireCompactionContext(): VfsLogCompactionContext
  fun tryAcquireCompactionContext(): VfsLogCompactionContext?

  fun getRecoveryPoints(): List<VfsRecoveryUtils.RecoveryPoint>

  fun awaitPendingWrites(timeout: Duration = Duration.INFINITE)
  fun flush()
  fun dispose()
}