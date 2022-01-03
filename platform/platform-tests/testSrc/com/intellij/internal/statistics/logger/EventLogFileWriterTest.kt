// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistics.logger

import com.intellij.internal.statistic.config.eventLog.EventLogBuildType
import com.intellij.internal.statistic.eventLog.EventLogFile
import com.intellij.internal.statistic.eventLog.EventLogFileWriter
import com.intellij.internal.statistic.eventLog.StatisticsEventLoggerProvider.Companion.DEFAULT_MAX_FILE_SIZE_BYTES
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.util.containers.ContainerUtil.newArrayList
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

const val MAX_AGE = (14 * 24 * 60 * 60 * 1000).toLong()

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
    doTestCleanupOldFiles(newArrayList(f3), newArrayList(false), -1, false)
  }

  @Test
  fun `test one old active file`() {
    val ts = System.currentTimeMillis()

    val f3 = TestFile(ts - 1000, "active.log")
    doTestCleanupOldFiles(newArrayList(f3), newArrayList(false), -1, false)
  }

  @Test
  fun `test one new active file`() {
    val ts = System.currentTimeMillis()

    val f3 = TestFile(ts - MAX_AGE, "active.log")
    doTestCleanupOldFiles(newArrayList(f3), newArrayList(false), -1, false)
  }

  @Test
  fun `test all new files`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - 3000, "test.log")
    val f2 = TestFile(ts - 2000, "test2.log")
    val f3 = TestFile(ts - 1000, "active.log")

    doTestCleanupOldFiles(newArrayList(f1, f2, f3), newArrayList(false, false, false), ts - 3000, true)
  }

  @Test
  fun `test all new files but one`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 10, "test.log")
    val f2 = TestFile(ts, "test2.log")
    val f3 = TestFile(ts - 10, "active.log")

    doTestCleanupOldFiles(newArrayList(f1, f2, f3), newArrayList(true, false, false), ts, true)
  }

  @Test
  fun `test all new files but active`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - 20, "test.log")
    val f2 = TestFile(ts, "test2.log")
    val f3 = TestFile(ts - MAX_AGE - 10, "active.log")

    doTestCleanupOldFiles(newArrayList(f1, f2, f3), newArrayList(false, false, false), ts - 20, true)
  }

  @Test
  fun `test all new files but one and active`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 10, "test.log")
    val f2 = TestFile(ts, "test2.log")
    val f3 = TestFile(ts - MAX_AGE - 20, "active.log")

    doTestCleanupOldFiles(newArrayList(f1, f2, f3), newArrayList(true, false, false), ts, true)
  }

  @Test
  fun `test all old files`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 3000, "test.log")
    val f2 = TestFile(ts - MAX_AGE - 2000, "test2.log")
    val f3 = TestFile(ts - MAX_AGE - 1000, "active.log")

    doTestCleanupOldFiles(newArrayList(f1, f2, f3), newArrayList(true, true, false), -1, false)
  }

  @Test
  fun `test all old files with oldest active`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 3000, "test.log")
    val f2 = TestFile(ts - MAX_AGE - 2000, "test2.log")
    val f3 = TestFile(ts - MAX_AGE - 4000, "active.log")

    doTestCleanupOldFiles(newArrayList(f1, f2, f3), newArrayList(true, true, false), -1, false)
  }

  @Test
  fun `test all old files but active`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 3000, "test.log")
    val f2 = TestFile(ts - MAX_AGE - 2000, "test2.log")
    val f3 = TestFile(ts - 1000, "active.log")

    doTestCleanupOldFiles(newArrayList(f1, f2, f3), newArrayList(true, true, false), -1, false)
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

    doTestCleanupOldFiles(newArrayList(f1, f2, active, f3, f4, f5, f6),
                          newArrayList(true, false, false, false, true, true, false),
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

    doTestCleanupOldFiles(newArrayList(f1, f3, f2, active, f4),
                          newArrayList(true, false, true, false, false),
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

  private fun format(message1: String) = message1 + "\n"
}


class TestEventLogFileWriter(dir: Path, files: List<File>)
  : EventLogFileWriter(dir,
                       DEFAULT_MAX_FILE_SIZE_BYTES,
                       MAX_AGE,
                       { directory -> EventLogFile.create(directory, EventLogBuildType.EAP, "221").file },
                       Supplier { files }) {
  var quickCleanCheck: Boolean = false

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

  override fun getActiveLogName(): String {
    return "active.log"
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