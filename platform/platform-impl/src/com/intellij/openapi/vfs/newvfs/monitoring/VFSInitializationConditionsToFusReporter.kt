// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.monitoring

import com.intellij.ide.util.RunOnceUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import com.intellij.openapi.vfs.newvfs.persistent.VFSInitException
import com.intellij.openapi.vfs.newvfs.persistent.VFSInitException.ErrorCategory.*
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Reports VFS initialization conditions to FUS -- once, on app start.
 * <p/>
 * 'Initialization conditions' are: VFS version, init attempts, and cause for VFS rebuild, if any
 */
private class VFSInitializationConditionsToFusReporter : ProjectActivity {
  override suspend fun execute(project: Project) {
    RunOnceUtil.runOnceForApp(VFSInitializationConditionsToFusReporter::class.java.simpleName) {
      reportToFUS()
    }
  }

  private fun reportToFUS() {
    val vfs = FSRecords.getInstance()

    val vfsImplementationVersion = vfs.version
    val creationTimestamp = vfs.creationTimestamp
    val initializationResult = vfs.initializationResult()
    val recoveryInfo = vfs.connection().recoveryInfo()
    val initializationFailures = initializationResult.attemptsFailures
    val wasCreateANew = initializationResult.vfsCreatedAnew
    val totalInitializationDurationMs = initializationResult.totalInitializationDurationNs.nanoseconds.inWholeMilliseconds

    val vfsInitKind = if (initializationFailures.isEmpty()) {
      if (wasCreateANew) {
        VFSInitKind.CREATED_EMPTY
      }
      else {
        if (recoveryInfo.recoveredErrors.isEmpty()) {
          VFSInitKind.LOADED_NORMALLY
        }
        else {
          VFSInitKind.RECOVERED
        }
      }
    }
    else when (initializationFailures.filterIsInstance<VFSInitException>()
      .map { it.category() }.firstOrNull()) {

      null -> VFSInitKind.UNRECOGNIZED
      UNRECOGNIZED -> VFSInitKind.UNRECOGNIZED
      HAS_ERRORS_IN_PREVIOUS_SESSION -> VFSInitKind.HAS_ERRORS_IN_PREVIOUS_SESSION

      SCHEDULED_REBUILD -> VFSInitKind.SCHEDULED_REBUILD

      NOT_CLOSED_PROPERLY -> VFSInitKind.NOT_CLOSED_PROPERLY

      IMPL_VERSION_MISMATCH -> VFSInitKind.IMPL_VERSION_MISMATCH

      NAME_STORAGE_INCOMPLETE -> VFSInitKind.NAME_STORAGE_INCOMPLETE

      ATTRIBUTES_STORAGE_CORRUPTED -> VFSInitKind.ATTRIBUTES_STORAGE_CORRUPTED

      CONTENT_STORAGES_INCOMPLETE -> VFSInitKind.CONTENT_STORAGES_INCOMPLETE
      CONTENT_STORAGES_NOT_MATCH -> VFSInitKind.CONTENT_STORAGES_NOT_MATCH
    }

    val errorsHappenedDuringInit =
      initializationFailures.filterIsInstance<VFSInitException>()
        .map { it.category() } +
      recoveryInfo.recoveredErrors.map { it.category() }


    VfsUsageCollector.logVfsInitialization(
      vfsImplementationVersion,
      creationTimestamp,
      vfsInitKind,
      errorsHappenedDuringInit,
      initializationFailures.size + 1,
      totalInitializationDurationMs
    )
  }

  /**
   * Kind of VFS initialization happened.
   *
   * Better schema would be 2-levels:
   * init_kind= CREATED_EMPTY | REGULAR | RECOVERED
   * errors   = SCHEDULED_REBUILD|NOT_CLOSED_PROPERLY|VERSION_MISMATCH|...
   */
  enum class VFSInitKind {
    /** VFS was created from scratch */
    CREATED_EMPTY,

    /** VFS was loaded from already existing files, without any issue */
    LOADED_NORMALLY,


    /** VFS was loaded from already existing files, with some errors fixed along the way */
    RECOVERED,

    /** VFS was cleared and rebuild from scratch because: rebuild marker was found */
    SCHEDULED_REBUILD,

    /** VFS was cleared and rebuild from scratch because: application wasn't closed properly,
     *  VFS storages are fractured */
    NOT_CLOSED_PROPERLY,

    /** VFS error was detected in a previous session (see [FSRecords.handleError]) */
    HAS_ERRORS_IN_PREVIOUS_SESSION,

    /** VFS was cleared and rebuild from scratch because: current VFS impl (code)
     *  version != VFS on-disk format version */
    IMPL_VERSION_MISMATCH,

    /** VFS was cleared and rebuild from scratch because: name storage is not able to resolve existing reference */
    NAME_STORAGE_INCOMPLETE,

    /** Attributes storage has corrupted record(s) */
    ATTRIBUTES_STORAGE_CORRUPTED,

    /** Content and ContentHashes storages are not match with each other */
    CONTENT_STORAGES_NOT_MATCH,

    /** Content or ContentHashes storages are not able to resolve existing reference */
    CONTENT_STORAGES_INCOMPLETE,

    /** Everything else is not covered by the specific constants above */
    UNRECOGNIZED
  }
}
                                                                