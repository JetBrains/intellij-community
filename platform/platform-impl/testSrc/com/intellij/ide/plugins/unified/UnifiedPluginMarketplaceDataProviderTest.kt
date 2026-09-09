// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.HtmlChunk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.ArrayList

@OptIn(ExperimentalCoroutinesApi::class)
internal class UnifiedPluginMarketplaceDataProviderTest {
  @Test
  fun `Marketplace maps category filters to tags without changing text search`() {
    val request = buildUnifiedMarketplaceSearchRequest(
      "Kotlin /tag:\"Developer Tools\" /category:\"Programming Language\" /category:Tools /sortBy:rating"
    )

    assertThat(request.parser.searchQuery).isEqualTo("Kotlin")
    assertThat(request.urlQuery).contains("orderBy=rating")
    assertThat(request.urlQuery).contains("search=Kotlin")
    assertThat(request.urlQuery).contains("tags=Developer%20Tools")
    assertThat(request.urlQuery).contains("tags=Programming%20Language")
    assertThat(request.urlQuery).contains("tags=Tools")
    assertThat(request.urlQuery).doesNotContain("category")
  }

  @Test
  fun `Marketplace does not duplicate equal category and tag filters`() {
    val request = buildUnifiedMarketplaceSearchRequest("/tag:Tools /category:Tools")

    assertThat(request.urlQuery.split("&")).containsExactly("tags=Tools")
  }

  @Test
  fun `Marketplace tags keep all values in count order`() {
    val counts = (1..25).associateBy({ index -> "Tag $index" }, { index -> index })

    val tags = sortMarketplaceTags(counts)

    assertThat(tags).containsExactlyElementsOf((25 downTo 1).map { index -> "Tag $index" })
  }

  @Test
  fun `Marketplace tags use name order for equal counts`() {
    val tags = sortMarketplaceTags(mapOf("beta" to 10, "Alpha" to 10, "alpha" to 10, "ignored" to 1, " " to 100))

    assertThat(tags).containsExactly("Alpha", "alpha", "beta", "ignored")
  }

  @Test
  fun `merged suggestions load Staff Picks when project suggestions are empty`() = runTest {
    var projectSuggestionsLoaded = false
    var staffPicksLoaded = false
    val staffPick = plugin("staff.pick", "Staff Pick")

    val results = loadMergedSuggestedPlugins(
      loadProjectSuggestions = {
        projectSuggestionsLoaded = true
        UnifiedPluginMarketplaceFetchResult(emptyList())
      },
      loadStaffPicks = {
        staffPicksLoaded = true
        UnifiedPluginMarketplaceFetchResult(listOf(staffPick))
      },
    ).toList()

    assertThat(projectSuggestionsLoaded).isTrue()
    assertThat(staffPicksLoaded).isTrue()
    assertThat(results.last().models).containsExactly(staffPick)
    assertThat(results.last().error).isNull()
  }

  @Test
  fun `merged suggestions keep project priority and partial errors`() = runTest {
    val projectDuplicate = plugin("duplicate.plugin", "Project suggestion")
    val projectOnly = plugin("project.plugin", "Project only")
    val staffDuplicate = plugin("duplicate.plugin", "Staff Pick")
    val staffOnly = plugin("staff.plugin", "Staff only")

    val result = loadMergedSuggestedPlugins(
      loadProjectSuggestions = {
        UnifiedPluginMarketplaceFetchResult(listOf(projectDuplicate, projectOnly), "project source failed partially")
      },
      loadStaffPicks = {
        UnifiedPluginMarketplaceFetchResult(listOf(staffDuplicate, staffOnly), "staff source failed partially")
      },
    ).toList().last()

    assertThat(result.models).containsExactly(projectDuplicate, projectOnly, staffOnly)
    assertThat(result.error).isEqualTo("project source failed partially; staff source failed partially")
  }

  @Test
  fun `merged suggestions omit only custom repository errors`() {
    val repositoryFailure = UnifiedPluginMarketplaceFetchResult(
      emptyList(),
      "custom repository unavailable",
      MarketplaceFetchErrorOrigin.CustomRepository,
    )
    val staffPick = plugin("staff.plugin", "Staff Pick")

    val customFailureOnly = combineSuggestedPluginResults(
      repositoryFailure,
      UnifiedPluginMarketplaceFetchResult(listOf(staffPick)),
    )
    assertThat(customFailureOnly.models).containsExactly(staffPick)
    assertThat(customFailureOnly.error).isNull()

    val staffFailure = combineSuggestedPluginResults(
      repositoryFailure,
      UnifiedPluginMarketplaceFetchResult(listOf(staffPick), "Staff Picks unavailable"),
    )
    assertThat(staffFailure.error).isEqualTo("Staff Picks unavailable")
  }

  @Test
  fun `merged suggestions isolate a failed source`() = runTest {
    val staffPick = plugin("staff.plugin", "Staff Pick")
    val failures = ArrayList<Pair<String, Throwable>>()

    val result = loadMergedSuggestedPlugins(
      loadProjectSuggestions = { error("project source unavailable") },
      loadStaffPicks = { UnifiedPluginMarketplaceFetchResult(listOf(staffPick)) },
      onFailure = { sourceName, cause -> failures.add(sourceName to cause) },
    ).toList().last()

    assertThat(result.models).containsExactly(staffPick)
    assertThat(result.error).isEqualTo("project source unavailable")
    assertThat(failures.map(Pair<String, Throwable>::first)).containsExactly("project suggestions")
  }

  @Test
  fun `Staff Picks publish while project suggestions are still loading`() = runTest {
    val projectSuggestions = CompletableDeferred<UnifiedPluginMarketplaceFetchResult>()
    val staffPick = plugin("staff.plugin", "Staff Pick")
    val projectSuggestion = plugin("project.plugin", "Project Suggestion")
    val results = ArrayList<UnifiedPluginMarketplaceFetchResult>()

    backgroundScope.launch {
      loadMergedSuggestedPlugins(
        loadProjectSuggestions = { projectSuggestions.await() },
        loadStaffPicks = { UnifiedPluginMarketplaceFetchResult(listOf(staffPick)) },
      ).toList(results)
    }
    runCurrent()

    assertThat(results).hasSize(1)
    assertThat(results.single().models).containsExactly(staffPick)

    projectSuggestions.complete(UnifiedPluginMarketplaceFetchResult(listOf(projectSuggestion)))
    runCurrent()

    assertThat(results.last().models).containsExactly(projectSuggestion, staffPick)
  }

  @Test
  fun `normalization keeps one stable occurrence per plugin id`() {
    val first = plugin("duplicate.plugin", "First")
    val second = plugin("duplicate.plugin", "Second")
    val distinct = plugin("distinct.plugin", "Distinct")

    val normalized = normalizeMarketplaceModels(listOf(first, second, distinct))

    assertThat(normalized).containsExactly(second, distinct)
  }

  @Test
  fun `snapshot enriches remote rows with installed and update facts`() {
    val remote = plugin("remote.plugin", "Remote").apply {
      displayCategory = "Programming Language"
      vendor = "JetBrains"
      tags = listOf("Developer Tools")
    }
    val duplicate = plugin("remote.plugin", "Duplicate")
    val installed = plugin("remote.plugin", "Installed")
    val update = plugin("remote.plugin", "Update")
    val error = HtmlChunk.text("error")
    val state = PluginInstallationState(true)

    val snapshot = buildMarketplaceSnapshot(
      models = listOf(duplicate, remote),
      updates = PluginUpdatesEvent(listOf(update), emptyList(), emptyList()),
      contentRevision = 7,
      installedModels = mapOf(remote.pluginId to installed),
      enabledStates = mapOf(remote.pluginId to true),
      errors = mapOf(remote.pluginId to listOf(error)),
      installationStates = mapOf(remote.pluginId to state),
      restrictions = mapOf(remote.pluginId to true),
    )

    val item = snapshot.items.single()
    assertThat(item.modelHandle?.model).isSameAs(remote)
    assertThat(item.contentRevision).isEqualTo(7)
    assertThat(item.searchCategory).isEqualTo("Programming Language")
    assertThat(item.searchVendor).isEqualTo("JetBrains")
    assertThat(item.searchTags).containsExactly("Developer Tools")
    val input = requireNotNull(item.rowInput)
    assertThat(input.installedPlugin).isSameAs(installed)
    assertThat(input.installationState).isSameAs(state)
    assertThat(input.errors).containsExactly(error)
    assertThat(input.updateDescriptor).isSameAs(update)
    assertThat(input.enabled).isTrue()
    assertThat(input.restrictedByProduct).isTrue()
    assertThat(snapshot.listModelData.installedModels).containsEntry(remote.pluginId, installed)
    assertThat(snapshot.listModelData.installationStates).containsEntry(remote.pluginId, state)
  }

  @Test
  fun `snapshot supplies absent installation defaults for available plugins`() {
    val remote = plugin("available.plugin", "Available")

    val snapshot = buildMarketplaceSnapshot(
      models = listOf(remote),
      updates = null,
      contentRevision = 1,
      installedModels = emptyMap(),
      enabledStates = emptyMap(),
      errors = emptyMap(),
      installationStates = emptyMap(),
      restrictions = emptyMap(),
    )

    val input = snapshot.items.single().rowInput
    assertThat(input?.installedPlugin).isNull()
    assertThat(input?.installationState?.fullyInstalled).isFalse()
    assertThat(input?.enabled).isFalse()
  }

  private fun plugin(id: String, name: String): PluginDto = PluginDto(name, PluginId.getId(id))
}
