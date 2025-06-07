// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.platform.util.io.storages.StorageFactory
import com.intellij.platform.util.io.storages.mmapped.MMappedFileStorageFactory
import com.intellij.util.SystemProperties
import com.intellij.util.io.StorageAlreadyInUseException
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.nio.file.Path
import kotlin.jvm.optionals.getOrNull

@Internal
abstract class PersistentFSRecordsStorageFactory(val id: Int) : StorageFactory<PersistentFSRecordsStorage> {


  /** Currently the default impl */
  data class OverMMappedFile(val pageSize: Int = PersistentFSRecordsLockFreeOverMMappedFile.DEFAULT_MAPPED_CHUNK_SIZE,
                             val acquireStorageOwnership: Boolean = true)
    : PersistentFSRecordsStorageFactory(id = 0) {

    override fun open(storagePath: Path): PersistentFSRecordsLockFreeOverMMappedFile = MMappedFileStorageFactory.withDefaults()
      .pageSize(pageSize)
      .wrapStorageSafely<PersistentFSRecordsLockFreeOverMMappedFile, IOException>(storagePath) { mappedFileStorage ->
        val recordsStorage = PersistentFSRecordsLockFreeOverMMappedFile(mappedFileStorage)

        if (acquireStorageOwnership) {
          try {
            val currentProcess = ProcessHandle.current()
            val currentProcessPid = currentProcess.pid().toInt()
            val acquiredBy = recordsStorage.tryAcquireExclusiveAccess(currentProcessPid,
                                                                      System.currentTimeMillis(),
                                                                      /*forcibly: */ false)
            if (acquiredBy.ownerProcessPid != currentProcessPid) {
              val ownerProcess = ProcessHandle.of(acquiredBy.ownerProcessPid.toLong()).getOrNull()
              if (ownerProcess != null && ownerProcess.isAlive) {
                val ownerStartedAtMs = ownerProcess.info().startInstant().getOrNull()?.toEpochMilli() ?: 0
                //OSes re-use process ids, so false positives (=pid collision) are possible: ownerProcess could be a new
                // process that re-uses original owner pid. To avoid such false-positives we check ownershipStarted timestamp
                // against owner process start time: false-owner must be started _after_ real owner has died, hence _after_
                // ownership timestamp:
                val likelyTrueOwner = (ownerStartedAtMs <= acquiredBy.ownershipAcquiredAtMs)
                if (likelyTrueOwner) {
                  val message = "Records storage [$storagePath] is in use by another process [${acquiredBy}, info: ${ownerProcess.info()}]"
                  val failIfUsedByAnotherProcess = SystemProperties.getBooleanProperty("vfs.fail-if-used-by-another-process", true)
                  if (failIfUsedByAnotherProcess) {
                    throw StorageAlreadyInUseException(message)
                  }
                  else {
                    FSRecords.LOG.error(message)
                  }
                }
                else {
                  FSRecords.LOG.warn("Records storage [$storagePath] was in use by process [${acquiredBy}] which is not exist now (wasn't closed properly/crashed?) -> re-acquiring forcibly (pid-collision with ${ownerProcess.info()} was successfully resolved)")
                }
              }
              else {
                FSRecords.LOG.warn("Records storage [$storagePath] was in use by process [${acquiredBy}] which is not exist now (wasn't closed properly/crashed?) -> re-acquiring forcibly")
              }

              //Previous owner process is terminated, i.e. storage wasn't closed properly on an app termination (crash?)
              // => acquire storage forcibly
              val acquiredByPidForcibly = recordsStorage.tryAcquireExclusiveAccess(currentProcessPid,
                                                                                   System.currentTimeMillis(),
                                                                                   /*forcibly: */ true)
              if (acquiredByPidForcibly.ownerProcessPid != currentProcessPid) {
                //yet another process acquired records concurrently => things get too complicated, just fail:
                val concurrentlyOwnedProcess = ProcessHandle.of(acquiredByPidForcibly.ownerProcessPid.toLong()).getOrNull()
                throw StorageAlreadyInUseException("Records storage [$storagePath] is in use by another process [pid: $acquiredByPidForcibly, info: ${concurrentlyOwnedProcess?.info()}]")
              }
            }
          }
          catch (e: Throwable) {
            //It seems like ProcessHandle methods could throw UOE in some cases
            // => try to still work even without acquireStorageOwnership safety umbrella:
            FSRecords.LOG.warn("VFS.acquireStorageOwnership is failed", e)
          }
        }

        recordsStorage
      }
  }

  /** For testing/benchmarking: serves as a reference point. Not a prod-level implementation! */
  data class InMemory(val maxRecordsCount: Int = (1 shl 24)) : PersistentFSRecordsStorageFactory(id = 2) {
    @Suppress("TestOnlyProblems")
    override fun open(storagePath: Path): PersistentFSRecordsOverInMemoryStorage = PersistentFSRecordsOverInMemoryStorage(storagePath, maxRecordsCount)
  }

  companion object {
    private var storageFactory = defaultFromSystemProperties()


    @JvmStatic
    fun storageImplementation(): PersistentFSRecordsStorageFactory = storageFactory

    @VisibleForTesting
    @JvmStatic
    @JvmName("setStorageImplementation")
    fun setStorageImplementation(value: PersistentFSRecordsStorageFactory) {
      storageFactory = value
    }

    @VisibleForTesting
    @JvmStatic
    @JvmName("resetStorageImplementation")
    fun resetStorageImplementation() {
      storageFactory = defaultFromSystemProperties()
    }

    private fun defaultFromSystemProperties(): PersistentFSRecordsStorageFactory {
      return when (System.getProperty("vfs.records-storage.impl", "OVER_MMAPPED_FILE")) {
        "IN_MEMORY" -> InMemory()
        else -> OverMMappedFile()
      }
    }
  }
}