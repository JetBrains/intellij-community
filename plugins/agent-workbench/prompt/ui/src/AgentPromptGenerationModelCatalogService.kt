// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.ui

import com.intellij.agent.workbench.prompt.core.AgentPromptGenerationModel
import com.intellij.agent.workbench.prompt.core.withGroup
import com.intellij.agent.workbench.sessions.core.providers.AgentSessionProviderDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val MODEL_CATALOG_REFRESH_COOLDOWN = 30.seconds
private val MODEL_CATALOG_REFRESH_STATUS_DELAY = 3.seconds
private val LOG = logger<AgentPromptGenerationModelCatalogService>()

@Service(Service.Level.PROJECT)
internal class AgentPromptGenerationModelCatalogService(
  private val coroutineScope: CoroutineScope,
) {
  private val lock = Any()
  private var cachedCatalogsByProviderId: Map<String, CachedModelCatalog> = emptyMap()
  private var catalogStatesByProviderId: Map<String, AgentPromptGenerationModelCatalogState> = emptyMap()
  private val inFlightRefreshesByProviderId = LinkedHashMap<String, Deferred<List<AgentPromptGenerationModel>>>()

  fun cachedCatalog(providerId: String): List<AgentPromptGenerationModel>? {
    return synchronized(lock) { cachedCatalogsByProviderId[providerId]?.models }
  }

  fun catalogState(providerId: String): AgentPromptGenerationModelCatalogState? {
    return synchronized(lock) {
      catalogStatesByProviderId[providerId]
      ?: cachedCatalogsByProviderId[providerId]?.models?.let(AgentPromptGenerationModelCatalogState::Loaded)
    }
  }

  fun requestStateRefresh(
    provider: AgentSessionProviderDescriptor,
    project: Project?,
    onStateChanged: () -> Unit,
  ) {
    if (!provider.supportsGenerationModelSelection) {
      return
    }
    val providerId = provider.provider.value
    synchronized(lock) { inFlightRefreshesByProviderId[providerId] }?.let { refresh ->
      observeRefresh(providerId, refresh, catalogState(providerId)?.modelsOrNull(), onStateChanged)
      return
    }

    val cachedModels = catalogState(providerId)?.modelsOrNull()
    setCatalogState(
      providerId,
      if (cachedModels == null) AgentPromptGenerationModelCatalogState.Loading
      else AgentPromptGenerationModelCatalogState.Loaded(cachedModels),
    )
    val refresh = requestRefresh(provider, project)
    observeRefresh(providerId, refresh, cachedModels, onStateChanged)
  }

  private fun observeRefresh(
    providerId: String,
    refresh: Deferred<List<AgentPromptGenerationModel>>,
    cachedModels: List<AgentPromptGenerationModel>?,
    onStateChanged: () -> Unit,
  ) {
    if (cachedModels != null) {
      coroutineScope.launch {
        delay(MODEL_CATALOG_REFRESH_STATUS_DELAY)
        withContext(Dispatchers.EDT) {
          if (!refresh.isCompleted && catalogState(providerId) == AgentPromptGenerationModelCatalogState.Loaded(cachedModels)) {
            setCatalogState(providerId, AgentPromptGenerationModelCatalogState.Refreshing(cachedModels))
            onStateChanged()
          }
        }
      }
    }
    coroutineScope.launch {
      val result = runCatching { refresh.await() }
      withContext(Dispatchers.EDT) {
        setCatalogState(
          providerId,
          result.fold(
            onSuccess = { models ->
              AgentPromptGenerationModelCatalogState.Loaded(models)
            },
            onFailure = {
              val fallbackModels = cachedCatalog(providerId) ?: cachedModels
              if (fallbackModels == null) {
                AgentPromptGenerationModelCatalogState.Failed
              }
              else {
                AgentPromptGenerationModelCatalogState.RefreshFailed(fallbackModels)
              }
            },
          ),
        )
        onStateChanged()
      }
    }
  }

  fun requestRefresh(
    provider: AgentSessionProviderDescriptor,
    project: Project?,
  ): Deferred<List<AgentPromptGenerationModel>> {
    val providerId = provider.provider.value
    synchronized(lock) { inFlightRefreshesByProviderId[providerId] }?.let { return it }
    val freshCachedModels = synchronized(lock) {
      cachedCatalogsByProviderId[providerId]
        ?.takeIf { catalog -> catalog.isFresh() }
        ?.models
    }
    if (freshCachedModels != null) {
      return CompletableDeferred(freshCachedModels)
    }

    val deferred = coroutineScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
      try {
        val models = provider.listAvailableGenerationModels(project).normalizedModelCatalog()
        synchronized(lock) {
          cachedCatalogsByProviderId = cachedCatalogsByProviderId + (providerId to CachedModelCatalog(models, System.currentTimeMillis()))
        }
        models
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        LOG.warn("Failed to refresh generation model catalog for provider '$providerId'", e)
        throw e
      }
    }
    val existingDeferred = synchronized(lock) {
      inFlightRefreshesByProviderId[providerId] ?: deferred.also {
        inFlightRefreshesByProviderId[providerId] = it
      }
    }
    if (existingDeferred !== deferred) {
      deferred.cancel()
      return existingDeferred
    }
    deferred.invokeOnCompletion {
      synchronized(lock) {
        if (inFlightRefreshesByProviderId[providerId] === deferred) {
          inFlightRefreshesByProviderId.remove(providerId)
        }
      }
    }
    deferred.start()
    return deferred
  }

  @TestOnly
  fun clearForTest() {
    val inFlightRefreshes = synchronized(lock) {
      val refreshes = inFlightRefreshesByProviderId.values.toList()
      cachedCatalogsByProviderId = emptyMap()
      catalogStatesByProviderId = emptyMap()
      inFlightRefreshesByProviderId.clear()
      refreshes
    }
    inFlightRefreshes.forEach { refresh -> refresh.cancel() }
  }

  @TestOnly
  fun ageCachedCatalogForTest(providerId: String, age: Duration) {
    synchronized(lock) {
      val cachedCatalog = cachedCatalogsByProviderId[providerId] ?: return
      cachedCatalogsByProviderId = cachedCatalogsByProviderId + (providerId to cachedCatalog.copy(
        refreshedAtMs = System.currentTimeMillis() - age.inWholeMilliseconds,
      ))
    }
  }

  private fun setCatalogState(providerId: String, state: AgentPromptGenerationModelCatalogState) {
    synchronized(lock) {
      catalogStatesByProviderId = catalogStatesByProviderId + (providerId to state)
    }
  }
}

private data class CachedModelCatalog(
  @JvmField val models: List<AgentPromptGenerationModel>,
  @JvmField val refreshedAtMs: Long,
) {
  fun isFresh(): Boolean {
    return System.currentTimeMillis() - refreshedAtMs < MODEL_CATALOG_REFRESH_COOLDOWN.inWholeMilliseconds
  }
}

private fun List<AgentPromptGenerationModel>.normalizedModelCatalog(): List<AgentPromptGenerationModel> {
  return asSequence()
    .filter { model -> model.id.isNotBlank() }
    .distinctBy { model -> model.id }
    .map { model ->
      val trimmedId = model.id.trim()
      model.copy(
        id = trimmedId,
        displayName = model.displayName.trim().takeIf { it.isNotEmpty() } ?: trimmedId,
      ).withGroup(model.group)
    }
    .toList()
}
