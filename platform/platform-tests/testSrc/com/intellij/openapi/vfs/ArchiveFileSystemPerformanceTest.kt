// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.vfs.newvfs.ArchiveFileSystem
import com.intellij.tools.ide.metrics.benchmark.Benchmark
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ArchiveFileSystemPerformanceTest : BareTestFixtureTestCase() {
  private lateinit var fs: ArchiveFileSystem
  private lateinit var entry: VirtualFile

  @Before fun setUp() {
    fs = StandardFileSystems.jar() as ArchiveFileSystem
    entry = fs.findFileByPath("${PathManager.getJarPathForClass(Test::class.java)}!/org/junit/Test.class")!!
  }

  @Test fun getRootByEntry() {
    val root = fs.getRootByEntry(entry)!!
    Benchmark.newBenchmark("ArchiveFileSystem.getRootByEntry()") {
      for (i in 0..100000) {
        assertEquals(root, fs.getRootByEntry(entry))
      }
    }.start()
  }

  @Test fun getLocalByEntry() {
    val local = fs.getLocalByEntry(entry)!!
    Benchmark.newBenchmark("ArchiveFileSystem.getLocalByEntry()") {
      for (i in 0..100000) {
        assertEquals(local, fs.getLocalByEntry(entry))
      }
    }.start()
  }
}