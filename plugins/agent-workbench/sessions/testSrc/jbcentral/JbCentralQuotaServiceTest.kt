// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
class JbCentralQuotaServiceTest {
  @Test
  fun requestRefreshInstantiatesApplicationServiceAndFetchesOffEdt() {
    val fetchRanOnEdt = AtomicReference<Boolean>()
    val expectedInfo = JbCentralQuotaInfo(
      email = "quota-test@jetbrains.com",
      licenseName = "JetBrains AI Ultimate",
      usedUsd = "12.34",
      totalUsd = "200.00",
      remainingUsd = "187.66",
      percentUsed = 6.17,
      resetDateText = "Jun 1, 2026",
    )
    val previousFactory = JbCentralQuotaServiceTestHook.replaceFetchQuotaForTest {
        fetchRanOnEdt.set(ApplicationManager.getApplication().isDispatchThread)
        JbCentralQuotaFetchResult(quotaInfo = expectedInfo)
    }

    try {
      val service = ApplicationManager.getApplication().service<JbCentralQuotaService>()

      runInEdtAndWait {
        service.requestRefresh()
      }

      runBlocking {
        withTimeout(5.seconds) {
          while (service.state.value.quotaInfo != expectedInfo) {
            delay(10.milliseconds)
          }
        }
      }

      assertThat(fetchRanOnEdt.get()).isFalse()
      assertThat(service.state.value.quotaInfo).isEqualTo(expectedInfo)
      assertThat(service.state.value.error).isNull()
      assertThat(service.state.value.isLoading).isFalse()
    }
    finally {
      JbCentralQuotaServiceTestHook.replaceFetchQuotaForTest(previousFactory)
    }
  }
}
