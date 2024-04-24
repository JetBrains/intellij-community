// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.FlowTestUtil.assertEmits
import com.intellij.collaboration.util.MainDispatcherRule
import com.intellij.util.messages.MessageBus
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
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mock
import org.mockito.junit.MockitoJUnit
import org.mockito.junit.MockitoRule
import org.mockito.kotlin.*
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

  @Rule
  @JvmField
  internal val mockitoRule: MockitoRule = MockitoJUnit.rule()

  @Mock
  internal lateinit var detailsService: GHPRDetailsService

  @Mock
  internal lateinit var messageBus: MessageBus

  @Mock
  internal lateinit var listener: GHPRDataOperationsListener

  @Before
  internal fun setup() {
    whenever(messageBus.syncPublisher(GHPRDataOperationsListener.TOPIC)) doReturn listener
  }

  private fun TestScope.createProvider() = GHPRDetailsDataProviderImpl(backgroundScope, detailsService, PR_ID, messageBus)

  @Test
  fun testCachingDetailsLoad() = runTest {
    val result = mock<GHPullRequest>()
    whenever(detailsService.loadDetails(PR_ID)) doReturn result

    val inst = createProvider()
    assertEquals(inst.loadDetails(), result)
    assertEquals(inst.loadedDetails, result)
    assertEquals(inst.loadDetails(), result)
    assertEquals(inst.loadedDetails, result)

    verify(detailsService, times(1)).loadDetails(PR_ID)
  }

  @Test
  fun testDetailsErrorLoad() = runTest {
    whenever(detailsService.loadDetails(PR_ID)) doThrow EXCEPTION

    val inst = createProvider()
    runCatching {
      inst.loadDetails()
    }.apply {
      assertEquals(EXCEPTION, exceptionOrNull())
    }

    verify(detailsService, times(1)).loadDetails(PR_ID)
  }

  @Test
  fun testDetailsReloadAfterError() = runTest {
    val result = mock<GHPullRequest>()
    var counter = 0
    whenever(detailsService.loadDetails(PR_ID)) doSuspendableAnswer {
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

    verify(detailsService, times(2)).loadDetails(PR_ID)
  }

  @Test
  fun testUpdate() = runTest {
    val result1 = mock<GHPullRequest>()
    val result2 = mock<GHPullRequest>()
    whenever(detailsService.loadDetails(PR_ID)) doReturn result1
    whenever(detailsService.updateDetails(eq(PR_ID), any(), any())) doReturn result2

    val inst = createProvider()
    assertEquals(inst.loadDetails(), result1)
    inst.updateDetails("", "")
    assertEquals(inst.loadDetails(), result2)

    verify(detailsService, times(1)).updateDetails(eq(PR_ID), any(), any())
    verify(detailsService, times(1)).loadDetails(PR_ID)
    verify(listener, times(1)).onMetadataChanged()
  }

  @Test
  fun testCachingMergeabilityLoad() = runTest {
    val result = mock<GHPRMergeabilityState>()
    whenever(detailsService.loadMergeabilityState(PR_ID)) doReturn result

    val inst = createProvider()
    assertEquals(inst.loadMergeabilityState(), result)
    assertEquals(inst.loadMergeabilityState(), result)

    verify(detailsService, times(1)).loadMergeabilityState(PR_ID)
  }

  @Test
  fun testMergeabilityErrorLoad() = runTest {
    whenever(detailsService.loadMergeabilityState(PR_ID)) doThrow EXCEPTION

    val inst = createProvider()
    runCatching {
      inst.loadMergeabilityState()
    }.apply {
      assertEquals(EXCEPTION, exceptionOrNull())
    }

    verify(detailsService, times(1)).loadMergeabilityState(PR_ID)
  }

  @Test
  fun testMergeabilityReloadAfterError() = runTest {
    val result = mock<GHPRMergeabilityState>()
    var counter = 0
    whenever(detailsService.loadMergeabilityState(PR_ID)) doSuspendableAnswer {
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

    verify(detailsService, times(2)).loadMergeabilityState(PR_ID)
  }

  @Test
  fun testReviewers() = runTest {
    val inst = createProvider()
    inst.adjustReviewers(mock())

    verify(detailsService, never()).loadDetails(PR_ID)
    verify(detailsService, times(1)).adjustReviewers(eq(PR_ID), any())
    verify(listener, times(1)).onMetadataChanged()
  }

  @Test
  fun testClose() = runTest {
    val inst = createProvider()
    inst.close()

    verify(detailsService, never()).loadDetails(PR_ID)
    verify(detailsService, times(1)).close(PR_ID)
    verify(listener, times(1)).onMetadataChanged()
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun testDetailsFlow() = runTest(UnconfinedTestDispatcher()) {
    val result = mock<GHPullRequest>()
    var counter = 0
    whenever(detailsService.loadDetails(PR_ID)) doSuspendableAnswer {
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
    val result = mock<GHPRMergeabilityState>()
    var counter = 0
    whenever(detailsService.loadMergeabilityState(PR_ID)) doSuspendableAnswer {
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