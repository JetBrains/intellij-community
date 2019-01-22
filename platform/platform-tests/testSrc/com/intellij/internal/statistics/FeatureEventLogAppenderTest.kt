// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistics

import com.intellij.internal.statistic.eventLog.FeatureUsageEventFileAppender
import com.intellij.util.containers.ContainerUtil.newArrayList
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import kotlin.test.assertTrue

class FeatureEventLogAppenderTest {
  private val MAX_AGE = (14 * 24 * 60 * 60 * 1000).toLong()

  private fun doTest(files: List<TestFile>, deleted: List<Boolean>, oldestAfterDelete: Long, secondQuick: Boolean) {
    val appender = TestFeatureUsageFileAppender(files)
    appender.setMaxFileAge(MAX_AGE)

    appender.assertOldest(-1)
    appender.cleanUpOldFiles()
    assertTrue { !appender.quickCleanCheck }
    appender.assertOldest(oldestAfterDelete)

    for ((index, file) in files.withIndex()) {
      assertTrue { file.deleted == deleted[index] }
    }

    appender.cleanUpOldFiles()
    assertTrue { appender.quickCleanCheck == secondQuick}
  }

  @Test
  fun `test no files`() {
    doTest(newArrayList(), newArrayList(), -1, false)
  }

  @Test
  fun `test one active file`() {
    val ts = System.currentTimeMillis()

    val f3 = TestFile(ts - 100 * 1000, "active.log")
    doTest(newArrayList(f3), newArrayList(false), -1, false)
  }

  @Test
  fun `test one old active file`() {
    val ts = System.currentTimeMillis()

    val f3 = TestFile(ts - 1000, "active.log")
    doTest(newArrayList(f3), newArrayList(false), -1, false)
  }

  @Test
  fun `test one new active file`() {
    val ts = System.currentTimeMillis()

    val f3 = TestFile(ts - MAX_AGE, "active.log")
    doTest(newArrayList(f3), newArrayList(false), -1, false)
  }

  @Test
  fun `test all new files`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - 3000, "test.log")
    val f2 = TestFile(ts - 2000, "test2.log")
    val f3 = TestFile(ts - 1000, "active.log")

    doTest(newArrayList(f1, f2, f3), newArrayList(false, false, false), ts - 3000, true)
  }

  @Test
  fun `test all new files but one`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 10, "test.log")
    val f2 = TestFile(ts, "test2.log")
    val f3 = TestFile(ts - 10, "active.log")

    doTest(newArrayList(f1, f2, f3), newArrayList(true, false, false), ts, true)
  }

  @Test
  fun `test all new files but active`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - 20, "test.log")
    val f2 = TestFile(ts, "test2.log")
    val f3 = TestFile(ts - MAX_AGE - 10, "active.log")

    doTest(newArrayList(f1, f2, f3), newArrayList(false, false, false), ts - 20, true)
  }

  @Test
  fun `test all new files but one and active`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 10, "test.log")
    val f2 = TestFile(ts, "test2.log")
    val f3 = TestFile(ts - MAX_AGE - 20, "active.log")

    doTest(newArrayList(f1, f2, f3), newArrayList(true, false, false), ts, true)
  }

  @Test
  fun `test all old files`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 3000, "test.log")
    val f2 = TestFile(ts - MAX_AGE - 2000, "test2.log")
    val f3 = TestFile(ts - MAX_AGE - 1000, "active.log")

    doTest(newArrayList(f1, f2, f3), newArrayList(true, true, false), -1, false)
  }

  @Test
  fun `test all old files with oldest active`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 3000, "test.log")
    val f2 = TestFile(ts - MAX_AGE - 2000, "test2.log")
    val f3 = TestFile(ts - MAX_AGE - 4000, "active.log")

    doTest(newArrayList(f1, f2, f3), newArrayList(true, true, false), -1, false)
  }

  @Test
  fun `test all old files but active`() {
    val ts = System.currentTimeMillis()

    val f1 = TestFile(ts - MAX_AGE - 3000, "test.log")
    val f2 = TestFile(ts - MAX_AGE - 2000, "test2.log")
    val f3 = TestFile(ts - 1000, "active.log")

    doTest(newArrayList(f1, f2, f3), newArrayList(true, true, false), -1, false)
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

    doTest(newArrayList(f1, f2, active, f3, f4, f5, f6),
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

    doTest(newArrayList(f1, f3, f2, active, f4),
           newArrayList(true, false, true, false, false),
           ts - 3000,
           true)
  }
}

class TestFeatureUsageFileAppender(files: List<File>) : FeatureUsageEventFileAppender(Paths.get("tmp"), files) {
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