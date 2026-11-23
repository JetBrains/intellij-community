// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.impl

import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.StartupUiGate
import com.intellij.vcs.log.impl.IdeVcsProjectLog.InitLogStartupActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@TestApplication
internal class InitLogStartupActivityGateTest {
  companion object {
    private val projectFixture = projectFixture()
  }

  private val project get() = projectFixture.get()

  @AfterEach
  fun tearDown() {
    StartupUiGate.resetForTest()
  }

  @Test
  fun theLogWaitsForTheGate(): Unit = timeoutRunBlocking {
    StartupUiGate.resetForTest()
    StartupUiGate.arm()

    val job = launch { InitLogStartupActivity.initOnStartup(project) }
    yield()
    delay(200.milliseconds)
    assertThat(job.isActive).isTrue()

    StartupUiGate.markEditorsShown()
    job.join()
    assertThat(job.isCompleted).isTrue()
  }

  @Test
  fun theLogRunsWhenTheGateIsNotArmed(): Unit = timeoutRunBlocking {
    StartupUiGate.resetForTest()

    val job = launch { InitLogStartupActivity.initOnStartup(project) }
    job.join()
    assertThat(job.isCompleted).isTrue()
  }
}
