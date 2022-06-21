// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.util.StorageId
import java.util.concurrent.Future

internal class VcsProjectLogErrorHandler(private val projectLog: VcsProjectLog) {
  private var recreatedCount = 0

  @RequiresEdt
  fun recreateOnError(t: Throwable) {
    if (projectLog.isDisposing) return
    val logManager = projectLog.logManager ?: return

    recreatedCount++

    val invalidateCaches = recreatedCount % INVALIDATE_CACHES_COUNT == 0
    if (invalidateCaches) {
      thisLogger().error("Invalidating Vcs Log caches and indexes after corruption (count=$recreatedCount).", t)
    }
    else {
      thisLogger().debug("Recreating Vcs Log after corruption (count=$recreatedCount).", t)
    }

    projectLog.recreateLog(logManager, invalidateCaches)
  }

  companion object {
    private const val INVALIDATE_CACHES_COUNT = 5

    fun VcsLogManager.storageIds(): List<StorageId> {
      return listOfNotNull((dataManager.index as? VcsLogPersistentIndex)?.indexStorageId,
                           (dataManager.storage as? VcsLogStorageImpl)?.refsStorageId,
                           (dataManager.storage as? VcsLogStorageImpl)?.hashesStorageId)
    }

    private fun VcsProjectLog.recreateLog(logManager: VcsLogManager, invalidateCaches: Boolean): Future<*>? {
      val storageIds = logManager.storageIds()
      thisLogger().assertTrue(storageIds.isNotEmpty())

      return runOnDisposedLog {
        if (invalidateCaches) {
          for (storageId in storageIds) {
            try {
              val storageDir = storageId.projectStorageDir
              val deleted = FileUtil.deleteWithRenaming(storageDir)
              if (deleted) thisLogger().info("Deleted $storageDir")
              else thisLogger().error("Could not delete $storageDir")
            }
            catch (t: Throwable) {
              thisLogger().error(t)
            }
          }
        }
      }
    }

    fun VcsProjectLog.invalidateCaches(logManager: VcsLogManager): Future<*>? = recreateLog(logManager, true)
  }
}