// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.ArrayList
import java.util.HashMap
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class UnifiedPluginMarketplaceSourceCoordinatorTest {
  @Test
  fun `empty query loads Suggested immediately`() = runTest {
    val provider = FakeMarketplaceDataProvider(suggested = fetch("suggested.plugin"))
    val coordinator = coordinator(provider)

    coordinator.start()
    runCurrent()

    assertThat(coordinator.state.value.section.id).isEqualTo(PluginSectionId.Suggested)
    assertThat(coordinator.state.value.section.items.map { it.pluginId.idString }).containsExactly("suggested.plugin")
    assertThat(coordinator.state.value.section.status).isEqualTo(PluginSectionStatus.Ready)
    assertThat(provider.suggestedCount).isEqualTo(1)
    coordinator.close()
  }

  @Test
  fun `non-empty query switches family before debounced execution`() = runTest {
    val provider = FakeMarketplaceDataProvider(suggested = fetch())
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()

    coordinator.setQuery(PluginsQueryState(" kotlin ", "kotlin", 1))
    runCurrent()

    assertThat(coordinator.state.value.section.id).isEqualTo(PluginSectionId.Marketplace)
    assertThat(coordinator.state.value.section.status).isEqualTo(PluginSectionStatus.Loading(false))
    assertThat(provider.searchQueries).isEmpty()

    advanceTimeBy(299.milliseconds)
    runCurrent()
    assertThat(provider.searchQueries).isEmpty()
    advanceTimeBy(1.milliseconds)
    runCurrent()
    assertThat(provider.searchQueries).containsExactly("kotlin")
    coordinator.close()
  }

  @Test
  fun `installed-only query leaves Marketplace family inactive`() = runTest {
    val provider = FakeMarketplaceDataProvider(suggested = fetch("suggested.plugin"))
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()

    coordinator.setQuery(PluginsQueryState("alpha", "alpha", 1, PluginsQueryScope.Installed))
    runCurrent()

    assertThat(coordinator.state.value.section.id).isEqualTo(PluginSectionId.Marketplace)
    assertThat(coordinator.state.value.section.status).isEqualTo(PluginSectionStatus.Ready)
    assertThat(coordinator.state.value.section.items).isEmpty()
    assertThat(provider.suggestedCount).isEqualTo(1)
    assertThat(provider.searchQueries).isEmpty()
    assertThat(provider.enrichCount).isEqualTo(1)
    coordinator.close()
  }

  @Test
  fun `late non-cooperative A-B-A result cannot publish`() = runTest {
    val firstA = CompletableDeferred<UnifiedPluginMarketplaceFetchResult>()
    val secondA = CompletableDeferred<UnifiedPluginMarketplaceFetchResult>()
    val provider = FakeMarketplaceDataProvider(suggested = fetch()) { query, call ->
      when (query) {
        "a" -> {
          if (call == 1) {
            try {
              firstA.await()
            }
            catch (_: CancellationException) {
              withContext(NonCancellable) { firstA.await() }
            }
          }
          else {
            secondA.await()
          }
        }
        else -> awaitCancellation()
      }
    }
    val coordinator = coordinator(provider, debounceMillis = 0)
    coordinator.start()
    runCurrent()

    coordinator.setQuery(PluginsQueryState("a", "a", 1))
    runCurrent()
    coordinator.setQuery(PluginsQueryState("b", "b", 2))
    runCurrent()
    coordinator.setQuery(PluginsQueryState("a", "a", 3))
    runCurrent()

    secondA.complete(fetch("fresh.plugin"))
    runCurrent()
    firstA.complete(fetch("stale.plugin"))
    runCurrent()

    val state = coordinator.state.value
    assertThat(state.queryRevision).isEqualTo(3)
    assertThat(state.section.items.map { it.pluginId.idString }).containsExactly("fresh.plugin")
    coordinator.close()
  }

  @Test
  fun `partial and failed results have distinct retryable states`() = runTest {
    val provider = FakeMarketplaceDataProvider(suggested = fetch()) { query, _ ->
      when (query) {
        "partial" -> fetch("partial.plugin", error = "one side failed")
        else -> fetch(error = "offline")
      }
    }
    val coordinator = coordinator(provider, debounceMillis = 0)
    coordinator.start()
    runCurrent()

    coordinator.setQuery(PluginsQueryState("partial", "partial", 1))
    runCurrent()
    assertThat(coordinator.state.value.section.status).isInstanceOf(PluginSectionStatus.Degraded::class.java)
    assertThat(coordinator.state.value.section.items).hasSize(1)

    coordinator.setQuery(PluginsQueryState("failed", "failed", 2))
    runCurrent()
    assertThat(coordinator.state.value.section.status).isInstanceOf(PluginSectionStatus.Failed::class.java)
    assertThat(coordinator.state.value.section.items).isEmpty()
    coordinator.close()
  }

  @Test
  fun `retry uses a new request token without changing query revision`() = runTest {
    val retryResult = CompletableDeferred<UnifiedPluginMarketplaceFetchResult>()
    var requestCount = 0
    val provider = FakeMarketplaceDataProvider(
      loadSuggested = {
        flow {
          requestCount++
          emit(if (requestCount == 1) fetch(error = "offline") else retryResult.await())
        }
      }
    )
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()

    assertThat(coordinator.state.value.section.status).isInstanceOf(PluginSectionStatus.Failed::class.java)
    coordinator.retry()
    runCurrent()
    assertThat(coordinator.state.value.queryRevision).isZero()
    assertThat(coordinator.state.value.section.status).isEqualTo(PluginSectionStatus.Loading(false))

    retryResult.complete(fetch("retry.plugin"))
    runCurrent()
    assertThat(coordinator.state.value.section.items.map { it.pluginId.idString }).containsExactly("retry.plugin")
    coordinator.close()
  }

  @Test
  fun `Suggested publishes partial content while another input is loading`() = runTest {
    val mergedResult = CompletableDeferred<UnifiedPluginMarketplaceFetchResult>()
    val provider = FakeMarketplaceDataProvider(
      loadSuggested = {
        flow {
          emit(fetch("staff.pick"))
          emit(mergedResult.await())
        }
      }
    )
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()

    assertThat(coordinator.state.value.section.items.map { it.pluginId.idString }).containsExactly("staff.pick")
    assertThat(coordinator.state.value.section.status).isEqualTo(PluginSectionStatus.Loading(showingStaleContent = true))

    mergedResult.complete(fetch("project.suggestion", "staff.pick"))
    runCurrent()

    assertThat(coordinator.state.value.section.items.map { it.pluginId.idString })
      .containsExactly("project.suggestion", "staff.pick")
    assertThat(coordinator.state.value.section.status).isEqualTo(PluginSectionStatus.Ready)
    coordinator.close()
  }

  @Test
  fun `Suggested facet items survive switching to Marketplace search`() = runTest {
    val suggested = plugin("suggested.plugin").apply {
      vendor = "Suggested Vendor"
      tags = listOf("Suggested Tag")
    }
    val provider = FakeMarketplaceDataProvider(
      suggested = UnifiedPluginMarketplaceFetchResult(listOf(suggested)),
    )
    val coordinator = coordinator(provider, debounceMillis = 0)
    coordinator.start()
    runCurrent()

    assertThat(coordinator.state.value.suggestedFacetItems.single().pluginId).isEqualTo(suggested.pluginId)
    assertThat(coordinator.state.value.suggestedFacetsLoading).isFalse()

    coordinator.setQuery(PluginsQueryState("search", "search", 1))
    runCurrent()

    assertThat(coordinator.state.value.section.id).isEqualTo(PluginSectionId.Marketplace)
    assertThat(coordinator.state.value.suggestedFacetItems.single().pluginId).isEqualTo(suggested.pluginId)
    assertThat(coordinator.state.value.suggestedFacetsLoading).isFalse()
    coordinator.close()
  }

  @Test
  fun `popular tags load independently from Marketplace content`() = runTest {
    val popularTags = CompletableDeferred<List<String>>()
    val provider = FakeMarketplaceDataProvider(
      suggested = fetch("suggested.plugin"),
      loadPopularTags = { popularTags.await() },
    )
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()

    assertThat(coordinator.state.value.section.status).isEqualTo(PluginSectionStatus.Ready)
    assertThat(coordinator.state.value.popularTagsLoading).isTrue()

    popularTags.complete(listOf("Code Tools", "Productivity"))
    runCurrent()

    assertThat(coordinator.state.value.popularTags).containsExactly("Code Tools", "Productivity")
    assertThat(coordinator.state.value.popularTagsLoading).isFalse()
    assertThat(provider.popularTagsLoadCount).isEqualTo(1)
    coordinator.close()
  }

  @Test
  fun `popular tag failure keeps Marketplace content ready`() = runTest {
    val provider = FakeMarketplaceDataProvider(
      suggested = fetch("suggested.plugin"),
      loadPopularTags = { error("offline") },
    )
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()

    assertThat(coordinator.state.value.section.status).isEqualTo(PluginSectionStatus.Ready)
    assertThat(coordinator.state.value.popularTags).isEmpty()
    assertThat(coordinator.state.value.popularTagsLoading).isFalse()
    coordinator.close()
  }

  @Test
  fun `updates re-enrich fetched models without another request`() = runTest {
    val updates = MutableSharedFlow<PluginUpdatesEvent>(extraBufferCapacity = 1)
    val provider = FakeMarketplaceDataProvider(suggested = fetch("suggested.plugin"))
    val coordinator = coordinator(provider, updates = updates)
    coordinator.start()
    runCurrent()

    assertThat(updates.tryEmit(PluginUpdatesEvent(listOf(plugin("suggested.plugin")), emptyList(), emptyList()))).isTrue()
    runCurrent()

    assertThat(provider.suggestedCount).isEqualTo(1)
    assertThat(provider.enrichCount).isEqualTo(2)
    coordinator.close()
  }

  @Test
  fun `shared fact refresh re-enriches without another request`() = runTest {
    val provider = FakeMarketplaceDataProvider(suggested = fetch("suggested.plugin"))
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()

    coordinator.refreshEnrichment()
    runCurrent()

    assertThat(provider.suggestedCount).isEqualTo(1)
    assertThat(provider.enrichCount).isEqualTo(2)
    coordinator.close()
  }

  @Test
  fun `shared fact refresh during fetch is not lost`() = runTest {
    val firstEnrichmentStarted = CompletableDeferred<Unit>()
    val releaseFirstEnrichment = CompletableDeferred<Unit>()
    val provider = FakeMarketplaceDataProvider(
      suggested = fetch("suggested.plugin"),
      beforeEnrich = { call ->
        if (call == 1) {
          firstEnrichmentStarted.complete(Unit)
          releaseFirstEnrichment.await()
        }
      },
    )
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()
    assertThat(firstEnrichmentStarted.isCompleted).isTrue()

    coordinator.refreshEnrichment()
    runCurrent()
    releaseFirstEnrichment.complete(Unit)
    runCurrent()

    assertThat(provider.suggestedCount).isEqualTo(1)
    assertThat(provider.enrichCount).isEqualTo(2)
    assertThat(coordinator.state.value.section.status).isEqualTo(PluginSectionStatus.Ready)
    coordinator.close()
  }

  @Test
  fun `disposal prevents late publication`() = runTest {
    val result = CompletableDeferred<UnifiedPluginMarketplaceFetchResult>()
    val provider = FakeMarketplaceDataProvider(loadSuggested = { flow { emit(result.await()) } })
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()
    val beforeClose = coordinator.state.value

    coordinator.close()
    result.complete(fetch("late.plugin"))
    runCurrent()

    assertThat(coordinator.state.value).isEqualTo(beforeClose)
  }

  private fun TestScope.coordinator(
    provider: UnifiedPluginMarketplaceDataProvider,
    updates: Flow<PluginUpdatesEvent> = emptyFlow(),
    debounceMillis: Long = 300,
  ): UnifiedPluginMarketplaceSourceCoordinator {
    return UnifiedPluginMarketplaceSourceCoordinator(
      scope = backgroundScope,
      initialQuery = PluginsQueryState(),
      dataProvider = provider,
      updates = updates,
      loadErrorMessage = "Unable to load Marketplace plugins",
      searchDebounce = debounceMillis.milliseconds,
    )
  }

  private class FakeMarketplaceDataProvider(
    private val suggested: UnifiedPluginMarketplaceFetchResult = fetch(),
    private val loadSuggested: (() -> Flow<UnifiedPluginMarketplaceFetchResult>)? = null,
    private val search: (suspend (String, Int) -> UnifiedPluginMarketplaceFetchResult)? = null,
    private val beforeEnrich: suspend (Int) -> Unit = {},
    private val loadPopularTags: suspend () -> List<String> = { emptyList() },
  ) : UnifiedPluginMarketplaceDataProvider {
    constructor(
      suggested: UnifiedPluginMarketplaceFetchResult,
      search: suspend (String, Int) -> UnifiedPluginMarketplaceFetchResult,
    ) : this(suggested, null, search)

    var suggestedCount = 0
    var enrichCount = 0
    var popularTagsLoadCount = 0
    val searchQueries = ArrayList<String>()
    private val queryCounts = HashMap<String, Int>()

    override fun loadSuggested(): Flow<UnifiedPluginMarketplaceFetchResult> {
      suggestedCount++
      return loadSuggested?.invoke() ?: flowOf(suggested)
    }

    override suspend fun searchMarketplace(query: String): UnifiedPluginMarketplaceFetchResult {
      searchQueries.add(query)
      val count = queryCounts.compute(query) { _, value -> (value ?: 0) + 1 }!!
      return search?.invoke(query, count) ?: fetch("$query.plugin")
    }

    override suspend fun loadPopularTags(): List<String> {
      popularTagsLoadCount++
      return loadPopularTags.invoke()
    }

    override suspend fun enrich(
      models: List<PluginUiModel>,
      updates: PluginUpdatesEvent?,
      contentRevision: Long,
    ): UnifiedPluginMarketplaceSnapshot {
      enrichCount++
      beforeEnrich(enrichCount)
      return UnifiedPluginMarketplaceSnapshot(
        items = normalizeMarketplaceModels(models).map { model ->
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
    fun fetch(vararg pluginIds: String, error: String? = null): UnifiedPluginMarketplaceFetchResult {
      return UnifiedPluginMarketplaceFetchResult(pluginIds.map(::plugin), error)
    }

    fun plugin(id: String): PluginDto = PluginDto(id, PluginId.getId(id))
  }
}
