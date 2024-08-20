// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.FlowTestUtil.assertEmits
import com.intellij.collaboration.util.MainDispatcherRule
import com.intellij.util.messages.MessageBus
import io.mockk.coEvery
import io.mockk.coVerifyAll
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequest
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.GHPRMergeabilityState
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRDetailsService
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.time.Duration.Companion.seconds

private val PR_ID = GHPRIdentifier("id", 0)
private val EXCEPTION = object : RuntimeException("TEST_LOADING_EXCEPTION") {}

@RunWith(JUnit4::class)
class GHPRDetailsDataProviderImplTest {

  companion object {
    @JvmField
    @ClassRule
    internal val mainRule = MainDispatcherRule()
  }

  private val detailsService = mockk<GHPRDetailsService>(relaxUnitFun = true)
  private val listener = mockk<GHPRDataOperationsListener>(relaxUnitFun = true)
  private val messageBus = mockk<MessageBus> {
    every { syncPublisher(GHPRDataOperationsListener.TOPIC) } returns listener
  }

  private fun TestScope.createProvider() = GHPRDetailsDataProviderImpl(backgroundScope, detailsService, PR_ID, messageBus)

  @Test
  fun testCachingDetailsLoad() = runTest {
    val result = mockk<GHPullRequest>()
    coEvery { detailsService.loadDetails(PR_ID) } returns result

    val inst = createProvider()
    assertEquals(inst.loadDetails(), result)
    assertEquals(inst.loadedDetails, result)
    assertEquals(inst.loadDetails(), result)
    assertEquals(inst.loadedDetails, result)

    coVerifyAll {
      detailsService.loadDetails(PR_ID)
    }
  }

  @Test
  fun testDetailsErrorLoad() = runTest {
    coEvery { detailsService.loadDetails(PR_ID) } throws EXCEPTION

    val inst = createProvider()
    runCatching {
      inst.loadDetails()
    }.apply {
      assertEquals(EXCEPTION, exceptionOrNull())
    }

    coVerifyAll { detailsService.loadDetails(PR_ID) }
  }

  @Test
  fun testDetailsReloadAfterError() = runTest {
    val result = mockk<GHPullRequest>()
    var counter = 0
    coEvery { detailsService.loadDetails(PR_ID) } coAnswers {
      if (counter == 0) {
        throw EXCEPTION
      }
      else {
        result
      }
    }

    val inst = createProvider()
    runCatching { inst.loadDetails() }.apply { assertEquals(EXCEPTION, exceptionOrNull()) }
    counter++
    inst.signalDetailsNeedReload()
    runCatching { inst.loadDetails() }.apply { assertEquals(result, getOrThrow()) }

    coVerifyAll {
      detailsService.loadDetails(PR_ID)
      detailsService.loadDetails(PR_ID)
    }
  }

  @Test
  fun testUpdate() = runTest {
    val result1 = mockk<GHPullRequest>()
    val result2 = mockk<GHPullRequest>()
    coEvery { detailsService.loadDetails(PR_ID) } returns result1
    coEvery { detailsService.updateDetails(eq(PR_ID), any(), any()) } returns result2

    val inst = createProvider()
    assertEquals(inst.loadDetails(), result1)
    inst.updateDetails("", "")
    assertEquals(inst.loadDetails(), result2)

    coVerifyAll {
      detailsService.updateDetails(eq(PR_ID), any(), any())
      detailsService.loadDetails(PR_ID)
      listener.onMetadataChanged()
    }
  }

  @Test
  fun testCachingMergeabilityLoad() = runTest {
    val result = mockk<GHPRMergeabilityState>()
    coEvery { detailsService.loadMergeabilityState(PR_ID) } returns result

    val inst = createProvider()
    assertEquals(inst.loadMergeabilityState(), result)
    assertEquals(inst.loadMergeabilityState(), result)

    coVerifyAll { detailsService.loadMergeabilityState(PR_ID) }
  }

  @Test
  fun testMergeabilityErrorLoad() = runTest {
    coEvery { detailsService.loadMergeabilityState(PR_ID) } throws EXCEPTION

    val inst = createProvider()
    runCatching {
      inst.loadMergeabilityState()
    }.apply {
      assertEquals(EXCEPTION, exceptionOrNull())
    }

    coVerifyAll { detailsService.loadMergeabilityState(PR_ID) }
  }

  @Test
  fun testMergeabilityReloadAfterError() = runTest {
    val result = mockk<GHPRMergeabilityState>()
    var counter = 0
    coEvery { detailsService.loadMergeabilityState(PR_ID) } coAnswers {
      if (counter == 0) {
        throw EXCEPTION
      }
      else {
        result
      }
    }

    val inst = createProvider()
    runCatching { inst.loadMergeabilityState() }.apply { assertEquals(EXCEPTION, exceptionOrNull()) }
    counter++
    inst.signalMergeabilityNeedsReload()
    runCatching { inst.loadMergeabilityState() }.apply { assertEquals(result, getOrThrow()) }

    coVerifyAll {
      detailsService.loadMergeabilityState(PR_ID)
      detailsService.loadMergeabilityState(PR_ID)
    }
  }

  @Test
  fun testReviewers() = runTest {
    val inst = createProvider()
    inst.adjustReviewers(mockk())

    coVerifyAll {
      detailsService.adjustReviewers(eq(PR_ID), any())
      listener.onMetadataChanged()
    }
  }

  @Test
  fun testClose() = runTest {
    val inst = createProvider()
    inst.close()

    coVerifyAll {
      detailsService.close(eq(PR_ID))
      listener.onMetadataChanged()
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun testDetailsFlow() = runTest(UnconfinedTestDispatcher()) {
    val result = mockk<GHPullRequest>()
    var counter = 0
    coEvery { detailsService.loadDetails(PR_ID) } coAnswers {
      if (counter == 0) {
        delay(1.seconds)
        throw EXCEPTION
      }
      else {
        delay(1.seconds)
        result
      }
    }
    val inst = createProvider()
    inst.detailsComputationFlow.assertEmits(ComputedResult.loading(),
                                            ComputedResult.failure(EXCEPTION),
                                            ComputedResult.loading(),
                                            ComputedResult.success(result)) {
      advanceTimeBy(2.seconds)
      counter++
      inst.signalDetailsNeedReload()
      advanceTimeBy(2.seconds)
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun testMergeabilityFlow() = runTest(UnconfinedTestDispatcher()) {
    val result = mockk<GHPRMergeabilityState>()
    var counter = 0
    coEvery { detailsService.loadMergeabilityState(PR_ID) } coAnswers {
      if (counter == 0) {
        delay(1.seconds)
        throw EXCEPTION
      }
      else {
        delay(1.seconds)
        result
      }
    }
    val inst = createProvider()
    inst.mergeabilityStateComputationFlow.assertEmits(ComputedResult.loading(),
                                                      ComputedResult.failure(EXCEPTION),
                                                      ComputedResult.loading(),
                                                      ComputedResult.success(result)) {
      advanceTimeBy(2.seconds)
      counter++
      inst.signalMergeabilityNeedsReload()
      advanceTimeBy(2.seconds)
    }
  }
}