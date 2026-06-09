// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions.service

import com.intellij.agent.workbench.common.session.AgentSessionProvider
import com.intellij.agent.workbench.common.session.AgentSessionThread
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviders
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionSource
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

private val LOG = logger<AgentSessionThreadLoadSupport>()

internal class AgentSessionThreadLoadSupport(
  private val sessionSourcesProvider: () -> List<AgentSessionSource>,
  private val applyArchiveSuppressions: (
    path: String,
    provider: AgentSessionProvider,
    threads: List<AgentSessionThread>,
  ) -> List<AgentSessionThread>,
  private val resolveErrorMessage: (AgentSessionProvider, Throwable) -> String,
  private val resolveProviderWarningMessage: (AgentSessionProvider, Throwable) -> String,
  private val providerDescriptorProvider: (AgentSessionProvider) -> AgentSessionProviderDescriptor? = AgentSessionProviders::find,
) {
  suspend fun loadThreadsFromClosedProject(path: String): AgentSessionLoadResult {
    return loadThreads(path) { source ->
      source.listThreadsFromClosedProject(path = path)
    }
  }

  private suspend fun loadThreads(
    path: String,
    loadOperation: suspend (AgentSessionSource) -> List<AgentSessionThread>,
  ): AgentSessionLoadResult {
    val sourceResults = coroutineScope {
      sessionSourcesProvider().map { source ->
        async {
          if (isProviderCliMissing(source.provider)) {
            return@async null
          }
          val result = try {
            Result.success(
              applyArchiveSuppressions(
                path,
                source.provider,
                loadOperation(source),
              ),
            )
          }
          catch (throwable: Throwable) {
            if (throwable is CancellationException) throw throwable
            if (isProviderCliMissingError(source.provider, throwable)) return@async null
            LOG.warn("Failed to load ${source.provider.value} sessions for $path", throwable)
            Result.failure(throwable)
          }
          AgentSessionSourceLoadResult(
            provider = source.provider,
            result = result,
            hasUnknownTotal = result.isSuccess && !source.canReportExactThreadCount,
          )
        }
      }.awaitAll().filterNotNull()
    }
    return mergeAgentSessionSourceLoadResults(
      sourceResults = sourceResults,
      resolveErrorMessage = resolveErrorMessage,
      resolveWarningMessage = resolveProviderWarningMessage,
    )
  }

  private suspend fun loadSourceResultForOpenProject(
    source: AgentSessionSource,
    normalizedPath: String,
    project: Project,
    prefetchedByProvider: Map<AgentSessionProvider, Map<String, List<AgentSessionThread>>>,
    originalPath: String,
    cliAvailabilityByProvider: Map<AgentSessionProvider, Boolean>? = null,
  ): AgentSessionSourceLoadResult? {
    if (isProviderCliMissing(source.provider, cliAvailabilityByProvider)) return null
    return try {
      val prefetched = prefetchedByProvider[source.provider]?.get(normalizedPath)
      val threads = applyArchiveSuppressions(
        normalizedPath,
        source.provider,
        prefetched ?: source.listThreadsFromOpenProject(path = normalizedPath, project = project),
      )
      AgentSessionSourceLoadResult(
        provider = source.provider,
        result = Result.success(threads),
        hasUnknownTotal = !source.canReportExactThreadCount,
      )
    }
    catch (e: Throwable) {
      if (e is CancellationException) throw e
      if (isProviderCliMissingError(source.provider, e)) return null
      LOG.warn("Failed to load ${source.provider.value} sessions for $originalPath", e)
      AgentSessionSourceLoadResult(
        provider = source.provider,
        result = Result.failure(e),
      )
    }
  }

  private suspend fun isProviderCliMissing(provider: AgentSessionProvider): Boolean {
    return isProviderCliMissing(provider, cliAvailabilityByProvider = null)
  }

  private suspend fun isProviderCliMissing(
    provider: AgentSessionProvider,
    cliAvailabilityByProvider: Map<AgentSessionProvider, Boolean>?,
  ): Boolean {
    cliAvailabilityByProvider?.get(provider)?.let { available -> return !available }
    val descriptor = providerDescriptorProvider(provider) ?: return false
    return !descriptor.isCliAvailable()
  }

  private fun isProviderCliMissingError(provider: AgentSessionProvider, throwable: Throwable): Boolean {
    return providerDescriptorProvider(provider)?.isCliMissingError(throwable) == true
  }

  suspend fun loadSourcesIncrementally(
    sessionSources: List<AgentSessionSource>,
    normalizedPath: String,
    project: Project,
    prefetchedByProvider: Map<AgentSessionProvider, Map<String, List<AgentSessionThread>>>,
    originalPath: String,
    cliAvailabilityByProvider: Map<AgentSessionProvider, Boolean>? = null,
    onPartialResult: (AgentSessionLoadResult, isComplete: Boolean) -> Unit,
  ): AgentSessionLoadResult {
    val sourceResults = CopyOnWriteArrayList<AgentSessionSourceLoadResult>()
    val totalSourceCount = sessionSources.size
    val completedSourceCount = AtomicInteger()
    coroutineScope {
      for (source in sessionSources) {
        launch {
          val sourceResult = loadSourceResultForOpenProject(
            source = source,
            normalizedPath = normalizedPath,
            project = project,
            prefetchedByProvider = prefetchedByProvider,
            originalPath = originalPath,
            cliAvailabilityByProvider = cliAvailabilityByProvider,
          )
          if (sourceResult != null) {
            sourceResults.add(sourceResult)
          }
          val partial = mergeAgentSessionSourceLoadResults(
            sourceResults = sourceResults.toList(),
            resolveErrorMessage = resolveErrorMessage,
            resolveWarningMessage = resolveProviderWarningMessage,
          )
          onPartialResult(partial, completedSourceCount.incrementAndGet() == totalSourceCount)
        }
      }
    }
    return mergeAgentSessionSourceLoadResults(
      sourceResults = sourceResults.toList(),
      resolveErrorMessage = resolveErrorMessage,
      resolveWarningMessage = resolveProviderWarningMessage,
    )
  }
}
