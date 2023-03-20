// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.google.common.collect.EnumMultiset
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.data.index.VcsLogBigRepositoriesList
import com.intellij.vcs.log.data.index.VcsLogPersistentIndex
import com.intellij.vcs.log.util.StorageId
import java.util.concurrent.Future

internal class VcsProjectLogErrorHandler(private val projectLog: VcsProjectLog) {
  private val countBySource = EnumMultiset.create(VcsLogErrorHandler.Source::class.java)

  @RequiresEdt
  fun recreateOnError(source: VcsLogErrorHandler.Source, t: Throwable) {
    if (projectLog.isDisposing) return
    val logManager = projectLog.logManager ?: return

    countBySource.add(source)
    val count = countBySource.count(source)

    if (source == VcsLogErrorHandler.Source.Index) {
      if (count > DISABLE_INDEX_COUNT) {
        val rootsForIndexing = VcsLogPersistentIndex.getRootsForIndexing(logManager.dataManager.logProviders)
        thisLogger().error("Disabling indexing for ${rootsForIndexing.map { it.name }} due to corruption " +
                          "(count=$count).", t)
        rootsForIndexing.forEach { VcsLogBigRepositoriesList.getInstance().addRepository(it) }
        projectLog.recreateLog(logManager, true)
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

    projectLog.recreateLog(logManager, invalidateCaches)
  }

  companion object {
    private const val INVALIDATE_CACHES_COUNT = 5
    private const val DISABLE_INDEX_COUNT = 2 * INVALIDATE_CACHES_COUNT

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