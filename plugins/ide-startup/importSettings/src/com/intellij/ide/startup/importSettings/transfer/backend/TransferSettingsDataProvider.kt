// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.transfer.backend

import com.intellij.ide.startup.importSettings.fus.TransferSettingsCollector
import com.intellij.ide.startup.importSettings.models.BaseIdeVersion
import com.intellij.ide.startup.importSettings.models.FailedIdeVersion
import com.intellij.ide.startup.importSettings.providers.TransferSettingsProvider
import com.intellij.ide.startup.importSettings.transfer.backend.models.IdeVersion
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.job
import java.util.Objects
import java.util.stream.Collectors
import kotlin.time.Duration.Companion.nanoseconds

class TransferSettingsDataProvider(val providers: List<TransferSettingsProvider>) {
  private val baseIdeVersions = mutableListOf<BaseIdeVersion>()
  private val ideVersions = mutableListOf<IdeVersion>()
  private val failedIdeVersions = mutableListOf<FailedIdeVersion>()

  val orderedIdeVersions: List<BaseIdeVersion> get() = ideVersions + failedIdeVersions

  constructor(vararg providers: TransferSettingsProvider) : this(providers.toList())

  suspend fun hasDataToImport(): Boolean =
    coroutineScope {
      val result: Boolean = providers.map { provider ->
        async {
          logger.runAndLogException { provider.isAvailable() && provider.hasDataToImport() } ?: false
        }::await.asFlow()
      }.merge().firstOrNull { it } ?: false

      coroutineContext.job.cancelChildren()
      result
    }

  fun refresh(): TransferSettingsDataProvider {
    baseIdeVersions.clear()
    ideVersions.clear()
    failedIdeVersions.clear()

    val newBase = TransferSettingsDataProviderSession(providers, baseIdeVersions.map { it.id }).baseIdeVersions
    baseIdeVersions.addAll(newBase)

    ideVersions.addAll(newBase.filterIsInstance<IdeVersion>())
    ideVersions.sortWith(
      compareBy<IdeVersion> { it.sortKey }
        .thenComparing(compareByDescending { it.lastUsed })
    )
    TransferSettingsCollector.logIdeVersionsFound(ideVersions)

    failedIdeVersions.addAll(newBase.filterIsInstance<FailedIdeVersion>())
    TransferSettingsCollector.logIdeVersionsFailed(failedIdeVersions)

    return this
  }
}

private class TransferSettingsDataProviderSession(private val providers: List<TransferSettingsProvider>,
                                                  private val skipIds: List<String>?) {

  val baseIdeVersions: List<BaseIdeVersion> by lazy { createBaseIdeVersions() }

  private fun createBaseIdeVersions() = providers
    .parallelStream()
    .flatMap { provider ->
      if (!provider.isAvailable()) {
        logger.info("Provider ${provider.name} is not available")
        return@flatMap null
      }

      try {
        val startTime = System.nanoTime().nanoseconds
        @Suppress("SSBasedInspection") // we are ok with Steam API because of parallelStream
        val result = provider.getIdeVersions(skipIds ?: emptyList()).stream()
        val endTime = System.nanoTime().nanoseconds
        TransferSettingsCollector.logPerformanceMeasured(
          TransferSettingsCollector.PerformanceMetricType.Total,
          provider.transferableIdeId,
          null,
          endTime - startTime
        )
        result
      }
      catch (t: Throwable) {
        logger.warn("Failed to get base ide versions", t)
        return@flatMap null
      }
    }
    .filter(Objects::nonNull)
    .collect(Collectors.toList())
}

private val logger = logger<TransferSettingsDataProvider>()
