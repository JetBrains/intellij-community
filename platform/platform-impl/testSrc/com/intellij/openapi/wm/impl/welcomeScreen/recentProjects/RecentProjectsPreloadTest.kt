// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen.recentProjects

import com.intellij.ide.RecentProjectsManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@TestApplication
internal class RecentProjectsPreloadTest {
  @Test
  fun `the first take returns the collected list and a later one returns null`(): Unit = timeoutRunBlocking {
    val scope = CoroutineScope(Job())
    val list = ArrayList<RecentProjectTreeItem>()
    val preload = RecentProjectsPreload(scope) { list }

    preload.job.join()
    assertThat(preload.take()).isSameAs(list)
    assertThat(preload.take()).isNull()
    scope.cancel()
  }

  @Test
  fun `a recent-project change after the start makes the list stale`(): Unit = timeoutRunBlocking {
    val scope = CoroutineScope(Job())
    val preload = RecentProjectsPreload(scope) { ArrayList() }

    preload.job.join()
    ApplicationManager.getApplication().messageBus.syncPublisher(RecentProjectsManager.RECENT_PROJECTS_CHANGE_TOPIC).change()
    assertThat(preload.take()).isNull()
    scope.cancel()
  }

  @Test
  fun `an unfinished collection is not awaited`(): Unit = timeoutRunBlocking {
    val scope = CoroutineScope(Job())
    val release = CountDownLatch(1)
    val preload = RecentProjectsPreload(scope) {
      release.await(1, TimeUnit.MINUTES)
      ArrayList()
    }

    assertThat(preload.take()).isNull()
    release.countDown()
    preload.job.join()
    assertThat(preload.take()).isNull()
    scope.cancel()
  }
}
