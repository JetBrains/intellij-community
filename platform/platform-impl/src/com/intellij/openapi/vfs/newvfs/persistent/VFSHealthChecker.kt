// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.ide.PowerSaveMode
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.IdeaLogRecordFormatter
import com.intellij.openapi.diagnostic.JulLogger
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags.CHILDREN_CACHED
import com.intellij.openapi.vfs.newvfs.persistent.PersistentFS.Flags.IS_DIRECTORY
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.CHECK_ORPHAN_RECORDS
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.HEALTH_CHECKING_ENABLED
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.HEALTH_CHECKING_PERIOD_MS
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.HEALTH_CHECKING_START_DELAY_MS
import com.intellij.openapi.vfs.newvfs.persistent.VFSHealthCheckerConstants.LOG
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.BitUtil
import com.intellij.util.SystemProperties.getBooleanProperty
import com.intellij.util.SystemProperties.getIntProperty
import com.intellij.util.io.DataEnumeratorEx
import com.intellij.util.io.PowerStatus
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import kotlinx.coroutines.*
import java.io.IOException
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


object VFSHealthCheckerConstants {
  @JvmStatic
  val HEALTH_CHECKING_ENABLED = getBooleanProperty("vfs.health-check.enabled",
                                                   ApplicationManager.getApplication().isEAP)

  @JvmStatic
  val HEALTH_CHECKING_PERIOD_MS = getIntProperty("vfs.health-check.checking-period-ms",
                                                 1.hours.inWholeMilliseconds.toInt())

  /** 10min in most cases enough for the initial storm of requests to VFS (scanning/indexing/etc)
   *  to finish, so VFS _likely_ +/- settles down after that.
   */
  @JvmStatic
  val HEALTH_CHECKING_START_DELAY_MS = getIntProperty("vfs.health-check.checking-start-delay-ms",
                                                      10.minutes.inWholeMilliseconds.toInt())


  /** May take quite a time, hence disabled by default */
  @JvmStatic
  val CHECK_ORPHAN_RECORDS = getBooleanProperty("vfs.health-check.check-orphan-records", false)

  @JvmStatic
  val LOG: Logger = FSRecords.LOG
}


class VFSHealthCheckServiceStarter : ApplicationInitializedListener {
  override suspend fun execute(asyncScope: CoroutineScope) {
    if (HEALTH_CHECKING_ENABLED) {
      if (HEALTH_CHECKING_PERIOD_MS < 1.minutes.inWholeMilliseconds) {
        LOG.warn("VFS health-check is NOT enabled: incorrect period $HEALTH_CHECKING_PERIOD_MS ms, must be >= 1 min")
        return
      }
      LOG.info("VFS health-check enabled: first after $HEALTH_CHECKING_START_DELAY_MS ms, and each following $HEALTH_CHECKING_PERIOD_MS ms")

      asyncScope.launch(Dispatchers.Default) {
        delay(HEALTH_CHECKING_START_DELAY_MS.toDuration(MILLISECONDS))

        val checkingPeriod = HEALTH_CHECKING_PERIOD_MS.toDuration(MILLISECONDS)
        while (isActive && !FSRecords.implOrFail().isDisposed) {

          //MAYBE RC: track FSRecords.getLocalModCount() to run the check only if there are enough changes
          //          since the last check.
          //MAYBE RC: use IdleTracker.getInstance().events to launch checkup on next _idle_ period?

          if (!PowerSaveMode.isEnabled()) {
            launch(Dispatchers.IO) {
              //MAYBE RC: show a progress bar -- or better not bother user?
              doCheckupAndReportResults()
            }
          }
          else {
            LOG.info("VFS health-check skipped: PowerSaveMode is enabled")
          }

          delay(checkingPeriod)

          if (PowerStatus.getPowerStatus() == PowerStatus.BATTERY) {
            LOG.info("VFS health-check delayed: power source is battery")
            delay(checkingPeriod) //make it twice rarer
          }
        }
      }
    }
    else {
      LOG.info("VFS health-check disabled")
    }
  }

  private fun doCheckupAndReportResults() {
    val fsRecordsImpl = FSRecords.implOrFail()
    if (fsRecordsImpl.isDisposed) {
      return
    }
    val checker = VFSHealthChecker(fsRecordsImpl, LOG)
    val checkHealthReport = try {
      checker.checkHealth()
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

  fun checkHealth(): VFSHealthCheckReport {
    log.info("Checking VFS started")
    val startedAtNs = System.nanoTime()
    val fileRecordsReport = verifyFileRecords()
    val rootsReport = verifyRoots()
    val namesEnumeratorReport = verifyNamesEnumerator()
    val contentEnumeratorReport = verifyContentEnumerator()

    val finishedAtNs = System.nanoTime()
    val vfsHealthCheckReport = VFSHealthCheckReport(recordsReport = fileRecordsReport,
                                                    namesEnumeratorReport = namesEnumeratorReport,
                                                    rootsReport = rootsReport,
                                                    contentEnumeratorReport = contentEnumeratorReport,
                                                    timeTaken = (finishedAtNs - startedAtNs).nanoseconds)
    log.info("Checking VFS finished: $vfsHealthCheckReport")

    return vfsHealthCheckReport
  }

  private fun verifyFileRecords(): VFSHealthCheckReport.FileRecordsReport {
    val connection = impl.connection()
    val fileRecords = connection.records
    val namesEnumerator = connection.names
    val contentHashesEnumerator = connection.contentHashesEnumerator
    val contentsStorage = connection.contents

    val maxAllocatedID = fileRecords.maxAllocatedID()

    val report = VFSHealthCheckReport.FileRecordsReport()
    val allRoots = IntOpenHashSet(impl.listRoots())
    return report.apply {
      val invalidFlagsMask = PersistentFS.Flags.getAllValidFlags().inv()
      for (fileId in FSRecords.MIN_REGULAR_FILE_ID..maxAllocatedID) {
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
            generalErrors++  //MAYBE RC: add specific error counter?
            log.info("file[#$fileId]: invalid flags: ${Integer.toBinaryString(flags)}")
          }

          if (PersistentFSRecordAccessor.hasDeletedFlag(flags)) {
            fileRecordsDeleted++
            continue
          }

          //if (length < 0) { //RC: length is regularly -1
          //  LOG.warn("file[#" + fileId + "]: length(=" + length + ") is negative -> suspicious");
          //}

          if (nameId == DataEnumeratorEx.NULL_ID) {
            nullNameIds++
            log.info("file[#$fileId]: nameId is not set (NULL_ID) -> file record is incorrect (broken?)")
          }
          val fileName = namesEnumerator.valueOf(nameId)
          if (fileName == null) {
            unresolvableNameIds++
            log.info("file[#$fileId]: name[#$nameId] does not exist (null)! -> names enumerator is inconsistent (broken?)")
          }


          if (contentId != DataEnumeratorEx.NULL_ID) {
            notNullContentIds++
            val contentHash = contentHashesEnumerator.valueOf(contentId)
            if (contentHash == null) {
              unresolvableContentIds++
              log.info("file[#$fileId]{$fileName}: contentHash[#$contentId] does not exist (null)! " +
                       "-> content hashes enumerator is inconsistent (broken?)")
            }
            try {
              //just ensure storage has the record 
              contentsStorage.readStream(contentId).use { _ -> }
            }
            catch (e: IOException) {
              unresolvableContentIds++
              log.info("file[#$fileId]{$fileName}: content[#$contentId] can't be read", e)
            }
          } //else: it is ok, contentId _could_ be NULL_ID

          if (parentId == DataEnumeratorEx.NULL_ID) {
            if (!allRoots.contains(fileId)) {
              nullParents++
              log.info("file[#$fileId]{$fileName}: not in ROOTS, but parentId is not set (NULL_ID) -> non-ROOTS must have parents")
            }
          }
          else {
            if (CHECK_ORPHAN_RECORDS) {
              if (BitUtil.isSet(flags, CHILDREN_CACHED)) {
                val childrenOfParent = impl.listIds(parentId)
                if (childrenOfParent.indexOf(fileId) < 0) {
                  inconsistentParentChildRelationships++ //MAYBE RC: dedicated counter?
                  log.info("file[#$fileId]{$fileName}: record is orphan, .parent.children(first 64: ${
                    childrenOfParent.take(64)
                  }...) doesn't contain it!")
                }
              }
            }
          }

          val isDirectory = BitUtil.isSet(flags, IS_DIRECTORY)

          val children = impl.listIds(fileId)
          if (isDirectory) {
            for (i in children.indices) {
              childrenChecked++
              val childId = children[i]
              //re-request maxAllocatedID before loop so racing changes will be accounted for:
              val maxAllocatedID = fileRecords.maxAllocatedID()
              if (childId < FSRecords.MIN_REGULAR_FILE_ID || childId > maxAllocatedID) {
                generalErrors++ //MAYBE RC: dedicated counter?
                log.info("file[#$fileId]{$fileName}: children[$i][#$childId] " +
                         "is outside of allocated IDs range [${FSRecords.MIN_REGULAR_FILE_ID}..$maxAllocatedID]")
              }
              else {
                val childParentId = fileRecords.getParent(childId)
                if (fileId != childParentId) {
                  inconsistentParentChildRelationships++
                  log.info("file[#$fileId]{$fileName}: children[$i][#$childId].parent[=#$childParentId] != this " +
                           "-> parent-child relationship is inconsistent (records are broken?)")
                }
              }
            }
          }
          else if (children.isNotEmpty()) {
            inconsistentParentChildRelationships++ //MAYBE RC: dedicated counter for that kind of errors?
            log.info("file[#$fileId]{$fileName}: !directory (flags: ${Integer.toBinaryString(flags)}) but has children(${children.size})")
          }

          //TODO RC: try read _all_ attributes, check all them are readable
          //TODO RC: check attribute storage _has_ such a record (not deleted)
          //if(attributeRecordId!=AbstractAttributesStorage.NON_EXISTENT_ATTR_RECORD_ID) {
            //connection.attributes.forEachAttribute(connection, fileId){
            //}
          //}

        }
        catch (t: Throwable) {
          generalErrors++
          log.info("file[#$fileId]: exception while checking", t)
        }
      }
      log.info("${fileRecords.recordsCount()} file records checked: ${childrenChecked} children, ${notNullContentIds} contents")
    }
  }

  private fun verifyRoots(): VFSHealthCheckReport.RootsReport {
    val report = VFSHealthCheckReport.RootsReport(0, 0, 0)
    return report.apply {
      try {
        val rootIds = impl.treeAccessor().listRoots()
        val records = impl.connection().records
        val maxAllocatedID = records.maxAllocatedID()
        rootsCount = rootIds.size

        for (rootId in rootIds) {
          if (rootId < FSRecords.MIN_REGULAR_FILE_ID || rootId > maxAllocatedID) {
            generalErrors++ //MAYBE RC: dedicated counter for that kind of errors?
            log.info("root[#$rootId]: is outside of allocated IDs range [${FSRecords.MIN_REGULAR_FILE_ID}..$maxAllocatedID]")
            continue
          }
          val rootParentId = records.getParent(rootId)
          if (rootParentId != FSRecords.NULL_FILE_ID) {
            rootsWithParents++
            log.info("root[#$rootId]: parentId[#$rootParentId] != ${FSRecords.NULL_FILE_ID} -> inconsistency")
          }

          val flags = records.getFlags(rootId)
          if (PersistentFSRecordAccessor.hasDeletedFlag(flags)) {
            rootsDeletedButNotRemoved++
            log.info("root[#$rootId]: record is deleted (flags: ${Integer.toBinaryString(flags)}) but not removed from the roots")
          }
        }
      }
      catch (t: Throwable) {
        generalErrors++
        log.info("verifyRoots: can't do something", t)
      }
    }
  }

  private fun verifyNamesEnumerator(): VFSHealthCheckReport.NamesEnumeratorReport {
    val namesEnumerator = impl.connection().names
    val report = VFSHealthCheckReport.NamesEnumeratorReport()
    try {
      namesEnumerator.processAllDataObjects { name ->
        try {
          report.namesChecked++
          val nameId = namesEnumerator.tryEnumerate(name)
          if (nameId == DataEnumeratorEx.NULL_ID) {
            report.namesResolvedToNull++
            log.info("name[$name] enumerated to NULL -> namesEnumerator is corrupted")
            return@processAllDataObjects true
          }
          val nameResolved = namesEnumerator.valueOf(nameId)
          if (nameResolved == null) {
            report.idsResolvedToNull++
            log.info("name[$name]: enumerated to nameId(=$nameId), resolved back to null -> namesEnumerator is corrupted")
            return@processAllDataObjects true
          }
          if (name != nameResolved) {
            report.inconsistentNames++
            log.info("name[$name]: enumerated to nameId(=$nameId), resolved back to different name [$nameResolved] " +
                     "-> namesEnumerator is corrupted")
          }
        }
        catch (e: Throwable) {
          report.generalErrors++
          log.info("name[$name]: exception while checking -> namesEnumerator is corrupted: ${e.message}")
        }
        return@processAllDataObjects true
      }
    }
    catch (e: Throwable) {
      report.generalErrors++
      log.info("Error to verify namesEnumerator", e)
    }
    return report
  }

  private fun verifyContentEnumerator(): VFSHealthCheckReport.ContentEnumeratorReport {
    val report = VFSHealthCheckReport.ContentEnumeratorReport(0, 0)

    val connection = impl.connection()
    val contentHashesEnumerator = connection.contentHashesEnumerator
    val contentsStorage = connection.contents
    val largestContentId = contentHashesEnumerator.largestId

    for (contentId in (DataEnumeratorEx.NULL_ID + 1)..largestContentId) {
      val contentHash = contentHashesEnumerator.valueOf(contentId)
      if (contentHash == null) {
        report.generalErrors++
        log.info("contentId[#$contentId]: id is absent in contentHashes -> contentHashEnumerator is corrupted?")
      }
      try {
        contentsStorage.readStream(contentId).use { stream -> stream.readAllBytes() }
        //MAYBE RC: evaluate content hash from stream, and check == contentHash?
      }
      catch (e: IOException) {
        report.generalErrors++
        log.info("contentId[#$contentId]: present in contentHashesEnumerator, but can't be read from content storage: ${e.message}")
      }
      report.contentRecordsChecked = contentId
    }
    return report
  }


  data class VFSHealthCheckReport(val recordsReport: FileRecordsReport,
                                  val rootsReport: RootsReport,
                                  val namesEnumeratorReport: NamesEnumeratorReport,
                                  val contentEnumeratorReport: ContentEnumeratorReport,
                                  val timeTaken: Duration) {

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
                                 /* record.parentId = NULL_ID & record is not ROOT */
                                 var nullParents: Int = 0,
                                 var childrenChecked: Int = 0,
                                 /* record.children[].parent != record */
                                 var inconsistentParentChildRelationships: Int = 0,
                                 /* exceptions */
                                 var generalErrors: Int = 0)

    data class NamesEnumeratorReport(var namesChecked: Int = 0,
                                     /* .tryEnumerate(existentNameId) = NULL_ID */
                                     var namesResolvedToNull: Int = 0,
                                     /* .valueOf(existentName) = null */
                                     var idsResolvedToNull: Int = 0,
                                     /* existent name -> tryEnumerate() -> .valueOf() -> different name */
                                     var inconsistentNames: Int = 0,
                                     /* tryEnumerate/valueOf/etc exceptions */
                                     var generalErrors: Int = 0)

    data class RootsReport(var rootsCount: Int = 0,
                           /* root.parentId != NULL_ID */
                           var rootsWithParents: Int = 0,
                           /* root.isDeleted but still present in roots catalog*/
                           var rootsDeletedButNotRemoved: Int = 0,
                           /* exceptions */
                           var generalErrors: Int = 0)

    data class ContentEnumeratorReport(
      var contentRecordsChecked: Int = 0,
      /* tryEnumerate/valueOf/etc exceptions */
      var generalErrors: Int = 0
    )
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
  val records = FSRecordsImpl.connect(path, emptyList()) { _, error ->
    throw error
  }
  println("VFS roots:")
  records.forEachRoot { rootUrl, rootId ->
    println("\troot[$rootId]: url: '$rootUrl")
  }

  val log = configureLogger()
  val checkupReport = VFSHealthChecker(records, log).checkHealth()
  println(checkupReport)

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

