// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.IdleTracker
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.CHECK_ORPHAN_RECORDS
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.HEALTH_CHECKING_ENABLED
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.HEALTH_CHECKING_PERIOD_MS
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.HEALTH_CHECKING_START_DELAY_MS
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.WRAP_HEALTH_CHECK_IN_READ_ACTION
import com.intellij.util.io.PowerStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.minutes
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.toDuration

private val LOG = Logger.getInstance(FSRecords::class.java)

internal class VFSHealthCheckServiceStarter : ApplicationActivity {
  init {
    if (!HEALTH_CHECKING_ENABLED) {
      LOG.info("VFS health-check disabled")
      throw ExtensionNotApplicableException.create()
    }

    if (HEALTH_CHECKING_PERIOD_MS < 1.minutes.inWholeMilliseconds) {
      LOG.warn("VFS health-check is NOT enabled: incorrect period $HEALTH_CHECKING_PERIOD_MS ms, must be >= 1 min")
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute() {
    LOG.info("VFS health-check enabled: first after $HEALTH_CHECKING_START_DELAY_MS ms, " +
             "and each following $HEALTH_CHECKING_PERIOD_MS ms, wrap in RA: $WRAP_HEALTH_CHECK_IN_READ_ACTION")

    delay(HEALTH_CHECKING_START_DELAY_MS.toDuration(MILLISECONDS))

    coroutineScope {
      val checkingPeriod = HEALTH_CHECKING_PERIOD_MS.toDuration(MILLISECONDS)
      while (isActive && !FSRecords.getInstance().isClosed) {
        //MAYBE RC: track FSRecords.getLocalModCount() to run the check only if there are enough changes
        //          since the last check.
        if (!PowerSaveMode.isEnabled()) {
          //HealthCheck is not really an urgent process -- it is OK to delay a minute or two after
          // the scheduled time. On the other side, health-check takes anywhere from 3 sec to 1.5 min
          // depending on load, and could slow down the IDE operations in the process.
          // We don't want to disturb the user with health-check, so we delay the check until the user
          // is idle for at least 1 min straight -- which gives us a good probability the health-check
          // will finish before the user returns from its retreat.
          @OptIn(FlowPreview::class)
          IdleTracker.getInstance().events
            .debounce(1.minutes)
            .take(1)
            .collect {
              withContext(Dispatchers.IO) {
                //MAYBE RC: show a progress bar -- or better not bother user?
                doCheckupAndReportResults()
              }
            }
        }
        else {
          LOG.info("VFS health-check skipped: PowerSaveMode is enabled")
        }

        delay(checkingPeriod)

        //MAYBE RC: this seems useless -- i.e. VFS h-check is ~10-60sec long once/(few) hours,
        //          which is negligible comparing to (GC/JIT/bg tasks) load accumulated
        //          over the same few hours
        if (PowerStatus.getPowerStatus() == PowerStatus.BATTERY) {
          LOG.info("VFS health-check delayed: power source is battery")
          delay(checkingPeriod) // make it twice rarer
        }
      }
    }
  }

  private suspend fun doCheckupAndReportResults() {
    val fsRecordsImpl = FSRecords.getInstance()
    if (fsRecordsImpl.isClosed) {
      return
    }
    val checker = VFSHealthChecker(fsRecordsImpl, LOG)
    val checkHealthReport = checker.checkHealth(CHECK_ORPHAN_RECORDS)

    VfsUsageCollector.logVfsHealthCheck(
      fsRecordsImpl.creationTimestamp,
      checkHealthReport.timeTaken.inWholeMilliseconds,

      checkHealthReport.recordsReport.fileRecordsChecked,
      checkHealthReport.recordsReport.fileRecordsDeleted,
      checkHealthReport.recordsReport.nullNameIds,
      checkHealthReport.recordsReport.unresolvableNameIds,
      checkHealthReport.recordsReport.unresolvableAttributesIds,
      checkHealthReport.recordsReport.notNullContentIds,
      checkHealthReport.recordsReport.unresolvableContentIds,
      checkHealthReport.recordsReport.nullParents,
      checkHealthReport.recordsReport.childrenChecked,
      checkHealthReport.recordsReport.inconsistentParentChildRelationships,
      checkHealthReport.recordsReport.generalErrors,

      checkHealthReport.namesEnumeratorReport.namesChecked,
      checkHealthReport.namesEnumeratorReport.namesResolvedToNull,
      checkHealthReport.namesEnumeratorReport.idsResolvedToNull,
      checkHealthReport.namesEnumeratorReport.inconsistentNames,
      checkHealthReport.namesEnumeratorReport.generalErrors,

      checkHealthReport.rootsReport.rootsCount,
      checkHealthReport.rootsReport.rootsWithParents,
      checkHealthReport.rootsReport.rootsDeletedButNotRemoved,
      checkHealthReport.rootsReport.generalErrors,

      checkHealthReport.contentEnumeratorReport.contentRecordsChecked,
      checkHealthReport.contentEnumeratorReport.generalErrors
    )

    //MAYBE RC: create VFS_BROKEN_MARKER?
  }
}
