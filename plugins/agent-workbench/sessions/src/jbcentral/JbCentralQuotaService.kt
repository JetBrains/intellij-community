// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.jbcentral

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly

@Service(Service.Level.APP)
internal class JbCentralQuotaService(
  private val serviceScope: CoroutineScope,
) {
  private val refreshMutex = Mutex()
  private val mutableState = MutableStateFlow(JbCentralQuotaState())
  val state: StateFlow<JbCentralQuotaState> = mutableState.asStateFlow()

  fun requestRefresh() {
    serviceScope.launch(Dispatchers.IO) {
      refresh()
    }
  }

  private suspend fun refresh() {
    if (!refreshMutex.tryLock()) return
    val previousState = mutableState.value
    try {
      mutableState.value = previousState.copy(isLoading = true, error = null)
      val result = withContext(Dispatchers.IO) { JbCentralQuotaServiceTestHook.fetchQuota() }
      val newState = when {
        result.quotaInfo != null -> {
          result.quotaInfo.usedUsd.toDoubleOrNull()?.let { used ->
            service<JbCentralQuotaUsageHistoryService>().record(used)
          }
          JbCentralQuotaState(quotaInfo = result.quotaInfo)
        }
        else -> previousState.copy(error = result.error, isLoading = false)
      }
      mutableState.value = newState
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      mutableState.value = previousState.copy(error = JbCentralQuotaError.COMMAND_FAILED, isLoading = false)
    }
    finally {
      refreshMutex.unlock()
    }
  }
}

internal object JbCentralQuotaServiceTestHook {
  @Volatile
  private var fetchQuotaOverride: (() -> JbCentralQuotaFetchResult)? = null

  fun fetchQuota(): JbCentralQuotaFetchResult {
    return fetchQuotaOverride?.invoke() ?: JbCentralQuotaCliClient().fetchQuota()
  }

  @TestOnly
  fun replaceFetchQuotaForTest(fetchQuota: (() -> JbCentralQuotaFetchResult)?): (() -> JbCentralQuotaFetchResult)? {
    val previous = fetchQuotaOverride
    fetchQuotaOverride = fetchQuota
    return previous
  }
}
