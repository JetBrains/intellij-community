// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.impl

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.testFramework.common.timeoutRunBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

internal class MainMenuFillPermitTest {
  @Test
  fun `a frame that is not initialized early gets the permit at once`() {
    assertThat(createMainMenuFillPermit(OpenProjectTask { isNewProject = true }).isCompleted).isTrue()
    assertThat(createMainMenuFillPermit(OpenProjectTask { beforeOpen = { true } }).isCompleted).isTrue()
    assertThat(createMainMenuFillPermit(OpenProjectTask {}).isCompleted).isFalse()
  }

  @Test
  fun `the permit comes when the frame content paints`(): Unit = timeoutRunBlocking {
    val scope = CoroutineScope(Job())
    val permit = Job()
    val contentPainted = CompletableDeferred<Unit>()

    permitMainMenuFillOnContentPainted(scope = scope, permit = permit, contentPainted = contentPainted, timeout = 1.minutes)

    delay(100.milliseconds)
    assertThat(permit.isCompleted).isFalse()

    contentPainted.complete(Unit)
    permit.join()
    scope.cancel()
  }

  @Test
  fun `the permit comes after the timeout when the frame content does not paint`(): Unit = timeoutRunBlocking {
    val scope = CoroutineScope(Job())
    val permit = Job()
    val start = TimeSource.Monotonic.markNow()

    val contentPainted = CompletableDeferred<Unit>()

    permitMainMenuFillOnContentPainted(scope = scope, permit = permit, contentPainted = contentPainted, timeout = 200.milliseconds)
    permit.join()

    assertThat(start.elapsedNow().inWholeMilliseconds).isGreaterThanOrEqualTo(200)
    scope.cancel()
  }

  @Test
  fun `the permit comes when the project scope ends before the paint`(): Unit = timeoutRunBlocking {
    val scope = CoroutineScope(Job())
    val permit = Job()

    permitMainMenuFillOnContentPainted(scope = scope, permit = permit, contentPainted = CompletableDeferred<Unit>(), timeout = 1.minutes)
    scope.cancel()

    permit.join()
  }

  @Test
  fun `the permit comes when the paint deferred is cancelled by the project disposal`(): Unit = timeoutRunBlocking {
    val scope = CoroutineScope(Job())
    val permit = Job()
    val contentPainted = CompletableDeferred<Unit>()

    permitMainMenuFillOnContentPainted(scope = scope, permit = permit, contentPainted = contentPainted, timeout = 1.minutes)
    contentPainted.cancel()

    permit.join()
    scope.cancel()
  }
}
