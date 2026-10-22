// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SeDecisionsEndpointSelectionTest {
  @Test
  fun `the first lease serves the popup and the next provider is not asked`() = runBlocking {
    val firstLease = TestLease("first")
    val second = TestProvider("second", TestLease("second"))

    val lease = selectDecisionsEndpoint(listOf(TestProvider("first", firstLease), second))

    assertSame(firstLease, lease)
    assertEquals(0, second.calls)
  }

  @Test
  fun `a provider that serves nothing is skipped`() = runBlocking {
    val secondLease = TestLease("second")

    val lease = selectDecisionsEndpoint(listOf(TestProvider("first", lease = null), TestProvider("second", secondLease)))

    assertSame(secondLease, lease)
  }

  @Test
  fun `a provider that throws is skipped`() = runBlocking {
    val failing = TestProvider("failing", failure = IllegalStateException("The proxy is broken"))
    val secondLease = TestLease("second")

    val lease = selectDecisionsEndpoint(listOf(failing, TestProvider("second", secondLease)))

    assertSame(secondLease, lease)
  }

  @Test
  fun `nothing serves the popup without a lease`() = runBlocking {
    assertNull(selectDecisionsEndpoint(listOf(TestProvider("first", lease = null))))
    assertNull(selectDecisionsEndpoint(emptyList()))
  }

  @Test
  fun `a provider that is cancelled while the caller is active is skipped`() = runBlocking {
    val cancelling = TestProvider("cancelling", failure = CancellationException("The scope of the provider is cancelled"))
    val secondLease = TestLease("second")

    val lease = selectDecisionsEndpoint(listOf(cancelling, TestProvider("second", secondLease)))

    assertSame(secondLease, lease)
  }

  @Test
  fun `a cancellation of the caller stops the selection`() = runBlocking {
    val cancellingCaller = object : SeDecisionsEndpointProvider {
      override val id: String get() = "cancelling the caller"

      override suspend fun acquire(): SeDecisionsEndpointLease? {
        currentCoroutineContext().cancel()
        awaitCancellation()
      }
    }
    val second = TestProvider("second", TestLease("second"))

    val selection = launch { selectDecisionsEndpoint(listOf(cancellingCaller, second)) }
    selection.join()

    assertTrue(selection.isCancelled)
    assertEquals(0, second.calls)
  }
}

private class TestProvider(
  override val id: String,
  private val lease: SeDecisionsEndpointLease? = null,
  private val failure: Throwable? = null,
) : SeDecisionsEndpointProvider {
  var calls: Int = 0
    private set

  override suspend fun acquire(): SeDecisionsEndpointLease? {
    calls++
    failure?.let { throw it }
    return lease
  }
}

private class TestLease(name: String) : SeDecisionsEndpointLease {
  override val url: String = "http://127.0.0.1/$name"
  override val headers: Map<String, String> = emptyMap()

  override suspend fun close() {}
}
