// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.logger

import com.intellij.internal.statistic.config.eventLog.EventLogBuildType
import com.intellij.internal.statistic.eventLog.EventLogFile
import com.intellij.internal.statistic.eventLog.EventLogFileWriter
import com.intellij.internal.statistic.eventLog.FileDeletionCause
import com.intellij.internal.statistic.eventLog.LogEventSerializer
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider.Companion.DEFAULT_MAX_FILE_SIZE_BYTES
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.TemporaryDirectory
import com.jetbrains.fus.reporting.model.lion3.LogEvent
import com.jetbrains.fus.reporting.model.lion3.LogEventAction
import com.jetbrains.fus.reporting.model.lion3.LogEventGroup
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Should be in sync with [EventLogFileWriter.maxFileAge] */
const val MAX_AGE = (7 * 24 * 60 * 60 * 1000).toLong()

class EventLogFileWriterTest {

  @Rule
  @JvmField
  val tempDir = TemporaryDirectory()

  private fun doTestCleanupOldFiles(files: List<TestFile>, deleted: List<Boolean>, oldestAfterDelete: Long, secondQuick: Boolean) {
    TestEventLogFileWriter(tempDir.createDir(), files).use { fileWriter ->
      fileWriter.assertOldest(-1)
      fileWriter.cleanUpOldFiles()
      assertTrue { !fileWriter.quickCleanCheck }
      fileWriter.assertOldest(oldestAfterDelete)

      for ((index, file) in files.withIndex()) {
        assertTrue { file.deleted == deleted[index] }
      }

      fileWriter.cleanUpOldFiles()
      assertTrue { fileWriter.quickCleanCheck == secondQuick }
    }
  }

  @Test
  fun `test no files`() {
    doTestCleanupOldFiles(ArrayList(), ArrayList(), -1, false)
  }

  @Test
  fun `test one active file`() {
    val ts = System.currentTimeMillis()

    val f3 = TestFile(ts - 100 * 1000, "active.log")
    doTestCleanupOldFiles(listOf(f3), listOf(false), -1, false)
  }

  @Test
  fun `test one old active file`() {
    val ts = System.currentTimeMillis()

    val f3 = TestFile(ts - 1000, "active.log")
    doTestCleanupOldFiles(listOf(f3), listOf(false), -1, false)
  }

  @Test
  fun `test one new active file`() {
    val ts = System.currentTimeMillis()

    val f3 = TestFile(ts - MAX_AGE, "active.log")
    doTestCleanupOldFiles(listOf(f3), listOf(false), -1, false)
  }

  @Test
  fun `test all new files`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - 3000, "test.log")
    val f2 = TestFile(ts - 2000, "test2.log")
    val f3 = TestFile(ts - 1000, "active.log")

    doTestCleanupOldFiles(listOf(f1, f2, f3), listOf(false, false, false), ts - 3000, true)
  }

  @Test
  fun `test all almost expired files`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE + 1000, "test.log")
    val f2 = TestFile(ts - MAX_AGE + 2000, "test2.log")
    val f3 = TestFile(ts - MAX_AGE + 3000, "test3.log")

    doTestCleanupOldFiles(listOf(f1, f2, f3), listOf(false, false, false), ts - MAX_AGE + 1000, true)
  }


  @Test
  fun `test all new files but one`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 10, "test.log")
    val f2 = TestFile(ts, "test2.log")
    val f3 = TestFile(ts - 10, "active.log")

    doTestCleanupOldFiles(listOf(f1, f2, f3), listOf(true, false, false), ts, true)
  }

  @Test
  fun `test all new files but active`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - 20, "test.log")
    val f2 = TestFile(ts, "test2.log")
    val f3 = TestFile(ts - MAX_AGE - 10, "active.log")

    doTestCleanupOldFiles(listOf(f1, f2, f3), listOf(false, false, false), ts - 20, true)
  }

  @Test
  fun `test all new files but one and active`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 10, "test.log")
    val f2 = TestFile(ts, "test2.log")
    val f3 = TestFile(ts - MAX_AGE - 20, "active.log")

    doTestCleanupOldFiles(listOf(f1, f2, f3), listOf(true, false, false), ts, true)
  }

  @Test
  fun `test all old files`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 3000, "test.log")
    val f2 = TestFile(ts - MAX_AGE - 2000, "test2.log")
    val f3 = TestFile(ts - MAX_AGE - 1000, "active.log")

    doTestCleanupOldFiles(listOf(f1, f2, f3), listOf(true, true, false), -1, false)
  }

  @Test
  fun `test all old files with oldest active`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 3000, "test.log")
    val f2 = TestFile(ts - MAX_AGE - 2000, "test2.log")
    val f3 = TestFile(ts - MAX_AGE - 4000, "active.log")

    doTestCleanupOldFiles(listOf(f1, f2, f3), listOf(true, true, false), -1, false)
  }

  @Test
  fun `test all old files but active`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 3000, "test.log")
    val f2 = TestFile(ts - MAX_AGE - 2000, "test2.log")
    val f3 = TestFile(ts - 1000, "active.log")

    doTestCleanupOldFiles(listOf(f1, f2, f3), listOf(true, true, false), -1, false)
  }

  @Test
  fun `test old and new files`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 3000, "test.log")
    val f2 = TestFile(ts - 2000, "test2.log")
    val f3 = TestFile(ts - 2000, "test3.log")
    val f4 = TestFile(ts - MAX_AGE - 2000, "test4.log")
    val f5 = TestFile(ts - MAX_AGE - 2000, "test5.log")
    val f6 = TestFile(ts - 2000, "test6.log")
    val active = TestFile(ts - 1000, "active.log")

    doTestCleanupOldFiles(listOf(f1, f2, active, f3, f4, f5, f6),
                          listOf(true, false, false, false, true, true, false),
                          ts - 2000,
                          true)
  }

  @Test
  fun `test dont check second time`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 3000, "test.log")
    val f2 = TestFile(ts - MAX_AGE - 2000, "test1.log")
    val f3 = TestFile(ts - 3000, "test2.log")
    val f4 = TestFile(ts - 1000, "test3.log")
    val active = TestFile(ts, "active.log")

    doTestCleanupOldFiles(listOf(f1, f3, f2, active, f4),
                          listOf(true, false, true, false, false),
                          ts - 3000,
                          true)
  }


  @Test
  fun testFilesRotation() {
    class TestLogFilePathProvider {
      var counter = 0
      fun getNextPath(dir: Path): File {
        val file = File(dir.absolutePathString(), "TestLogFilePathProvider.$counter.log")
        counter++
        return file
      }
    }

    val logPath = tempDir.createDir()
    val pathProvider = TestLogFilePathProvider()
    EventLogFileWriter(logPath, 100, pathProvider::getNextPath).use { fileWriter ->
      val message1 = StringUtil.repeat("a", 80)
      fileWriter.log(message1)
      fileWriter.flush()
      val logFile1 = Path.of(logPath.absolutePathString(), fileWriter.getActiveLogName())
      assertEquals(format(message1), Files.readString(logFile1))
      val message2 = StringUtil.repeat("b", 80)
      fileWriter.log(message2)
      fileWriter.flush()
      assertEquals(format(message1) + format(message2), Files.readString(logFile1))
      val message3 = StringUtil.repeat("c", 80)
      fileWriter.log(message3)
      fileWriter.flush()
      val logFile2 = Path.of(logPath.absolutePathString(), fileWriter.getActiveLogName())
      assertTrue("New file should be created when max size exceed") { logFile1 != logFile2 }
      assertTrue { logFile1.exists() }
      assertEquals(format(message3), Files.readString(logFile2))
    }
  }

  @Test
  fun `test deleted file metrics are reported`() {
    val dir = tempDir.createDir()
    val file = oldContentFile(dir, "events-eap.log", eventLine(1000L) + "\n" + eventLine(5000L) + "\n")

    TestEventLogFileWriter(dir, listOf(file)).use { fileWriter ->
      fileWriter.cleanUpOldFiles()

      val report = fileWriter.deletedReports.single()
      assertEquals(FileDeletionCause.AGE, report.cause)
      assertEquals(file.length(), report.sizeBytes)
      assertEquals(EventLogBuildType.EAP, report.buildType)
      assertTrue { report.ageMs > 0 }
      assertTrue { report.queuedMs > 0 }
    }
  }

  @Test
  fun `test deleted file skips blank lines when reading first event`() {
    val dir = tempDir.createDir()
    val file = oldContentFile(dir, "events-release.log", "\n" + eventLine(2000L) + "\n\n" + eventLine(9000L) + "\n\n")

    TestEventLogFileWriter(dir, listOf(file)).use { fileWriter ->
      fileWriter.cleanUpOldFiles()

      val report = fileWriter.deletedReports.single()
      assertEquals(EventLogBuildType.RELEASE, report.buildType)
      assertTrue("blank lines are skipped, so the oldest event is found and age is positive") { report.ageMs > 0 }
    }
  }

  @Test
  fun `test deleted file with unreadable content reports unknown first event`() {
    val dir = tempDir.createDir()
    val file = oldContentFile(dir, "events-eap.log", "not a serialized event\n")

    TestEventLogFileWriter(dir, listOf(file)).use { fileWriter ->
      fileWriter.cleanUpOldFiles()

      val report = fileWriter.deletedReports.single()
      assertEquals(-1L, report.ageMs)
    }
  }

  private fun oldContentFile(dir: Path, name: String, content: String): ContentTestFile {
    val realFile = dir.resolve(name).toFile()
    realFile.writeText(content)
    return ContentTestFile(realFile, System.currentTimeMillis() - MAX_AGE - 10_000L)
  }

  private fun eventLine(time: Long): String {
    val event = LogEvent(
      "session", "233.1", "1", time,
      LogEventGroup("my.group", "1"), "1",
      LogEventAction("my.event", false, HashMap<String, Any>())
    )
    return LogEventSerializer.toString(event)
  }

  private fun format(message1: String) = message1 + "\n"
}


class TestEventLogFileWriter(dir: Path, files: List<File>)
  : EventLogFileWriter(dir,
                       DEFAULT_MAX_FILE_SIZE_BYTES,
                       { directory -> EventLogFile.create(directory, EventLogBuildType.EAP, "221").file },
                       Supplier { files }) {
  var quickCleanCheck: Boolean = false
  val deletedReports: MutableList<DeletedFileReport> = mutableListOf()

  fun assertOldest(expected: Long) {
    assertTrue { oldestExistingFile == expected }
  }

  public override fun cleanUpOldFiles() {
    quickCleanCheck = true
    super.cleanUpOldFiles()
  }

  public override fun cleanUpOldFiles(oldestAcceptable: Long) {
    quickCleanCheck = false
    super.cleanUpOldFiles(oldestAcceptable)
  }

  public override fun logDeletedFile(
    cause: FileDeletionCause,
    ageMs: Long,
    queuedMs: Long,
    sizeBytes: Long,
    buildType: EventLogBuildType,
  ) {
    deletedReports.add(DeletedFileReport(cause, ageMs, queuedMs, sizeBytes, buildType))
  }

  override fun getActiveLogName(): String {
    return "active.log"
  }
}

data class DeletedFileReport(
  val cause: FileDeletionCause,
  val ageMs: Long,
  val queuedMs: Long,
  val sizeBytes: Long,
  val buildType: EventLogBuildType,
)

/** A [File] backed by a real file on disk (so its contents can be read), but with a controlled last-modified time. */
private class ContentTestFile(realFile: File, private val modified: Long) : File(realFile.path) {
  var deleted: Boolean = false

  override fun lastModified(): Long = modified

  override fun delete(): Boolean {
    deleted = true
    return true
  }
}

private class TestFile(val modified: Long, path: String) : File("/tmp/$path") {
  var deleted: Boolean = false

  override fun lastModified(): Long {
    return modified
  }

  override fun delete(): Boolean {
    deleted = true
    return true
  }
}