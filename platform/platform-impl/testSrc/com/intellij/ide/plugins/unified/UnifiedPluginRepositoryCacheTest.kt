// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.CustomPluginRepository
import com.intellij.ide.plugins.newui.CustomPluginRepositoryLoadResult
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.openapi.extensions.PluginId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
internal class UnifiedPluginRepositoryCacheTest {
  @Test
  fun `concurrent consumers share catalog and repository requests for one page`() = runTest {
    val repository = repository("repository")
    val result = CompletableDeferred<CustomPluginRepositoryLoadResult>()
    val provider = FakeRepositoryDataProvider(listOf(repository)) { _ -> result.await() }
    val cache = UnifiedPluginRepositoryCache(backgroundScope, provider)

    val first = async { cache.loadAllForSuggestions() }
    val second = async { cache.loadRepository(repository) }
    runCurrent()

    assertThat(provider.catalogLoadCount).isEqualTo(1)
    assertThat(provider.repositoryLoadCount).isEqualTo(1)

    result.complete(loadResult("plugin"))
    assertThat(first.await().pluginsByRepository.getValue(repository.id)).hasSize(1)
    assertThat(second.await().plugins).hasSize(1)

    cache.loadRepository(repository)
    assertThat(provider.repositoryLoadCount).isEqualTo(1)
  }

  @Test
  fun `a new page cache obtains a fresh response`() = runTest {
    val repository = repository("repository")
    val provider = FakeRepositoryDataProvider(listOf(repository)) { _ -> loadResult("plugin") }

    UnifiedPluginRepositoryCache(backgroundScope, provider).loadRepository(repository)
    UnifiedPluginRepositoryCache(backgroundScope, provider).loadRepository(repository)

    assertThat(provider.repositoryLoadCount).isEqualTo(2)
  }

  @Test
  fun `failed refresh does not evict the last page-cached success`() = runTest {
    val repository = repository("repository")
    var fail = false
    val provider = FakeRepositoryDataProvider(listOf(repository)) { _ ->
      if (fail) CustomPluginRepositoryLoadResult(emptyList(), "offline") else loadResult("cached.plugin")
    }
    val cache = UnifiedPluginRepositoryCache(backgroundScope, provider)

    val cached = cache.loadRepository(repository)
    fail = true
    val failedRefresh = cache.loadRepository(repository, refresh = true)
    val afterFailure = cache.loadRepository(repository)

    assertThat(cached.plugins).hasSize(1)
    assertThat(failedRefresh.error).isEqualTo("offline")
    assertThat(afterFailure.plugins.map { it.pluginId.idString }).containsExactly("cached.plugin")
    assertThat(provider.repositoryLoadCount).isEqualTo(2)
  }

  @Test
  fun `suggestion projection preserves catalog order and partial errors`() = runTest {
    val first = repository("first")
    val second = repository("second")
    val provider = FakeRepositoryDataProvider(listOf(first, second)) { repository ->
      if (repository == first) loadResult("first.plugin")
      else CustomPluginRepositoryLoadResult(listOf(plugin("second.plugin")), "partial")
    }
    val cache = UnifiedPluginRepositoryCache(backgroundScope, provider)

    val aggregate = cache.loadAllForSuggestions()

    assertThat(aggregate.pluginsByRepository.keys).containsExactly("first", "second")
    assertThat(aggregate.error).isEqualTo("partial")
  }

  private class FakeRepositoryDataProvider(
    private val repositories: List<CustomPluginRepository>,
    private val load: suspend (CustomPluginRepository) -> CustomPluginRepositoryLoadResult,
  ) : UnifiedPluginRepositoryDataProvider {
    var catalogLoadCount = 0
    var repositoryLoadCount = 0

    override suspend fun loadCatalog(): UnifiedPluginRepositoryCatalogResult {
      catalogLoadCount++
      return UnifiedPluginRepositoryCatalogResult(repositories)
    }

    override suspend fun loadRepository(repository: CustomPluginRepository): CustomPluginRepositoryLoadResult {
      repositoryLoadCount++
      return load(repository)
    }
  }

  private companion object {
    fun repository(id: String): CustomPluginRepository = CustomPluginRepository(id, PluginSource.LOCAL)

    fun loadResult(vararg pluginIds: String): CustomPluginRepositoryLoadResult {
      return CustomPluginRepositoryLoadResult(pluginIds.map(::plugin))
    }

    fun plugin(id: String): PluginUiModel = PluginDto(id, PluginId.getId(id))
  }
}
