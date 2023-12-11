// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl.local

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.IoTestUtil.assumeSymLinkCreationIsSupported
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.NATIVE_PROCESS_DELAY
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.refresh
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.shutdown
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.startup
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.wait
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.RunAll
import com.intellij.testFramework.SkipSlowTestLocally
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.testFramework.runInEdtAndWait
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.util.*
import kotlin.test.assertFalse

@SkipSlowTestLocally
class WatchRootsManagerPerformanceTest : BareTestFixtureTestCase() {
  //<editor-fold desc="Set up / tear down">
  private val LOG = logger<WatchRootsManagerPerformanceTest>()

  @Rule @JvmField val tempDir = TempDirectory()

  private lateinit var fs: LocalFileSystem
  private lateinit var vfsTempDir: VirtualFile
  private lateinit var watcher: FileWatcher

  @Before fun setUp() {
    LOG.debug("================== setting up " + getTestName(false) + " ==================")

    fs = LocalFileSystem.getInstance()
    vfsTempDir = refresh(tempDir.rootPath)

    runInEdtAndWait { fs.refresh(false) }
    runInEdtAndWait { fs.refresh(false) }

    watcher = (fs as LocalFileSystemImpl).fileWatcher
    assertFalse(watcher.isOperational)
    startup(watcher) { }

    LOG.debug("================== setting up " + getTestName(false) + " ==================")
  }

  @After fun tearDown() {
    LOG.debug("================== tearing down " + getTestName(false) + " ==================")

    RunAll(
      { if (this::watcher.isInitialized) shutdown(watcher) },
      {
        runInEdtAndWait {
          if (this::vfsTempDir.isInitialized) runWriteAction { vfsTempDir.delete(this) }
          if (this::fs.isInitialized) (fs as LocalFileSystemImpl).cleanupForNextTest()
        }
      },
    ).run()

    LOG.debug("================== tearing down " + getTestName(false) + " ==================")
  }
  //</editor-fold>

  @Test fun testWatchRootsAddOnlyTiming() {
    val root = tempDir.newDirectoryPath("root")
    val fileCount = 50_000
    refresh(root)

    try {
      val roots = (1..fileCount).map { "${root}/f${it}" }
      val requests = ArrayList<LocalFileSystem.WatchRequest>(fileCount)
      PlatformTestUtil.startPerformanceTest("Adding roots", 9000) {
        roots.forEach {
          requests.add(fs.addRootToWatch(it, true)!!)
        }
        fs.removeWatchedRoots(requests)
      }.assertTiming()
    }
    finally {
      wait(NATIVE_PROCESS_DELAY) { watcher.isSettingRoots }
    }
  }

  @Test fun testWatchRootsBulkAddOnlyTiming() {
    val root = tempDir.newDirectoryPath("root")
    val fileCount = 100_000
    refresh(root)

    try {
      val roots = (1..fileCount).map { "${root}/f${it}" }
      PlatformTestUtil.startPerformanceTest("Adding roots", 13000) {
        fs.removeWatchedRoots(fs.addRootsToWatch(roots, true))
      }.assertTiming()
    }
    finally {
      wait(NATIVE_PROCESS_DELAY) { watcher.isSettingRoots }
    }
  }

  @Test fun testAddWatchRootWithManySymbolicLinks() {
    assumeSymLinkCreationIsSupported()
    val root = tempDir.newDirectoryPath("root")
    val linkCount = 20_000
    (1..linkCount).forEach { Files.createSymbolicLink(root.resolve("l${it}"), tempDir.newDirectoryPath("targets/d${it}")) }
    refresh(root)

    try {
      val rootPath = root.toString()
      PlatformTestUtil.startPerformanceTest("Adding roots", 2000) {
        fs.removeWatchedRoot(fs.addRootToWatch(rootPath, true)!!)
      }.assertTiming()
    }
    finally {
      wait(NATIVE_PROCESS_DELAY) { watcher.isSettingRoots }
    }
  }

  @Test fun testCreateCanonicalPathMap() {
    val root = tempDir.newDirectoryPath("root").toString().replace('\\', '/')
    val flatWatchRoots = TreeSet<String>()
    val optimizedRecursiveWatchRoots = TreeSet<String>()
    val pathMappings = WatchRootsUtil.createMappingsNavigableSet()

    val filesCount = 10_000
    (1..filesCount).forEach {i ->
      optimizedRecursiveWatchRoots.add("$root/rec$i")
      (1..5).forEach { flatWatchRoots.add("$root/rec$i/flat$it") }
      flatWatchRoots.add("$root/flat$i")
      (1..5).forEach { pathMappings.add(Pair("$root/rec$i/ln$it", "$root/targets/rec$i/ln$it")) }
    }

    PlatformTestUtil.startPerformanceTest("Create canonical path map", 7000) {
      repeat(18) {
        WatchRootsManager.createCanonicalPathMap(flatWatchRoots, optimizedRecursiveWatchRoots, pathMappings, false)
      }
    }.assertTimingAsSubtest()

    PlatformTestUtil.startPerformanceTest("Create canonical path map - convert paths", 10000) {
      repeat(18) {
        WatchRootsManager.createCanonicalPathMap(flatWatchRoots, optimizedRecursiveWatchRoots, pathMappings, true)
      }
    }.assertTimingAsSubtest()
  }

  @Test fun testCanonicalPathMapWithManySymlinks() {
    val root = "/users/foo/workspace"
    val flatWatchRoots = TreeSet<String>()
    val optimizedRecursiveWatchRoots = TreeSet<String>()
    val pathMappings = WatchRootsUtil.createMappingsNavigableSet()

    val filesCount = 100_000
    optimizedRecursiveWatchRoots.add("$root/targets")
    optimizedRecursiveWatchRoots.add("$root/src")
    (1..filesCount).forEach {i ->
      pathMappings.add(Pair("$root/src/ln$i", "$root/targets/ln$i-1"))
      pathMappings.add(Pair("$root/src/ln$i", "$root/targets/ln$i-2"))
    }

    val map = WatchRootsManager.createCanonicalPathMap(flatWatchRoots, optimizedRecursiveWatchRoots, pathMappings, false)
    map.addMapping((1..filesCount).map { Pair("$root/src/ln$it-3", "$root/src/ln$it-3") })
    PlatformTestUtil.startPerformanceTest("Test apply mapping from canonical path map", 3000) {
      repeat(1_000_000) {
        map.mapToOriginalWatchRoots("$root/src/ln${(Math.random() * 200_000).toInt()}", true)
      }
    }.assertTimingAsSubtest()

    PlatformTestUtil.startPerformanceTest("Create canonical path map", 3000) {
      repeat(100) {
        WatchRootsManager.createCanonicalPathMap(flatWatchRoots, optimizedRecursiveWatchRoots, pathMappings, false)
      }
    }.assertTimingAsSubtest()

    PlatformTestUtil.startPerformanceTest("Create canonical path map - convert paths", 3000) {
      repeat(40) {
        WatchRootsManager.createCanonicalPathMap(flatWatchRoots, optimizedRecursiveWatchRoots, pathMappings, true)
      }
    }.assertTimingAsSubtest()
  }
}
