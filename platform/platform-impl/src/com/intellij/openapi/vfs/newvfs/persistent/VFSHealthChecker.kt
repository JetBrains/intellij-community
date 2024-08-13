// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.ide.ApplicationActivity
import com.intellij.ide.IdleTracker
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.JulLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags.CHILDREN_CACHED
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags.IS_DIRECTORY
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.CHECK_ORPHAN_RECORDS
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.HEALTH_CHECKING_ENABLED
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.HEALTH_CHECKING_PERIOD_MS
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.HEALTH_CHECKING_START_DELAY_MS
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.MAX_CHILDREN_TO_LOG
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.MAX_SINGLE_ERROR_LOGS_BEFORE_THROTTLE
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.WRAP_HEALTH_CHECK_IN_READ_ACTION
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.BitUtil
import com.intellij.util.SystemProperties.getBooleanProperty
import com.intellij.util.SystemProperties.getIntProperty
import com.intellij.util.io.DataEnumerator
import com.intellij.util.io.DataEnumeratorEx
import com.intellij.util.io.PowerStatus
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.take
import java.nio.file.Paths
import java.util.logging.ConsoleHandler
import java.util.logging.Level
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit.MILLISECONDS
import kotlin.time.toDuration

private val LOG: Logger
  get() = FSRecords.LOG

private object VFSHealthCheckerConstants {
  val HEALTH_CHECKING_ENABLED = getBooleanProperty("vfs.health-check.enabled",
                                                   !ApplicationManager.getApplication().isUnitTestMode)

  val HEALTH_CHECKING_PERIOD_MS = getIntProperty("vfs.health-check.checking-period-ms",
                                                 if (ApplicationManager.getApplication().isEAP)
                                                   1.hours.inWholeMilliseconds.toInt()
                                                 else
                                                   12.hours.inWholeMilliseconds.toInt()
  )

  /** 10min in most cases enough for the initial storm of requests to VFS (scanning/indexing/etc)
   *  to finish, so VFS _likely_ +/- settles down after that. */
  val HEALTH_CHECKING_START_DELAY_MS = getIntProperty("vfs.health-check.checking-start-delay-ms",
                                                      10.minutes.inWholeMilliseconds.toInt())


  /** Wrap the main part of health-check (fs-records vs secondary storages, like name/content/attributes...) in
   *  RA. Since VFS modifications are done under WA, checking consistency without RA is generally incorrect
   *  because the checker could see intermediate inconsistent results.
   *  Acquiring RA for each file record could be costly, and I want HealthCheck to be un-intrusive, so
   *  it is initially switched off -- I expect it to be switched on by default, eventually, after it proves itself
   *  to be safe */
  val WRAP_HEALTH_CHECK_IN_READ_ACTION = getBooleanProperty("vfs.health-check.wrap-in-read-action", true)

  /**
   * May slow down scanning significantly, hence dedicated property to control.
   * Default: false, since orphan records appear to be quite common so far.
   */
  val CHECK_ORPHAN_RECORDS = getBooleanProperty("vfs.health-check.check-orphan-records", false)

  /** How many children to log at max with orphan records reporting */
  val MAX_CHILDREN_TO_LOG = getIntProperty("vfs.health-check.max-children-to-log", 16)

  val MAX_SINGLE_ERROR_LOGS_BEFORE_THROTTLE = getIntProperty("vfs.health-check.max-single-error-logs", 128)
}

private class VFSHealthCheckServiceStarter : ApplicationActivity {
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
             "and each following $HEALTH_CHECKING_PERIOD_MS ms, wrap in RA: ${WRAP_HEALTH_CHECK_IN_READ_ACTION}")

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
    val checkHealthReport = try {
      checker.checkHealth(CHECK_ORPHAN_RECORDS)
    }
    catch (e: AlreadyDisposedException) {
      return
    }

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

/**
 * Performs VFS self-consistency checks.
 * E.g. fields have reasonable values, all the references (ids) are valid and could be resolved, and so on.
 */
class VFSHealthChecker(private val impl: FSRecordsImpl,
                       private val log: Logger = LOG) {

  constructor() : this(FSRecords.getInstance(), FSRecords.LOG)

  suspend fun checkHealth(checkForOrphanRecords: Boolean = CHECK_ORPHAN_RECORDS): VFSHealthCheckReport {
    log.info("Checking VFS started")
    val startedAtNs = System.nanoTime()
    val fileRecordsReport = verifyFileRecords(checkForOrphanRecords)
    val rootsReport = verifyRoots()
    val namesEnumeratorReport = verifyNamesEnumerator()
    val contentEnumeratorReport = verifyContentEnumerator()

    val finishedAtNs = System.nanoTime()
    val vfsHealthCheckReport = VFSHealthCheckReport(recordsReport = fileRecordsReport,
                                                    namesEnumeratorReport = namesEnumeratorReport,
                                                    rootsReport = rootsReport,
                                                    contentEnumeratorReport = contentEnumeratorReport,
                                                    timeTaken = (finishedAtNs - startedAtNs).nanoseconds)
    if (vfsHealthCheckReport.healthy) {
      log.info("Checking VFS finished (healthy): $vfsHealthCheckReport")
    }
    else {
      log.warn("Checking VFS finished (non-healthy): $vfsHealthCheckReport")
    }

    return vfsHealthCheckReport
  }

  private suspend fun verifyFileRecords(checkForOrphanRecords: Boolean): VFSHealthCheckReport.FileRecordsReport {
    val connection = impl.connection()
    val fileRecords = connection.records
    val namesEnumerator = connection.names
    val contentsStorage = connection.contents

    val maxAllocatedID = fileRecords.maxAllocatedID()

    val report = VFSHealthCheckReport.FileRecordsReport()
    val allRoots = IntOpenHashSet(impl.listRoots())
    return report.apply {
      val invalidFlagsMask = PersistentFS.Flags.getAllValidFlags().inv()
      for (fileId in FSRecords.MIN_REGULAR_FILE_ID..maxAllocatedID) {
        val checkSingleFileTask = task@{
          try {
            val nameId = fileRecords.getNameId(fileId)
            val parentId = fileRecords.getParent(fileId)
            val flags = fileRecords.getFlags(fileId)
            val attributeRecordId = fileRecords.getAttributeRecordId(fileId)
            val contentId = fileRecords.getContentRecordId(fileId)
            val length = fileRecords.getLength(fileId)
            val timestamp = fileRecords.getTimestamp(fileId)

            fileRecordsChecked = fileId

            if (flags and invalidFlagsMask != 0) {
              generalErrors++.alsoLogThrottled("file[#$fileId]: invalid flags: ${Integer.toBinaryString(flags)}")
            }

            if (PersistentFSRecordAccessor.hasDeletedFlag(flags)) {
              fileRecordsDeleted++
              return@task
            }

            //if (length < 0) { //RC: length is regularly -1
            //  LOG.warn("file[#" + fileId + "]: length(=" + length + ") is negative -> suspicious");
            //}

            if (nameId == DataEnumeratorEx.NULL_ID) {
              nullNameIds++.alsoLogThrottled("file[#$fileId]: nameId is not set (NULL_ID) -> file record is incorrect (broken?)")
            }
            val fileName = namesEnumerator.valueOf(nameId)
            if (fileName == null) {
              unresolvableNameIds++.alsoLogThrottled(
                "file[#$fileId]: name[#$nameId] does not exist (null)! -> names enumerator is inconsistent (broken?)"
              )
            }

            var attributesAreResolvable: Boolean
            try {
              connection.attributes.checkAttributeRecordSanity(fileId, attributeRecordId)
              attributesAreResolvable = true
            }
            catch (t: Throwable) {
              unresolvableAttributesIds++.alsoLogThrottled(
                "file[#$fileId]{$fileName}: attribute[#$attributeRecordId] can't be read", t
              )
              attributesAreResolvable = false
            }


            if (contentId != DataEnumeratorEx.NULL_ID) {
              notNullContentIds++
              try {
                contentsStorage.checkRecord(contentId, false)
              }
              catch (e: Throwable) {
                unresolvableContentIds++.alsoLogThrottled(
                  "file[#$fileId]{$fileName}: content[#$contentId] can't be read, or inconsistent", e
                )
              }
            } //else: it is ok, contentId _could_ be NULL_ID

            if (parentId == FSRecords.NULL_FILE_ID) {
              if (!allRoots.contains(fileId)) {
                nullParents++.alsoLogThrottled(
                  "file[#$fileId]{$fileName}: not in ROOTS, but parentId is not set (NULL_ID) -> non-ROOTS must have parents"
                )
              }
            }
            else {
              val parentFlags = fileRecords.getFlags(parentId)
              val parentIsDirectory = BitUtil.isSet(parentFlags, IS_DIRECTORY)
              if (!parentIsDirectory) {
                inconsistentParentChildRelationships++.alsoLogThrottled(
                  "file[#$fileId]{$fileName}: parent[#$parentId] is !directory (flags: ${Integer.toBinaryString(parentFlags)})"
                )
              }

              if (attributesAreResolvable) { //'children' are part of 'file attributes'
                if (checkForOrphanRecords) {
                  checkRecordIsOrphan(fileRecords, fileId, parentId, parentFlags, fileName)
                }
              }
            }

            if (attributesAreResolvable) { //'children' are part of 'file attributes'
              val isDirectory = BitUtil.isSet(flags, IS_DIRECTORY)

              val children = try {
                impl.listIds(fileId)
              }
              catch (e: Throwable) {
                //'childId is out of allocated range' now also falls here: 'cos .listIds() checks childId, and
                // throws CorruptedException in such cases
                generalErrors++.alsoLogThrottled("file[#$fileId]{$fileName}: error accessing children", e)
                IntArray(0)
              }

              if (isDirectory) {
                for (i in children.indices) {
                  childrenChecked++
                  val childId = children[i]
                  val childParentId = fileRecords.getParent(childId)
                  if (fileId != childParentId) {
                    inconsistentParentChildRelationships++.alsoLogThrottled(
                      "file[#$fileId]{$fileName}: children[$i][#$childId].parent[=#$childParentId] != this " +
                      "-> parent-child relationship is inconsistent (records are broken?)"
                    )
                  }
                }
              }
              else if (children.isNotEmpty()) {
                //MAYBE RC: dedicated counter for that kind of errors?
                inconsistentParentChildRelationships++.alsoLogThrottled(
                  "file[#$fileId]{$fileName}: !directory (flags: ${Integer.toBinaryString(flags)}) but has children(${children.size})"
                )
              }
            }
          }
          catch (t: Throwable) {
            generalErrors++.alsoLogThrottled("file[#$fileId]: unhandled exception while checking", t)
          }
        }

        if (WRAP_HEALTH_CHECK_IN_READ_ACTION) {
          readAction(checkSingleFileTask)
        }
        else {
          checkSingleFileTask()
        }
      }
    }
  }

  private fun VFSHealthCheckReport.FileRecordsReport.checkRecordIsOrphan(fileRecords: PersistentFSRecordsStorage,
                                                                         fileId: Int,
                                                                         parentId: Int,
                                                                         parentFlags: Int,
                                                                         fileName: String?) {
    if (BitUtil.isSet(parentFlags, CHILDREN_CACHED)) {
      try {
        //MAYBE RC: use .listIdsUnchecked() method -- to not trigger VFS rebuild?
        val childrenOfParent = impl.listIds(parentId)
        if (childrenOfParent.indexOf(fileId) < 0) {
          //Check: is it an orphan _duplicate_ -- is there a non-orphan child with the same name?
          val fileNameId = fileRecords.getNameId(fileId)
          val hasChildWithSameName = childrenOfParent
            .any { childId -> fileRecords.getNameId(childId) == fileNameId }


          val childrenPrefix = if (childrenOfParent.size > MAX_CHILDREN_TO_LOG)
            "first ${MAX_CHILDREN_TO_LOG} of ${childrenOfParent.size}: "
          else
            ""
          //MAYBE RC: dedicated counter?
          inconsistentParentChildRelationships++.alsoLogThrottled(
            "file[#$fileId]{$fileName}: record is orphan, " +
            ".parent[#$parentId].children($childrenPrefix${childrenOfParent.joinToString(limit = MAX_CHILDREN_TO_LOG)}) " +
            "doesn't contain it, " +
            if (hasChildWithSameName) "but there is non-orphan child with same name."
            else "and there are NO non-orphan children with the same name."
          )
        }
      }
      catch (e: Throwable) {
        generalErrors++.alsoLogThrottled("file[#$fileId]{$fileName}.parent[#$parentId]: error accessing children", e)
      }
    }
  }

  private suspend fun verifyRoots(): VFSHealthCheckReport.RootsReport {
    val report = VFSHealthCheckReport.RootsReport(0, 0, 0)
    return report.apply {
      try {
        readAction {
          val rootIds = impl.treeAccessor().listRoots()
          val records = impl.connection().records
          val maxAllocatedID = records.maxAllocatedID()
          rootsCount = rootIds.size

          for (rootId in rootIds) {
            if (rootId < FSRecords.MIN_REGULAR_FILE_ID || rootId > maxAllocatedID) {
              //MAYBE RC: dedicated counter for that kind of errors?
              generalErrors++.alsoLogThrottled(
                "root[#$rootId]: is outside of allocated IDs range [${FSRecords.MIN_REGULAR_FILE_ID}..$maxAllocatedID]")
              continue
            }
            val rootParentId = records.getParent(rootId)
            if (rootParentId != FSRecords.NULL_FILE_ID) {
              rootsWithParents++.alsoLogThrottled("root[#$rootId]: parentId[#$rootParentId] != ${FSRecords.NULL_FILE_ID} -> inconsistency")
            }

            val flags = records.getFlags(rootId)
            if (PersistentFSRecordAccessor.hasDeletedFlag(flags)) {
              rootsDeletedButNotRemoved++.alsoLogThrottled(
                "root[#$rootId]: record is deleted (flags: ${Integer.toBinaryString(flags)}) but not removed from the roots")
            }
          }
        }
      }
      catch (t: Throwable) {
        generalErrors++.alsoLogThrottled("verifyRoots: can't do something", t)
      }
    }
  }

  private fun verifyNamesEnumerator(): VFSHealthCheckReport.NamesEnumeratorReport {
    val namesEnumerator = impl.connection().names
    val report = VFSHealthCheckReport.NamesEnumeratorReport()
    try {
      namesEnumerator.forEach { id, name ->
        try {
          report.namesChecked++
          val nameId = namesEnumerator.tryEnumerate(name)
          if (nameId == DataEnumerator.NULL_ID) {
            report.namesResolvedToNull++.alsoLogThrottled(
              "name[$name] enumerated to NULL -> namesEnumerator is corrupted")
            return@forEach true
          }
          val nameResolved = namesEnumerator.valueOf(nameId)
          if (nameResolved == null) {
            report.idsResolvedToNull++.alsoLogThrottled(
              "name[$name]: enumerated to nameId(=$nameId), resolved back to null -> namesEnumerator is corrupted")
            return@forEach true
          }
          if (name != nameResolved) {
            report.inconsistentNames++.alsoLogThrottled(
              "name[$name]: enumerated to nameId(=$nameId), resolved back to different name [$nameResolved] " +
              "-> namesEnumerator is corrupted")
          }
        }
        catch (e: Throwable) {
          report.generalErrors++.alsoLogThrottled("name[$name]: exception while checking -> namesEnumerator is corrupted: ${e.message}")
        }
        return@forEach true
      }
    }
    catch (e: Throwable) {
      report.generalErrors++.alsoLogThrottled("Error to verify namesEnumerator", e)
    }
    return report
  }

  private fun verifyContentEnumerator(): VFSHealthCheckReport.ContentEnumeratorReport {
    val report = VFSHealthCheckReport.ContentEnumeratorReport(0, 0)

    val connection = impl.connection()
    val contentsStorage = connection.contents
    val contentRecordsIterator = contentsStorage.createRecordIdIterator()
    while (contentRecordsIterator.hasNextId()) {
      report.contentRecordsChecked ++
      val contentId = contentRecordsIterator.nextId()
      try {
        contentsStorage.checkRecord(contentId, false)
      }
      catch (e: Throwable) {
        report.generalErrors++.alsoLogThrottled(
          "contentId[#$contentId]: content record fails to read or inconsistent: ${e.message}")
      }
    }
    return report
  }


  data class VFSHealthCheckReport(val recordsReport: FileRecordsReport,
                                  val rootsReport: RootsReport,
                                  val namesEnumeratorReport: NamesEnumeratorReport,
                                  val contentEnumeratorReport: ContentEnumeratorReport,
                                  val timeTaken: Duration) {
    fun hasSameErrors(other: VFSHealthCheckReport): Boolean {
      return recordsReport.hasSameErrors(other.recordsReport)
             && rootsReport.hasSameErrors(other.rootsReport)
             && namesEnumeratorReport.hasSameErrors(other.namesEnumeratorReport)
             && contentEnumeratorReport.hasSameErrors(other.contentEnumeratorReport)
    }

    override fun toString(): String {
      return "VFSHealthCheckReport[healthy: $healthy]($recordsReport, $rootsReport, $namesEnumeratorReport, $contentEnumeratorReport){timeTaken=$timeTaken}"
    }

    val healthy: Boolean
      get() = recordsReport.healthy && rootsReport.healthy && namesEnumeratorReport.healthy && contentEnumeratorReport.healthy


    data class FileRecordsReport(var fileRecordsChecked: Int = 0,
                                 var fileRecordsDeleted: Int = 0,
                                 /* record.nameId = NULL_ID */
                                 var nullNameIds: Int = 0,
                                 /* nameEnumerator.valueOf(record.nameId) = null */
                                 var unresolvableNameIds: Int = 0,
                                 /* record.contentId != NULL_ID */
                                 var notNullContentIds: Int = 0,
                                 /* contentEnumerator.valueOf(record.contentId) = null */
                                 var unresolvableContentIds: Int = 0,
                                 /* failure to read attribute record from the attribute storage */
                                 var unresolvableAttributesIds: Int = 0,
                                 /* record.parentId = NULL_ID & record is not ROOT */
                                 var nullParents: Int = 0,
                                 var childrenChecked: Int = 0,
                                 /* record.children[].parent != record */
                                 var inconsistentParentChildRelationships: Int = 0,
                                 /* exceptions */
                                 var generalErrors: Int = 0) {
      val healthy: Boolean
        get() = nullNameIds == 0
                && unresolvableNameIds == 0
                && unresolvableAttributesIds == 0
                && unresolvableContentIds == 0
                && nullParents == 0
                && inconsistentParentChildRelationships == 0
                && generalErrors == 0

      fun hasSameErrors(other: FileRecordsReport): Boolean {
        return nullNameIds == other.nullNameIds
               && unresolvableNameIds == other.unresolvableNameIds
               && unresolvableAttributesIds == other.unresolvableAttributesIds
               && unresolvableContentIds == other.unresolvableContentIds
               && nullParents == other.nullParents
               && inconsistentParentChildRelationships == other.inconsistentParentChildRelationships
               && generalErrors == other.generalErrors
      }

      override fun toString(): String {
        return "FileRecordsReport[recordsChecked=$fileRecordsChecked, recordsDeleted=$fileRecordsDeleted, childrenChecked=$childrenChecked]{nullNameIds=$nullNameIds, unresolvableNameIds=$unresolvableNameIds, notNullContentIds=$notNullContentIds, unresolvableContentIds=$unresolvableContentIds, unresolvableAttributesIds=$unresolvableAttributesIds, nullParents=$nullParents, inconsistentParentChildRelationships=$inconsistentParentChildRelationships, generalErrors=$generalErrors)"
      }
    }

    data class NamesEnumeratorReport(var namesChecked: Int = 0,
                                     /* .tryEnumerate(existentNameId) = NULL_ID */
                                     var namesResolvedToNull: Int = 0,
                                     /* .valueOf(existentName) = null */
                                     var idsResolvedToNull: Int = 0,
                                     /* existent name -> tryEnumerate() -> .valueOf() -> different name */
                                     var inconsistentNames: Int = 0,
                                     /* tryEnumerate/valueOf/etc exceptions */
                                     var generalErrors: Int = 0) {
      val healthy: Boolean
        get() = namesResolvedToNull == 0
                && idsResolvedToNull == 0
                && inconsistentNames == 0
                && generalErrors == 0

      fun hasSameErrors(other: NamesEnumeratorReport): Boolean {
        return namesResolvedToNull == other.namesResolvedToNull
               && idsResolvedToNull == other.idsResolvedToNull
               && inconsistentNames == other.inconsistentNames
               && generalErrors == other.generalErrors
      }
    }

    data class RootsReport(var rootsCount: Int = 0,
                           /* root.parentId != NULL_ID */
                           var rootsWithParents: Int = 0,
                           /* root.isDeleted but still present in roots catalog*/
                           var rootsDeletedButNotRemoved: Int = 0,
                           /* exceptions */
                           var generalErrors: Int = 0) {
      val healthy: Boolean
        get() = rootsWithParents == 0
                && rootsDeletedButNotRemoved == 0
                && generalErrors == 0

      fun hasSameErrors(other: RootsReport): Boolean {
        return rootsWithParents == other.rootsWithParents
               && rootsDeletedButNotRemoved == other.rootsDeletedButNotRemoved
               && generalErrors == other.generalErrors
      }
    }

    data class ContentEnumeratorReport(
      var contentRecordsChecked: Int = 0,
      /* tryEnumerate/valueOf/etc exceptions */
      var generalErrors: Int = 0) {

      val healthy: Boolean
        get() = (generalErrors == 0)

      fun hasSameErrors(other: ContentEnumeratorReport): Boolean = (generalErrors == other.generalErrors)
    }
  }

  /**Log the message if errors count is < MAX_LOG_MESSAGES_BEFORE_THROTTLE, otherwise don't log */
  private fun Int.alsoLogThrottled(message: String) {
    val errorsCount = this
    if (errorsCount < MAX_SINGLE_ERROR_LOGS_BEFORE_THROTTLE) {
      log.info(message)
    }
    else if (errorsCount == MAX_SINGLE_ERROR_LOGS_BEFORE_THROTTLE) {
      log.info(
        message +
        "\n...$MAX_SINGLE_ERROR_LOGS_BEFORE_THROTTLE similar errors " +
        "-> continue counting, but don't log same errors anymore to not trash the log file"
      )
    }
  }

  /**Log the message if errors count is < MAX_LOG_MESSAGES_BEFORE_THROTTLE, otherwise don't log */
  private fun Int.alsoLogThrottled(message: String, error: Throwable) {
    val errorsCount = this
    if (errorsCount < MAX_SINGLE_ERROR_LOGS_BEFORE_THROTTLE) {
      log.info(message, error)
    }
    else if (errorsCount == MAX_SINGLE_ERROR_LOGS_BEFORE_THROTTLE) {
      log.info(
        message + "\n...$MAX_SINGLE_ERROR_LOGS_BEFORE_THROTTLE similar errors " +
        "-> continue counting, but don't log same errors anymore to not trash the log file",
        error
      )
    }
  }
}


/**
 * Runs health-check on given VFS files (<path-to-VFS-dir>) and prints report.
 * BEWARE: it could change VFS files, e.g. rebuild some of them -- so always make a backup.
 */
fun main(args: Array<String>) {
  if (args.isEmpty()) {
    System.err.println("Usage: <path-to-VFS-dir>")
    return
  }
  val path = Paths.get(args[0])
  println("Checking VFS [$path]")
  val records = FSRecordsImpl.connect(path) { _, error ->
    throw error
  }
  println("VFS roots:")
  records.forEachRoot { rootUrl, rootId ->
    println("\troot[$rootId]: url: '$rootUrl")
  }

  val log = configureLogger()
  runBlocking {
    val checkupReport = VFSHealthChecker(records, log).checkHealth(checkForOrphanRecords = true)
    println(checkupReport)
  }

  exitProcess(0) //too many non-daemon threads
}

private fun configureLogger(): JulLogger {
  val julLogger = java.util.logging.Logger.getLogger("VFSHealthChecker")
  julLogger.level = Level.INFO
  julLogger.useParentHandlers = false
  val handler = ConsoleHandler()
  handler.formatter = IdeaLogRecordFormatter()
  julLogger.addHandler(handler)
  return JulLogger(julLogger)
}

