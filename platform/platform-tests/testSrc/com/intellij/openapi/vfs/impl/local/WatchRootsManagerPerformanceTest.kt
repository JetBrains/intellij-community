// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.impl.local

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.IoTestUtil.assumeNioSymLinkCreationIsSupported
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
import java.io.File
import java.nio.file.Files
import java.util.*
import kotlin.collections.ArrayList
import kotlin.test.assertFalse

@SkipSlowTestLocally
class WatchRootsManagerPerformanceTest : BareTestFixtureTestCase() {
  //<editor-fold desc="Set up / tear down">
  private val LOG: Logger by lazy { Logger.getInstance(NativeFileWatcherImpl::class.java) }

  @Rule @JvmField val tempDir = TempDirectory()

  private lateinit var fs: LocalFileSystem
  private lateinit var vfsTempDir: VirtualFile
  private lateinit var watcher: FileWatcher

  @Before fun setUp() {
    LOG.debug("================== setting up " + getTestName(false) + " ==================")

    fs = LocalFileSystem.getInstance()
    vfsTempDir = refresh(tempDir.root)

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
    val root = tempDir.newDirectory("root")
    val fileCount = 50_000
    try {
      PlatformTestUtil.startPerformanceTest("Adding roots", 9000) {
        val requests = ArrayList<LocalFileSystem.WatchRequest>(fileCount)
        for (i in 1..fileCount) {
          requests.add(fs.addRootToWatch(File(root, "f$i").path, true)!!)
        }
        fs.removeWatchedRoots(requests)
      }.assertTiming()
    }
    finally {
      wait(NATIVE_PROCESS_DELAY) { watcher.isSettingRoots }
    }
  }

  @Test fun testWatchRootsBulkAddOnlyTiming() {
    val root = tempDir.newDirectory("root")
    val fileCount = 100_000
    refresh(root)

    try {
      PlatformTestUtil.startPerformanceTest("Adding roots", 13000) {
        fs.removeWatchedRoots(fs.addRootsToWatch((1..fileCount).map { File(root, "f$it").path }, true))
      }.assertTiming()
    }
    finally {
      wait(NATIVE_PROCESS_DELAY) { watcher.isSettingRoots }
    }
  }

  @Test fun testAddWatchRootWithManySymbolicLinks() {
    assumeNioSymLinkCreationIsSupported()
    val root = tempDir.newDirectory("root")
    val linkCount = 20_000
    (1..linkCount).forEach {
      Files.createSymbolicLink(root.toPath().resolve("l$it"), tempDir.newDirectory("targets/d$it").toPath())
    }
    refresh(root)

    try {
      PlatformTestUtil.startPerformanceTest("Adding roots", 2000) {
        fs.removeWatchedRoot(fs.addRootToWatch(root.path, true)!!)
      }.assertTiming()
    }
    finally {
      wait(NATIVE_PROCESS_DELAY) { watcher.isSettingRoots }
    }
  }

  @Test fun testCreateCanonicalPathMap() {
    val root = tempDir.newDirectory("root").path.replace('\\', '/')
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
    }.assertTiming()

    PlatformTestUtil.startPerformanceTest("Create canonical path map - convert paths", 10000) {
      repeat(18) {
        WatchRootsManager.createCanonicalPathMap(flatWatchRoots, optimizedRecursiveWatchRoots, pathMappings, true)
      }
    }.assertTiming()
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
    }.assertTiming()

    PlatformTestUtil.startPerformanceTest("Create canonical path map", 3000) {
      repeat(100) {
        WatchRootsManager.createCanonicalPathMap(flatWatchRoots, optimizedRecursiveWatchRoots, pathMappings, false)
      }
    }.assertTiming()

    PlatformTestUtil.startPerformanceTest("Create canonical path map - convert paths", 3000) {
      repeat(40) {
        WatchRootsManager.createCanonicalPathMap(flatWatchRoots, optimizedRecursiveWatchRoots, pathMappings, true)
      }
    }.assertTiming()
  }
}
