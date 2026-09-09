// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.CustomPluginRepository
import com.intellij.ide.plugins.newui.CustomPluginRepositoryLoadResult
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.HashMap
import java.util.concurrent.CancellationException

@OptIn(ExperimentalCoroutinesApi::class)
internal class UnifiedPluginRepositorySourceCoordinatorTest {
  @Test
  fun `catalog order is published before repositories settle independently`() = runTest {
    val first = repository("first")
    val second = repository("second")
    val firstResult = CompletableDeferred<CustomPluginRepositoryLoadResult>()
    val secondResult = CompletableDeferred<CustomPluginRepositoryLoadResult>()
    val provider = FakeRepositoryDataProvider(
      catalogLoader = { catalog(first, second) },
      repositoryLoader = { repository, _ -> if (repository == first) firstResult.await() else secondResult.await() },
    )
    val coordinator = coordinator(provider)

    coordinator.start()
    runCurrent()

    assertThat(sectionIds(coordinator)).containsExactly("first", "second")
    assertThat(coordinator.state.value.sections).allMatch { it.status == PluginSectionStatus.Loading(false) }
    assertThat(coordinator.state.value.repositoryPlugins).isNull()
    assertThat(coordinator.state.value.catalogLoading).isFalse()
    assertThat(coordinator.state.value.facetsLoading).isTrue()

    secondResult.complete(loadResult("shared.plugin", "second.plugin"))
    runCurrent()

    assertThat(coordinator.state.value.sections[0].status).isEqualTo(PluginSectionStatus.Loading(false))
    assertThat(coordinator.state.value.sections[1].status).isEqualTo(PluginSectionStatus.Ready)
    assertThat(coordinator.state.value.repositoryPlugins).isNull()
    assertThat(coordinator.state.value.settledRepositoryPlugins.mapValues { (_, models) ->
      models.map { it.pluginId.idString }
    }).containsExactlyEntriesOf(mapOf("second" to listOf("shared.plugin", "second.plugin")))

    firstResult.complete(loadResult("shared.plugin", "first.plugin"))
    runCurrent()

    assertThat(repositoryPluginIds(coordinator)).containsExactly("shared.plugin", "first.plugin", "second.plugin")
    assertThat(coordinator.state.value.settledRepositoryPlugins.keys).containsExactly("first", "second")
    assertThat(coordinator.state.value.facetsLoading).isFalse()
    coordinator.close()
  }

  @Test
  fun `status changes reuse repository aggregates`() = runTest {
    val repository = repository("repository")
    val retryResult = CompletableDeferred<CustomPluginRepositoryLoadResult>()
    val provider = FakeRepositoryDataProvider(
      catalogLoader = { catalog(repository) },
      repositoryLoader = { _, call ->
        if (call == 1) loadResult("plugin") else retryResult.await()
      },
    )
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()
    val readyState = coordinator.state.value

    coordinator.retry(repository.id)
    runCurrent()
    val loadingState = coordinator.state.value

    assertThat(loadingState.repositoryContentRevision).isEqualTo(readyState.repositoryContentRevision)
    assertThat(loadingState.listModelData).isSameAs(readyState.listModelData)
    assertThat(loadingState.settledRepositoryPlugins).isSameAs(readyState.settledRepositoryPlugins)

    retryResult.complete(CustomPluginRepositoryLoadResult(emptyList(), "offline"))
    runCurrent()
    val failedState = coordinator.state.value

    assertThat(failedState.repositoryContentRevision).isEqualTo(readyState.repositoryContentRevision)
    assertThat(failedState.listModelData).isSameAs(readyState.listModelData)
    assertThat(failedState.settledRepositoryPlugins).isSameAs(readyState.settledRepositoryPlugins)
    coordinator.close()
  }

  @Test
  fun `repository projection keeps the newest duplicate version`() = runTest {
    val first = repository("first")
    val second = repository("second")
    val older = plugin("shared.plugin", version = "1.0")
    val newer = plugin("shared.plugin", version = "2.0")
    val provider = FakeRepositoryDataProvider(
      catalogLoader = { catalog(first, second) },
      repositoryLoader = { repository, _ ->
        CustomPluginRepositoryLoadResult(listOf(if (repository == first) older else newer))
      },
    )
    val coordinator = coordinator(provider)

    coordinator.start()
    runCurrent()

    assertThat(coordinator.state.value.repositoryPlugins).containsExactly(newer)
    coordinator.close()
  }

  @Test
  fun `empty failed and partial repositories remain distinct visible sections`() = runTest {
    val empty = repository("empty")
    val failed = repository("failed")
    val partial = repository("partial")
    val provider = FakeRepositoryDataProvider(
      catalogLoader = { catalog(empty, failed, partial) },
      repositoryLoader = { repository, _ ->
        when (repository.id) {
          "empty" -> loadResult()
          "failed" -> CustomPluginRepositoryLoadResult(emptyList(), "offline")
          else -> CustomPluginRepositoryLoadResult(listOf(plugin("partial.plugin")), "incomplete")
        }
      },
    )
    val coordinator = coordinator(provider)

    coordinator.start()
    runCurrent()

    val sections = coordinator.state.value.sections
    assertThat(sections.map { it.count }).containsExactly(0, 0, 1)
    assertThat(sections[0].status).isEqualTo(PluginSectionStatus.Ready)
    assertThat(sections[1].status).isInstanceOf(PluginSectionStatus.Failed::class.java)
    assertThat(sections[2].status).isInstanceOf(PluginSectionStatus.Degraded::class.java)
    assertThat(repositoryPluginIds(coordinator)).containsExactly("partial.plugin")
    coordinator.close()
  }

  @Test
  fun `initial catalog error publishes a failed catalog section`() = runTest {
    val provider = FakeRepositoryDataProvider(
      catalogLoader = { UnifiedPluginRepositoryCatalogResult(emptyList(), "offline") },
      repositoryLoader = { _, _ -> loadResult() },
    )
    val coordinator = coordinator(provider)

    coordinator.start()
    runCurrent()

    val section = coordinator.state.value.sections.single()
    assertThat(section.id).isEqualTo(PluginSectionId.CustomRepositoryCatalog)
    assertThat(section.status).isInstanceOf(PluginSectionStatus.Failed::class.java)
    assertThat(coordinator.state.value.repositoryPlugins).isEmpty()
    coordinator.close()
  }

  @Test
  fun `retry replaces a thrown catalog failure with repository sections`() = runTest {
    val repository = repository("repository")
    val provider = FakeRepositoryDataProvider(
      catalogLoader = { call ->
        if (call == 1) throw IllegalStateException("offline")
        catalog(repository)
      },
      repositoryLoader = { _, _ -> loadResult("plugin") },
    )
    val coordinator = coordinator(provider)

    coordinator.start()
    runCurrent()

    assertThat(coordinator.state.value.sections.single().id).isEqualTo(PluginSectionId.CustomRepositoryCatalog)

    coordinator.refresh()
    runCurrent()

    assertThat(sectionIds(coordinator)).containsExactly(repository.id)
    assertThat(coordinator.state.value.sections.single().status).isEqualTo(PluginSectionStatus.Ready)
    coordinator.close()
  }

  @Test
  fun `retry reloads only one repository and refreshes suggestions after it settles`() = runTest {
    val failed = repository("failed")
    val stable = repository("stable")
    val retryResult = CompletableDeferred<CustomPluginRepositoryLoadResult>()
    val provider = FakeRepositoryDataProvider(
      catalogLoader = { catalog(failed, stable) },
      repositoryLoader = { repository, call ->
        when {
          repository == stable -> loadResult("stable.plugin")
          call == 1 -> CustomPluginRepositoryLoadResult(emptyList(), "offline")
          else -> retryResult.await()
        }
      },
    )
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()

    coordinator.retry(failed.id)
    runCurrent()

    assertThat(provider.repositoryLoadCounts).containsEntry(failed.id, 2).containsEntry(stable.id, 1)
    assertThat(coordinator.state.value.sections[0].status).isEqualTo(PluginSectionStatus.Loading(false))
    assertThat(coordinator.state.value.suggestionsRefreshRevision).isZero()

    retryResult.complete(loadResult("retried.plugin"))
    runCurrent()

    assertThat(coordinator.state.value.suggestionsRefreshRevision).isEqualTo(1)
    assertThat(repositoryPluginIds(coordinator)).containsExactly("retried.plugin", "stable.plugin")
    coordinator.close()
  }

  @Test
  fun `catalog refresh removes a repository and rejects its late completion`() = runTest {
    val removed = repository("removed")
    val lateResult = CompletableDeferred<CustomPluginRepositoryLoadResult>()
    val provider = FakeRepositoryDataProvider(
      catalogLoader = { call -> if (call == 1) catalog(removed) else catalog() },
      repositoryLoader = { _, _ ->
        try {
          lateResult.await()
        }
        catch (_: CancellationException) {
          withContext(NonCancellable) { lateResult.await() }
        }
      },
    )
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()

    coordinator.refresh()
    runCurrent()

    assertThat(coordinator.state.value.sections).isEmpty()
    assertThat(coordinator.state.value.repositoryPlugins).isEmpty()
    assertThat(coordinator.state.value.suggestionsRefreshRevision).isEqualTo(1)

    lateResult.complete(loadResult("late.plugin"))
    runCurrent()

    assertThat(coordinator.state.value.sections).isEmpty()
    assertThat(coordinator.state.value.repositoryPlugins).isEmpty()
    coordinator.close()
  }

  @Test
  fun `plugin updates re-enrich cached models without repository refetch`() = runTest {
    val updates = MutableSharedFlow<PluginUpdatesEvent>(extraBufferCapacity = 1)
    val repository = repository("repository")
    val provider = FakeRepositoryDataProvider(
      catalogLoader = { catalog(repository) },
      repositoryLoader = { _, _ -> loadResult("plugin") },
    )
    val enricher = FakeRemoteDataEnricher()
    val coordinator = coordinator(provider, enricher, updates)
    coordinator.start()
    runCurrent()

    assertThat(updates.tryEmit(PluginUpdatesEvent(listOf(plugin("plugin")), emptyList(), emptyList()))).isTrue()
    runCurrent()

    assertThat(provider.repositoryLoadCounts).containsEntry(repository.id, 1)
    assertThat(enricher.enrichCount).isEqualTo(2)
    coordinator.close()
  }

  @Test
  fun `shared fact refresh re-enriches cached models without repository refetch`() = runTest {
    val repository = repository("repository")
    val provider = FakeRepositoryDataProvider(
      catalogLoader = { catalog(repository) },
      repositoryLoader = { _, _ -> loadResult("plugin") },
    )
    val enricher = FakeRemoteDataEnricher()
    val coordinator = coordinator(provider, enricher)
    coordinator.start()
    runCurrent()

    coordinator.refreshEnrichment()
    runCurrent()

    assertThat(provider.repositoryLoadCounts).containsEntry(repository.id, 1)
    assertThat(enricher.enrichCount).isEqualTo(2)
    assertThat(enricher.sharedFactsLoadCount).isEqualTo(2)
    coordinator.close()
  }

  @Test
  fun `shared fact refresh does not cancel a pending repository fetch`() = runTest {
    val repository = repository("repository")
    val repositoryResult = CompletableDeferred<CustomPluginRepositoryLoadResult>()
    val provider = FakeRepositoryDataProvider(
      catalogLoader = { catalog(repository) },
      repositoryLoader = { _, _ -> repositoryResult.await() },
    )
    val enricher = DeferredSharedFactsEnricher()
    val coordinator = coordinator(provider, enricher)
    coordinator.start()
    runCurrent()

    assertThat(enricher.sharedFactsLoadCount).isEqualTo(1)
    coordinator.refreshEnrichment()
    runCurrent()

    repositoryResult.complete(loadResult("plugin"))
    enricher.firstRequest.complete(Unit)
    runCurrent()

    assertThat(coordinator.state.value.sections.single().status).isEqualTo(PluginSectionStatus.Ready)
    assertThat(provider.repositoryLoadCounts).containsEntry(repository.id, 1)
    assertThat(enricher.sharedFactsLoadCount).isEqualTo(2)
    assertThat(enricher.enrichCount).isEqualTo(2)
    coordinator.close()
  }

  @Test
  fun `repositories share one fact snapshot for each enrichment revision`() = runTest {
    val first = repository("first")
    val second = repository("second")
    val provider = FakeRepositoryDataProvider(
      catalogLoader = { catalog(first, second) },
      repositoryLoader = { repository, _ -> loadResult("${repository.id}.plugin") },
    )
    val enricher = FakeRemoteDataEnricher()
    val coordinator = coordinator(provider, enricher)
    coordinator.start()
    runCurrent()

    assertThat(enricher.sharedFactsLoadCount).isEqualTo(1)
    assertThat(enricher.enrichCount).isEqualTo(2)

    coordinator.refreshEnrichment()
    runCurrent()

    assertThat(enricher.sharedFactsLoadCount).isEqualTo(2)
    assertThat(enricher.enrichCount).isEqualTo(4)
    coordinator.close()
  }

  private fun TestScope.coordinator(
    provider: UnifiedPluginRepositoryDataProvider,
    enricher: UnifiedPluginRemoteDataEnricher = FakeRemoteDataEnricher(),
    updates: MutableSharedFlow<PluginUpdatesEvent>? = null,
  ): UnifiedPluginRepositorySourceCoordinator {
    val cache = UnifiedPluginRepositoryCache(backgroundScope, provider)
    return UnifiedPluginRepositorySourceCoordinator(
      scope = backgroundScope,
      repositoryCache = cache,
      dataEnricher = enricher,
      updates = updates ?: emptyFlow(),
      loadErrorMessage = "Unable to load repository plugins",
    )
  }

  private class FakeRepositoryDataProvider(
    private val catalogLoader: suspend (Int) -> UnifiedPluginRepositoryCatalogResult,
    private val repositoryLoader: suspend (CustomPluginRepository, Int) -> CustomPluginRepositoryLoadResult,
  ) : UnifiedPluginRepositoryDataProvider {
    private var catalogLoadCount = 0
    val repositoryLoadCounts = HashMap<String, Int>()

    override suspend fun loadCatalog(): UnifiedPluginRepositoryCatalogResult {
      return catalogLoader(++catalogLoadCount)
    }

    override suspend fun loadRepository(repository: CustomPluginRepository): CustomPluginRepositoryLoadResult {
      val count = repositoryLoadCounts.compute(repository.id) { _, value -> (value ?: 0) + 1 }!!
      return repositoryLoader(repository, count)
    }
  }

  private class FakeRemoteDataEnricher : UnifiedPluginRemoteDataEnricher {
    var enrichCount = 0
    var sharedFactsLoadCount = 0

    override suspend fun loadSharedFacts(): UnifiedPluginRemoteSharedFacts {
      sharedFactsLoadCount++
      return UnifiedPluginRemoteSharedFacts.EMPTY
    }

    override suspend fun enrich(
      models: List<PluginUiModel>,
      updates: PluginUpdatesEvent?,
      contentRevision: Long,
    ): UnifiedPluginMarketplaceSnapshot {
      enrichCount++
      return UnifiedPluginMarketplaceSnapshot(
        items = models.map { model ->
          PluginItemState(
            pluginId = model.pluginId,
            name = model.name,
            contentRevision = contentRevision,
            modelHandle = PluginItemModelHandle(model),
          )
        },
        listModelData = PluginListModelData.EMPTY,
      )
    }
  }

  private class DeferredSharedFactsEnricher : UnifiedPluginRemoteDataEnricher {
    val firstRequest = CompletableDeferred<Unit>()
    var sharedFactsLoadCount = 0
    var enrichCount = 0

    override suspend fun loadSharedFacts(): UnifiedPluginRemoteSharedFacts {
      sharedFactsLoadCount++
      if (sharedFactsLoadCount == 1) firstRequest.await()
      return UnifiedPluginRemoteSharedFacts.EMPTY
    }

    override suspend fun enrich(
      models: List<PluginUiModel>,
      updates: PluginUpdatesEvent?,
      contentRevision: Long,
    ): UnifiedPluginMarketplaceSnapshot {
      enrichCount++
      return UnifiedPluginMarketplaceSnapshot(
        items = models.map { model ->
          PluginItemState(
            pluginId = model.pluginId,
            name = model.name,
            contentRevision = contentRevision,
            modelHandle = PluginItemModelHandle(model),
          )
        },
        listModelData = PluginListModelData.EMPTY,
      )
    }
  }

  private companion object {
    fun repository(id: String): CustomPluginRepository = CustomPluginRepository(id, PluginSource.LOCAL)

    fun catalog(vararg repositories: CustomPluginRepository): UnifiedPluginRepositoryCatalogResult {
      return UnifiedPluginRepositoryCatalogResult(repositories.toList())
    }

    fun loadResult(vararg pluginIds: String): CustomPluginRepositoryLoadResult {
      return CustomPluginRepositoryLoadResult(pluginIds.map(::plugin))
    }

    fun plugin(id: String, version: String? = null): PluginDto {
      return PluginDto(id, PluginId.getId(id)).apply { this.version = version }
    }

    fun sectionIds(coordinator: UnifiedPluginRepositorySourceCoordinator): List<String> {
      return coordinator.state.value.sections.map { (it.id as PluginSectionId.CustomRepository).repositoryId }
    }

    fun repositoryPluginIds(coordinator: UnifiedPluginRepositorySourceCoordinator): List<String>? {
      return coordinator.state.value.repositoryPlugins?.map { it.pluginId.idString }
    }
  }
}
