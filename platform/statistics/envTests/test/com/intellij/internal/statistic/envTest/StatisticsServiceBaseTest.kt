// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.envTest

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.ThreadLeakTracker
import java.io.File

internal abstract class StatisticsServiceBaseTest : HeavyPlatformTestCase() {
  private val longRunningThreadDisposable: Disposable = Disposer.newDisposable()
  protected lateinit var container: ApacheContainer
  protected lateinit var tmpLocalRoot: String

  override fun setUp() {
    super.setUp()
    tmpLocalRoot = tempDir.createVirtualDir("docker_server_root").path
    FileUtil.copyDir(File("${getTestDataPath()}/default_root"), File(tmpLocalRoot))

    ThreadLeakTracker.longRunningThreadCreated(longRunningThreadDisposable, "Okio Watchdog")
    ThreadLeakTracker.longRunningThreadCreated(longRunningThreadDisposable, "testcontainers-ryuk")
    ThreadLeakTracker.longRunningThreadCreated(longRunningThreadDisposable, "docker-java-stream")
    ThreadLeakTracker.longRunningThreadCreated(longRunningThreadDisposable, "HttpClient-")

    container = ApacheContainer(tmpLocalRoot)
    container.start()
  }

  protected fun getTestDataPath(): String {
    return FileUtil.toSystemIndependentName(PlatformTestUtil.getCommunityPath()) + "/platform/statistics/envTests/testData/"
  }

  override fun tearDown() {
    try {
      container.stop()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      val disposable = longRunningThreadDisposable
      super.tearDown()
      Disposer.dispose(disposable)
    }
  }
}
