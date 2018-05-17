// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.local

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.impl.local.FileWatcher
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.openapi.vfs.impl.local.NativeFileWatcherImpl
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.INTER_RESPONSE_DELAY
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.shutdown
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.startup
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.unwatch
import com.intellij.openapi.vfs.local.FileWatcherTestUtil.watch
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import com.intellij.testFramework.rules.TempDirectory
import com.intellij.util.TimeoutUtil
import org.apache.log4j.Level
import org.assertj.core.api.Assertions.assertThat
import org.junit.*
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

private const val WARM_UPS = 5
private const val REPEATS = 50
private const val NUM_FILES = 1000

@Ignore
class FileWatcherPerformanceTest : BareTestFixtureTestCase() {
  @Rule @JvmField val tempDir = TempDirectory()

  private var tracing: Boolean = false
  private lateinit var watcher: FileWatcher
  private val events = AtomicInteger(0)
  private val resets = AtomicInteger(0)
  private val notifier: (String) -> Unit = { path ->
    if (path === FileWatcher.RESET || path !== FileWatcher.OTHER && path.startsWith(tempDir.root.path)) {
      events.incrementAndGet()
      if (path == FileWatcher.RESET) {
        resets.incrementAndGet()
      }
    }
  }

  @Before fun setUp() {
    val logger = Logger.getInstance(NativeFileWatcherImpl::class.java)
    tracing = logger.isTraceEnabled
    if (tracing) logger.setLevel(Level.WARN)
    watcher = (LocalFileSystem.getInstance() as LocalFileSystemImpl).fileWatcher
  }

  @After
  fun tearDown() {
    if (tracing) Logger.getInstance(NativeFileWatcherImpl::class.java).setLevel(Level.TRACE)
  }

  @Test fun watcherOverhead() {
    var unwatchedTime = 0L
    var watchedTime = 0L

    for (i in 1..WARM_UPS) {
      createDeleteFiles(tempDir.newFolder())
    }

    for (i in 1..REPEATS) {
      TimeoutUtil.sleep(250)

      val unwatchedDir = tempDir.newFolder()
      unwatchedTime += time { createDeleteFiles(unwatchedDir) }
      TimeoutUtil.sleep(250)

      val watchedDir = tempDir.newFolder()
      val request = startWatcher(watchedDir)
      watchedTime += time { createDeleteFiles(watchedDir) }
      waitForEvents()
      stopWatcher(request)
    }

    val overhead = ((watchedTime - unwatchedTime) * 100) / unwatchedTime
    println("** FW overhead = ${overhead}%, events = ${events}, resets = ${resets}")
    assertThat(overhead).isLessThanOrEqualTo(25)
    assertThat(resets.get()).isLessThanOrEqualTo(REPEATS)
  }

  private fun createDeleteFiles(directory: File) {
    for (i in 1..NUM_FILES) File(directory, "test_file_${i}.txt").writeText("hi there.")
    for (i in 1..NUM_FILES) File(directory, "test_file_${i}.txt").delete()
  }

  private fun time(block: () -> Unit): Long {
    val t = System.nanoTime()
    block()
    return (System.nanoTime() - t) / 1000
  }

  private fun startWatcher(directory: File): LocalFileSystem.WatchRequest {
    startup(watcher, notifier)
    return watch(watcher, directory)
  }

  private fun stopWatcher(request: LocalFileSystem.WatchRequest) {
    unwatch(watcher, request)
    shutdown(watcher)
  }

  private fun waitForEvents() {
    var lastCount = events.get()
    while (true) {
      TimeoutUtil.sleep(INTER_RESPONSE_DELAY)
      val newCount = events.get()
      if (lastCount == newCount) break
      lastCount = newCount
    }
  }
}