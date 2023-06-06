// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent

import com.intellij.ide.IdleTracker
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.AccessToken
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.newvfs.monitoring.VfsUsageCollector
import com.intellij.serviceContainer.AlreadyDisposedException
import com.intellij.util.SystemProperties
import com.intellij.util.io.DataEnumeratorEx
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import java.io.IOException
import java.nio.file.Paths
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds


class VFSHealthCheckStarter : ProjectActivity {

  override suspend fun execute(project: Project) {
    val enableHealthChecking = SystemProperties.getBooleanProperty("vfs.health-checking-enabled", false)
    if (enableHealthChecking) {
      service<VFSHealthChecker>()
    }
  }

  @Service
  class VFSHealthChecker : Disposable {
    private val healthCheckingPeriodMs = SystemProperties.getIntProperty("vfs.health-checking-period-ms", 600_000)

    private var cancelToken: AccessToken = run {
      IdleTracker.getInstance().addIdleListener(healthCheckingPeriodMs, ::checkHealth)
    }

    private fun checkHealth() {
      val fsRecordsImpl = FSRecords.implOrFail()
      if (fsRecordsImpl.isDisposed) {
        return
      }
      val checker = VFSHealthChecker(fsRecordsImpl)
      val checkHealthReport = try {
        //TODO RC: run in a background thread to avoid blocking EDT
        checker.checkHealth()
      }
      catch (e: AlreadyDisposedException) {
        return
      }

      cancelToken.finish()
      cancelToken = IdleTracker.getInstance().addIdleListener(healthCheckingPeriodMs, ::checkHealth)

      //TODO RC: report to FUS (with FS.creatingTimestamp so corruptions vs age could be seen)
      val totalNamesErrors = checkHealthReport.recordsReport.nullNameIds +
                             checkHealthReport.recordsReport.unresolvableNameIds +
                             checkHealthReport.namesEnumeratorReport.generalErrors +
                             checkHealthReport.namesEnumeratorReport.idsResolvedToNull +
                             checkHealthReport.namesEnumeratorReport.namesResolvedToNull +
                             checkHealthReport.namesEnumeratorReport.inconsistentNames
      val totalContentErrors = checkHealthReport.recordsReport.unresolvableContentIds +
                               checkHealthReport.contentEnumeratorReport.generalErrors

      VfsUsageCollector.logVfsHealthCheck(
        /*duration: */ checkHealthReport.timeTaken.inWholeMilliseconds,

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
        checkHealthReport.rootsReport.generalErrors,

        checkHealthReport.contentEnumeratorReport.contentRecordsChecked,
        checkHealthReport.contentEnumeratorReport.generalErrors
      )
      //TODO RC: create VFS_BROKEN_MARKER?
    }

    override fun dispose() {
      cancelToken.close()
    }
  }
}

/**
 * Performs VFS self-consistency checks.
 * E.g. fields have reasonable values, all the references (ids) are valid and could be resolved, and so on.
 */
class VFSHealthChecker(private val impl: FSRecordsImpl) {
  companion object {
    private val LOG = Logger.getInstance(VFSHealthChecker::class.java)
  }

  fun checkHealth(): VFSHealthCheckReport {
    LOG.info("Checking VFS started")
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
    LOG.info("Checking VFS finished: $vfsHealthCheckReport")

    return vfsHealthCheckReport
  }

  private fun verifyFileRecords(): VFSHealthCheckReport.FileRecordsReport {
    val connection = impl.connection()
    val fileRecords = connection.records
    val namesEnumerator = connection.names
    val contentHashesEnumerator = connection.contentHashesEnumerator
    val contentsStorage = connection.contents

    val recordsCount = fileRecords.recordsCount()

    val report = VFSHealthCheckReport.FileRecordsReport()
    val allRoots = IntOpenHashSet(impl.listRoots())
    return report.apply {
      for (fileId in FSRecords.MIN_REGULAR_FILE_ID until recordsCount) {
        try {
          val nameId = fileRecords.getNameId(fileId)
          val parentId = fileRecords.getParent(fileId)
          val flags = fileRecords.getFlags(fileId)
          val attributeRecordId = fileRecords.getAttributeRecordId(fileId)
          val contentId = fileRecords.getContentRecordId(fileId)
          val length = fileRecords.getLength(fileId)
          val timestamp = fileRecords.getTimestamp(fileId)

          fileRecordsChecked = fileId
          if (PersistentFSRecordAccessor.hasDeletedFlag(flags)) {
            fileRecordsDeleted++
            continue
          }

          //if (length < 0) {TODO length is regularly -1
          //  LOG.warn("file[#" + fileId + "]: length(=" + length + ") is negative -> suspicious");
          //}

          if (nameId == DataEnumeratorEx.NULL_ID) {
            nullNameIds++;
            LOG.info("file[#$fileId]: nameId[=$nameId] is not set (NULL_ID) -> names enumerator is inconsistent (broken?)")
          }
          val fileName = namesEnumerator.valueOf(nameId)
          if (fileName == null) {
            unresolvableNameIds++;
            LOG.info("file[#$fileId]: name[#$nameId] does not exist (null)! -> names enumerator is inconsistent (broken?)")
          }
          if (contentId != DataEnumeratorEx.NULL_ID) {
            notNullContentIds++
            val contentHash = contentHashesEnumerator.valueOf(contentId)
            if (contentHash == null) {
              unresolvableContentIds++;
              LOG.info("file[#$fileId]{$fileName}: contentHash[#$contentId] does not exist (null)! " +
                       "-> content hashes enumerator is inconsistent (broken?)")
            }
            try {
              contentsStorage.readStream(contentId).use { stream -> }
            }
            catch (e: IOException) {
              LOG.info("file[#$fileId]{$fileName}: content[#$contentId] can't be read", e)
            }
          } //else -> contentId _could_ be NULL_ID

          if (parentId == DataEnumeratorEx.NULL_ID && !allRoots.contains(fileId)) {
            nullParents++
            LOG.info("file[#$fileId]{$fileName}: parentId is not set (NULL_ID) -> only ROOTS could have no parents -> inconsistency")
          }

          val children = impl.listIds(fileId)
          for (i in children.indices) {
            childrenChecked++
            val childId = children[i]
            val childParentId = fileRecords.getParent(childId)
            if (fileId != childParentId) {
              inconsistentParentChildRelationships++
              LOG.info("file[#$fileId]{$fileName}: children[$i][#$childId].parent(=$childParentId) != fileId " +
                       "-> parent-child relationship is inconsistent (records are broken?)")
            }
          }

          //TODO RC: try read _all_ attributes
        }
        catch (t: Throwable) {
          generalErrors++;
          LOG.info("file[#$fileId]: error to do something", t)
        }
      }
      LOG.info("$recordsCount file records checked: ${childrenChecked} children, ${notNullContentIds} contents")
    }
  }

  private fun verifyRoots(): VFSHealthCheckReport.RootsReport {
    val report = VFSHealthCheckReport.RootsReport(0, 0, 0)
    return report.apply {
      try {
        val rootIds = impl.treeAccessor().listRoots()
        val records = impl.connection().records
        rootsCount = rootIds.size

        for (rootId in rootIds) {
          val rootParentId = records.getParent(rootId)
          if (rootParentId != FSRecords.NULL_FILE_ID) {
            rootsWithParents++
            LOG.info("root[#$rootId]: parentId[#$rootParentId] != ${FSRecords.NULL_FILE_ID} -> inconsistency")
          }
        }
      }
      catch (t: Throwable) {
        generalErrors++
        LOG.info("verifyRoots: can't do something", t)
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
            LOG.info("name[$name] enumerated to NULL -> namesEnumerator is corrupted")
            return@processAllDataObjects true
          }
          val _name = namesEnumerator.valueOf(nameId)
          if (_name == null) {
            report.idsResolvedToNull++
            LOG.info("name[$name]: enumerated to nameId(=$nameId), resolved back to null -> namesEnumerator is corrupted")
            return@processAllDataObjects true
          }
          if (name != _name) {
            report.inconsistentNames++
            LOG.info("name[$name]: enumerated to nameId(=$nameId), resolved back to different name [$_name] " +
                     "-> namesEnumerator is corrupted")
          }
        }
        catch (e: Throwable) {
          report.generalErrors++
          LOG.info("name[$name]: error to do something -> namesEnumerator is corrupted: ${e.message}")
        }
        return@processAllDataObjects true
      }
    }
    catch (e: Throwable) {
      report.generalErrors++
      LOG.info("Error to verify namesEnumerator", e)
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
      try {
        contentsStorage.readStream(contentId).use { stream -> }
      }
      catch (e: IOException) {
        report.generalErrors++;
        LOG.info("contentId[#$contentId]: present in contentHashes, but can't be read: ${e.message}")
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
                                 var nullNameIds: Int = 0,
                                 var unresolvableNameIds: Int = 0,
                                 var notNullContentIds: Int = 0,
                                 var unresolvableContentIds: Int = 0,
                                 var nullParents: Int = 0,
                                 var childrenChecked: Int = 0,
                                 var inconsistentParentChildRelationships: Int = 0,
                                 var generalErrors: Int = 0)

    data class NamesEnumeratorReport(var namesChecked: Int = 0,
                                     var namesResolvedToNull: Int = 0,
                                     var idsResolvedToNull: Int = 0,
                                     var inconsistentNames: Int = 0,
                                     var generalErrors: Int = 0)

    data class RootsReport(var rootsCount: Int = 0,
                           var rootsWithParents: Int = 0,
                           var generalErrors: Int = 0)

    data class ContentEnumeratorReport(
      var contentRecordsChecked: Int = 0,
      var generalErrors: Int = 0
    )
  }
}

fun main(args: Array<String>) {
  if (args.size == 0) {
    System.err.println("Usage: <path-to-VFS-dir>")
    return
  }
  val path = Paths.get(args[0])
  println("Checking VFS [$path]")
  val records = FSRecordsImpl.connect(path, emptyList()) { _records, error ->
    throw error
  }

  val checkupReport = VFSHealthChecker(records).checkHealth()
  println(checkupReport)

  System.exit(0) //too many non-daemon threads
}

