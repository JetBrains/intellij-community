// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.google.common.collect.EnumMultiset
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.data.index.VcsLogBigRepositoriesList
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.impl.VcsProjectLog.Companion.runOnDisposedLog
import com.intellij.vcs.log.util.StorageId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class VcsProjectLogErrorHandler(private val projectLog: VcsProjectLog, private val coroutineScope: CoroutineScope) {
  private val countBySource = EnumMultiset.create(VcsLogErrorHandler.Source::class.java)

  @RequiresEdt
  fun recreateOnError(source: VcsLogErrorHandler.Source, t: Throwable) {
    if (projectLog.isDisposing) return
    val logManager = projectLog.logManager ?: return

    countBySource.add(source)
    val count = countBySource.count(source)

    if (source == VcsLogErrorHandler.Source.Index) {
      if (count > DISABLE_INDEX_COUNT) {
        val rootsForIndexing = logManager.dataManager.index.indexingRoots
        thisLogger().error("Disabling indexing for ${rootsForIndexing.map { it.name }} due to corruption " +
                           "(count=$count).", t)
        rootsForIndexing.forEach { VcsLogBigRepositoriesList.getInstance().addRepository(it) }
        coroutineScope.launch { projectLog.recreateLog(logManager, true) }
        return
      }
    }

    val invalidateCaches = count % INVALIDATE_CACHES_COUNT == 0
    if (invalidateCaches) {
      thisLogger().error("Invalidating Vcs Log caches after $source corruption (count=$count).", t)
    }
    else {
      thisLogger().debug("Recreating Vcs Log after $source corruption (count=$count).", t)
    }

    coroutineScope.launch { projectLog.recreateLog(logManager, invalidateCaches) }
  }
}

private const val INVALIDATE_CACHES_COUNT = 5
private const val DISABLE_INDEX_COUNT = 2 * INVALIDATE_CACHES_COUNT

internal fun VcsLogManager.storageIds(): List<StorageId> {
  return linkedSetOf((dataManager.index as? VcsLogPersistentIndex)?.indexStorageId,
                     (dataManager.storage as? VcsLogStorageImpl)?.refsStorageId,
                     (dataManager.storage as? VcsLogStorageImpl)?.hashesStorageId).filterNotNull()
}

internal suspend fun VcsProjectLog.invalidateCaches(logManager: VcsLogManager) {
  recreateLog(logManager = logManager, invalidateCaches = true)
}

internal suspend fun VcsProjectLog.recreateLog(logManager: VcsLogManager, invalidateCaches: Boolean) {
  val storageIds = logManager.storageIds()
  thisLogger().assertTrue(storageIds.isNotEmpty())

  runOnDisposedLog {
    if (invalidateCaches) {
      for (storageId in storageIds) {
        try {
          val deleted = withContext(Dispatchers.IO) { storageId.cleanupAllStorageFiles() }
          if (deleted) {
            thisLogger().info("Deleted ${storageId.storagePath}")
          }
          else {
            thisLogger().error("Could not delete ${storageId.storagePath}")
          }
        }
        catch (t: Throwable) {
          thisLogger().error(t)
        }
      }
    }
  }
}
