package org.jetbrains.plugins.github.pullrequest.data.provider

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.util.ComputedResult
import com.intellij.collaboration.util.FlowTestUtil.assertEmits
import com.intellij.collaboration.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyAll
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.jetbrains.plugins.github.api.data.pullrequest.GHPullRequestChangedFile
import org.jetbrains.plugins.github.pullrequest.data.GHPRIdentifier
import org.jetbrains.plugins.github.pullrequest.data.service.GHPRFilesService
import org.junit.Assert.assertEquals
import org.junit.ClassRule
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class GHPRViewedStateDataProviderImplTest {
  companion object {
    private val PR_ID = GHPRIdentifier("id", 0)
    private val EXCEPTION = object : RuntimeException("TEST_LOADING_EXCEPTION") {}

    @JvmField
    @ClassRule
    internal val mainRule = MainDispatcherRule()
  }

  private val filesService = mockk<GHPRFilesService>()

  private fun TestScope.createProvider(): GHPRViewedStateDataProvider =
    GHPRViewedStateDataProviderImpl(backgroundScope, filesService, PR_ID)

  @Test
  fun testCachingStateLoad() = runTest {
    val result = emptyList<GHPullRequestChangedFile>()
    coEvery { filesService.loadFiles(PR_ID) } returns result

    val inst = createProvider()
    assertEquals(inst.loadViewedState(), inst.loadViewedState())

    coVerifyAll { filesService.loadFiles(eq(PR_ID)) }
  }

  @Test
  fun testReloadOnError() = runTest {
    coEvery { filesService.updateViewedState(eq(PR_ID), any(), any()) } throws EXCEPTION

    val inst = createProvider()
    val signalAwaiter = launchNow {
      inst.viewedStateNeedsReloadSignal.first()
    }
    inst.updateViewedState(listOf("1", "2", "3"), false)

    withTimeout(50.milliseconds) {
      signalAwaiter.join()
    }

    coVerify { filesService.updateViewedState(eq(PR_ID), any(), any()) }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  @Test
  fun testChangesFlow() = runTest(UnconfinedTestDispatcher()) {
    val result = emptyList<GHPullRequestChangedFile>()
    var counter = 0
    coEvery { filesService.loadFiles(eq(PR_ID)) } coAnswers {
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
    inst.viewedStateComputationState.assertEmits(ComputedResult.loading(),
                                                 ComputedResult.failure(EXCEPTION),
                                                 ComputedResult.loading(),
                                                 ComputedResult.success(emptyMap())) {
      advanceTimeBy(2.seconds)
      counter++
      inst.signalViewedStateNeedsReload()
      advanceTimeBy(2.seconds)
    }
  }
}