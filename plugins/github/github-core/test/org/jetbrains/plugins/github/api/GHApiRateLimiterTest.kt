// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.api

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.registry.Registry
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private const val REPO_PATH = "/repos/owner/repo"

/**
 * Checks the limit of concurrent requests of [GHApiRateLimiter].
 */
@TestApplication
internal class GHApiRateLimiterTest {
  @TestDisposable
  lateinit var testDisposable: Disposable

  @Test
  fun `a new limit of concurrent requests applies to a server in use without a restart`(): Unit = timeoutRunBlocking {
    Registry.get(MAX_CONCURRENT_REQUESTS_REGISTRY_KEY).setValue(1, testDisposable)
    val limiter = GHApiRateLimiter()
    val running = CompletableDeferred<Unit>()
    val finish = CompletableDeferred<Unit>()
    val first = launch {
      limiter.execute(restRequest()) {
        running.complete(Unit)
        finish.await()
        TestRepo("repo")
      }
    }
    running.await()

    // with one permit, the second request waits for the first one
    val second = withTimeoutOrNull(200.milliseconds) {
      limiter.execute(restRequest()) { TestRepo("repo") }
    }
    assertNull(second)

    Registry.get(MAX_CONCURRENT_REQUESTS_REGISTRY_KEY).setValue(2, testDisposable)
    withTimeout(5.seconds) {
      limiter.execute(restRequest()) { TestRepo("repo") }
    }

    finish.complete(Unit)
    first.join()
  }

  private fun restRequest(): GithubApiRequest<TestRepo> =
    GithubApiRequest.Get.Json("https://example.com$REPO_PATH", TestRepo::class.java)
}
