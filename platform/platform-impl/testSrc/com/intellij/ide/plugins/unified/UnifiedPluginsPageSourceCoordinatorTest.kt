// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.CustomPluginRepository
import com.intellij.ide.plugins.newui.CustomPluginRepositoryLoadResult
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginModelEvent
import com.intellij.ide.plugins.newui.PluginPreparedUpdateState
import com.intellij.ide.plugins.newui.PluginProgressState
import com.intellij.ide.plugins.newui.PluginRowInput
import com.intellij.ide.plugins.newui.PluginSource
import com.intellij.ide.plugins.newui.PluginUiModel
import com.intellij.ide.plugins.newui.PluginUpdatesEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.HtmlChunk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
internal class UnifiedPluginsPageSourceCoordinatorTest {
  @Test
  fun `projection checks cancellation while scanning items`() {
    val items = (1..1_000).map { index -> item(plugin("plugin.$index", "Plugin $index")) }
    var cancellationChecks = 0

    assertThatThrownBy {
      composeUnifiedPluginsPageSourceState(
        query = PluginsQueryState("missing", "missing", 1),
        localState = localState(
          listOf(
            PluginSectionState(PluginSectionId.Installed, items = items),
            PluginSectionState(PluginSectionId.Bundled),
          )
        ),
        cancellationCheck = {
          cancellationChecks++
          if (cancellationChecks == 20) throw CancellationException("superseded")
        },
      )
    }.isInstanceOf(CancellationException::class.java)
    assertThat(cancellationChecks).isEqualTo(20)
  }

  @Test
  fun `page owns normalized query revisions and filters cached local sections`() = runTest {
    val provider = FakeLocalDataProvider(
      UnifiedPluginInventory(
        installedPlugins = listOf(inventoryItem("alpha.plugin", "Alpha"), inventoryItem("beta.plugin", "Beta")),
        bundledPlugins = emptyList(),
      )
    )
    val coordinator = coordinator(provider, "  alpha  ")
    coordinator.start()
    runCurrent()

    assertThat(coordinator.state.value.query).isEqualTo(PluginsQueryState("  alpha  ", "alpha", 0))
    assertThat(installedIds(coordinator.state.value)).containsExactly("alpha.plugin")

    coordinator.setQuery(" alpha ")
    assertThat(coordinator.state.value.query).isEqualTo(PluginsQueryState(" alpha ", "alpha", 0))

    coordinator.setQuery(" beta ")
    assertThat(coordinator.state.value.query).isEqualTo(PluginsQueryState(" beta ", "beta", 1))
    assertThat(coordinator.state.value.sections.map { it.id }).contains(PluginSectionId.Marketplace)
      .doesNotContain(PluginSectionId.Suggested)
    assertThat(installedIds(coordinator.state.value)).containsExactly("alpha.plugin")

    coordinator.setQuery("Alpha")
    coordinator.setQuery("Beta")
    runCurrent()

    assertThat(coordinator.state.value.query).isEqualTo(PluginsQueryState("Beta", "Beta", 3))
    assertThat(installedIds(coordinator.state.value)).containsExactly("beta.plugin")
    assertThat(provider.loadCount).isEqualTo(1)
    coordinator.close()
  }

  @Test
  fun `search includes plugins beyond the section display limit`() {
    val items = (0 until PluginSectionState.MAX_DISPLAYED_ITEM_COUNT).map { index ->
      item(plugin("plugin.$index", "Plugin $index"))
    } + item(plugin("target.plugin", "Search Target"))
    val local = localState(
      listOf(
        PluginSectionState(PluginSectionId.Installed, items = items, expanded = true),
        PluginSectionState(PluginSectionId.Bundled),
      )
    )

    val unfilteredSection = composeUnifiedPluginsPageSourceState(PluginsQueryState(), local)
      .sections.single { it.id == PluginSectionId.Installed }
    assertThat(unfilteredSection.items).hasSize(PluginSectionState.MAX_DISPLAYED_ITEM_COUNT + 1)
    assertThat(unfilteredSection.visibleItems).hasSize(PluginSectionState.MAX_DISPLAYED_ITEM_COUNT)

    val filteredSection = composeUnifiedPluginsPageSourceState(
      PluginsQueryState("Search Target", "Search Target"),
      local,
    ).sections.single { it.id == PluginSectionId.Installed }
    assertThat(filteredSection.items.map { it.pluginId.idString }).containsExactly("target.plugin")
    assertThat(filteredSection.visibleItems.map { it.pluginId.idString }).containsExactly("target.plugin")
  }

  @Test
  fun `downloading manual update shows progress in every occurrence`() {
    val plugin = plugin("updated.plugin", "Updated Plugin")
    val update = plugin("updated.plugin", "Updated Plugin Update")
    val item = localItem(plugin, enabled = true, update = update)
    val sections = listOf(
      PluginSectionState(PluginSectionId.Installing, items = listOf(item)),
      PluginSectionState(PluginSectionId.Installed, items = listOf(item)),
      PluginSectionState(PluginSectionId.Internal, items = listOf(item)),
      PluginSectionState(PluginSectionId.Marketplace, items = listOf(item)),
      PluginSectionState(PluginSectionId.CustomRepository("custom"), items = listOf(item)),
    )

    val projected = applyManualUpdatePresentations(
      sections,
      mapOf(plugin.pluginId to UnifiedPluginManualUpdateState(
        UUID.randomUUID(),
        UnifiedPluginManualUpdatePresentation.Downloading,
      )),
    )

    assertThat(projected.flatMap(PluginSectionState::items)).allSatisfy { occurrence ->
      assertThat(occurrence.rowInput?.operationInProgress).isTrue()
      assertThat(occurrence.rowInput?.detailsProgress).isEqualTo(PluginProgressState.Indeterminate)
      assertThat(occurrence.rowInput?.preparedUpdate).isNull()
    }
  }

  @Test
  fun `prepared manual update overrides a cached update in every occurrence`() {
    val plugin = plugin("updated.plugin", "Updated Plugin")
    val cachedUpdate = plugin("updated.plugin", "Cached Update")
    val activeInput = localItem(plugin, enabled = true, update = cachedUpdate).rowInput!!.copy(
      operationInProgress = true,
      detailsProgress = PluginProgressState.Indeterminate,
    )
    val item = localItem(plugin, enabled = true, update = cachedUpdate).copy(rowInput = activeInput)
    val sections = listOf(
      PluginSectionState(PluginSectionId.Installing, items = listOf(item)),
      PluginSectionState(PluginSectionId.Installed, items = listOf(item)),
      PluginSectionState(PluginSectionId.Internal, items = listOf(item)),
      PluginSectionState(PluginSectionId.Marketplace, items = listOf(item)),
      PluginSectionState(PluginSectionId.CustomRepository("custom"), items = listOf(item)),
    )

    val projected = applyManualUpdatePresentations(
      sections,
      mapOf(plugin.pluginId to UnifiedPluginManualUpdateState(
        UUID.randomUUID(),
        UnifiedPluginManualUpdatePresentation.Prepared(restartRequired = true),
      )),
    )

    assertThat(projected.flatMap(PluginSectionState::items)).allSatisfy { occurrence ->
      assertThat(occurrence.rowInput?.operationInProgress).isFalse()
      assertThat(occurrence.rowInput?.detailsProgress).isNull()
      assertThat(occurrence.rowInput?.preparedUpdate).isEqualTo(PluginPreparedUpdateState(restartRequired = true))
      assertThat(occurrence.rowInput?.updateDescriptor).isSameAs(cachedUpdate)
    }
  }

  @Test
  fun `query scope changes source intent without changing visible query`() = runTest {
    val provider = FakeLocalDataProvider(
      UnifiedPluginInventory(
        installedPlugins = listOf(inventoryItem("alpha.plugin", "Alpha"), inventoryItem("beta.plugin", "Beta")),
        bundledPlugins = emptyList(),
      )
    )
    val marketplaceProvider = RecordingMarketplaceDataProvider()
    val coordinator = coordinator(provider, marketplaceDataProvider = marketplaceProvider)
    coordinator.start()
    runCurrent()

    coordinator.setQuery("Alpha", PluginsQueryScope.Installed)
    runCurrent()

    assertThat(coordinator.state.value.query.scope).isEqualTo(PluginsQueryScope.Installed)
    assertThat(installedIds(coordinator.state.value)).containsExactly("alpha.plugin")
    assertThat(coordinator.state.value.sections.map { it.id }).contains(PluginSectionId.Marketplace)
      .doesNotContain(PluginSectionId.Suggested)
    assertThat(coordinator.state.value.sections.single { it.id == PluginSectionId.Marketplace }.status)
      .isEqualTo(PluginSectionStatus.Ready)
    assertThat(marketplaceProvider.searches).isEmpty()
    assertThat(marketplaceProvider.suggestedCount).isEqualTo(1)

    coordinator.setQuery("Alpha", PluginsQueryScope.Marketplace)
    advanceTimeBy(500.milliseconds)
    runCurrent()

    assertThat(coordinator.state.value.query.scope).isEqualTo(PluginsQueryScope.Marketplace)
    assertThat(installedIds(coordinator.state.value)).containsExactly("alpha.plugin", "beta.plugin")
    assertThat(coordinator.state.value.sections.map { it.id }).contains(PluginSectionId.Marketplace)
    assertThat(marketplaceProvider.searches).containsExactly("Alpha")
    coordinator.close()
  }

  @Test
  fun `internal group has its own ordered and searchable section`() = runTest {
    val internalAlpha = plugin("internal.alpha", "Alpha Internal")
    val internalBeta = plugin("internal.beta", "Beta Internal")
    val marketplaceProvider = RecordingMarketplaceDataProvider()
    val coordinator = coordinator(
      provider = FakeLocalDataProvider(UnifiedPluginInventory(emptyList(), emptyList())),
      marketplaceDataProvider = marketplaceProvider,
      internalGroup = UnifiedPluginInternalGroup("Managed plugins", listOf(internalAlpha, internalBeta)),
    )
    coordinator.start()
    runCurrent()

    assertThat(coordinator.state.value.sections.map(PluginSectionState::id)).containsSubsequence(
      PluginSectionId.Installed,
      PluginSectionId.Bundled,
      PluginSectionId.Internal,
      PluginSectionId.Suggested,
    )
    assertThat(coordinator.state.value.sections.single { it.id == PluginSectionId.Internal }.title)
      .isEqualTo("Managed plugins")

    coordinator.setQuery("Alpha")
    runCurrent()
    assertThat(coordinator.state.value.sections.single { it.id == PluginSectionId.Internal }.items.map { it.pluginId.idString })
      .containsExactly("internal.alpha")

    coordinator.setQuery("/internal Beta")
    runCurrent()
    assertThat(coordinator.state.value.sections.single { it.id == PluginSectionId.Internal }.items.map { it.pluginId.idString })
      .containsExactly("internal.beta")
    assertThat(coordinator.state.value.sections.single { it.id == PluginSectionId.Marketplace }.items).isEmpty()
    assertThat(marketplaceProvider.searches).isEmpty()
    coordinator.close()
  }

  @Test
  fun `clearing a non-empty query records one search reset`() = runTest {
    var searchResetCount = 0
    val coordinator = coordinator(
      provider = FakeLocalDataProvider(UnifiedPluginInventory(emptyList(), emptyList())),
      initialQuery = "alpha",
      onSearchReset = { searchResetCount++ },
    )
    coordinator.start()
    runCurrent()

    coordinator.setQuery("  ")
    coordinator.setQuery("")
    assertThat(searchResetCount).isEqualTo(1)

    coordinator.setQuery("beta")
    coordinator.setQuery("")
    assertThat(searchResetCount).isEqualTo(2)
    coordinator.close()
  }

  @Test
  fun `search control intents use the owned query revision path`() = runTest {
    val coordinator = coordinator(FakeLocalDataProvider(UnifiedPluginInventory(emptyList(), emptyList())))
    coordinator.start()
    runCurrent()

    coordinator.applySearchControl(
      UnifiedPluginSearchControlIntent.ToggleAttribute(UnifiedPluginQueryAttribute.Vendor, "Acme Tools", true)
    )
    coordinator.applySearchControl(
      UnifiedPluginSearchControlIntent.ToggleInstalledFilter(UnifiedPluginInstalledFilter.Disabled, true)
    )
    coordinator.applySearchControl(
      UnifiedPluginSearchControlIntent.SelectSort(com.intellij.ide.plugins.MarketplaceTabSearchSortByOptions.NAME)
    )

    assertThat(coordinator.state.value.query.rawQuery)
      .isEqualTo("/vendor:\"Acme Tools\" /disabled /sortBy:name")
    assertThat(coordinator.state.value.query.revision).isEqualTo(3)
    coordinator.close()
  }

  @Test
  fun `installed advanced filters use plugin and row facts`() {
    val enabled = plugin("enabled.plugin", "Enabled").apply {
      displayCategory = "Productivity"
      vendor = "JetBrains"
      tags = listOf("Developer Tools")
    }
    val disabled = plugin("disabled.plugin", "Disabled")
    val bundled = plugin("bundled.plugin", "Bundled").apply { isBundled = true }
    val outdated = plugin("outdated.plugin", "Outdated")
    val invalid = plugin("invalid.plugin", "Invalid")
    val local = localState(
      listOf(
        PluginSectionState(
          PluginSectionId.Installed,
          items = listOf(
            localItem(enabled, enabled = true, tags = setOf("Developer Tools"), category = "Productivity"),
            localItem(disabled, enabled = false, category = "Languages"),
            localItem(outdated, enabled = true, update = plugin("outdated.plugin", "Update")),
            localItem(invalid, enabled = true, errors = listOf(HtmlChunk.text("broken"))),
          ),
        ),
        PluginSectionState(PluginSectionId.Bundled, items = listOf(localItem(bundled, enabled = true))),
      )
    )

    fun result(query: String): List<String> = composeUnifiedPluginsPageSourceState(
      PluginsQueryState(query, query, scope = PluginsQueryScope.Installed),
      local,
    ).sections.flatMap(PluginSectionState::items).map { it.pluginId.idString }

    assertThat(result("/enabled")).containsExactlyInAnyOrder("enabled.plugin", "bundled.plugin", "outdated.plugin")
    assertThat(result("/disabled")).containsExactly("disabled.plugin")
    assertThat(result("/bundled")).containsExactly("bundled.plugin")
    assertThat(result("/userInstalled")).containsExactlyInAnyOrder(
      "enabled.plugin", "disabled.plugin", "outdated.plugin", "invalid.plugin"
    )
    assertThat(result("/invalid")).containsExactly("invalid.plugin")
    assertThat(result("/outdated")).containsExactly("outdated.plugin")
    assertThat(result("/vendor: JetBrains")).containsExactly("enabled.plugin")
    assertThat(result("/category: Productivity")).containsExactly("enabled.plugin")
    assertThat(result("/category:Productivity /category:Languages"))
      .containsExactlyInAnyOrder("enabled.plugin", "disabled.plugin")
    assertThat(result("/category:Productivity /tag:\"Developer Tools\"")).containsExactly("enabled.plugin")
    assertThat(result("/category:Languages /tag:\"Developer Tools\"")).isEmpty()
    assertThat(result("/tag: \"Developer Tools\"")).containsExactly("enabled.plugin")
  }

  @Test
  fun `category filter applies to each locally projected source`() {
    val installedTool = localItem(plugin("installed.tool", "Installed Tool"), enabled = true, category = "Tools")
    val installedLanguage = localItem(plugin("installed.language", "Installed Language"), enabled = true, category = "Languages")
    val internalTool = item(plugin("internal.tool", "Internal Tool")).copy(searchCategory = "Tools")
    val internalLanguage = item(plugin("internal.language", "Internal Language")).copy(searchCategory = "Languages")
    val repositoryTool = item(plugin("repository.tool", "Repository Tool")).copy(searchCategory = "Tools")
    val repositoryLanguage = item(plugin("repository.language", "Repository Language")).copy(searchCategory = "Languages")
    val query = PluginsQueryState("/category:Tools", "/category:Tools", 1)

    val state = composeUnifiedPluginsPageSourceState(
      query = query,
      localState = localState(listOf(
        PluginSectionState(PluginSectionId.Installed, items = listOf(installedTool, installedLanguage)),
        PluginSectionState(PluginSectionId.Bundled),
      )),
      repositoryState = UnifiedPluginRepositorySourceState(
        sections = listOf(
          PluginSectionState(
            PluginSectionId.CustomRepository("repository"),
            items = listOf(repositoryTool, repositoryLanguage),
          )
        ),
        listModelData = PluginListModelData.EMPTY,
        repositoryPlugins = emptyList(),
        suggestionsRefreshRevision = 0,
      ),
      internalState = UnifiedPluginInternalSourceState(
        section = PluginSectionState(PluginSectionId.Internal, items = listOf(internalTool, internalLanguage)),
        listModelData = PluginListModelData.EMPTY,
        facetsLoading = false,
        descriptorRequestSettled = true,
      ),
    )

    assertThat(state.sections.single { it.id == PluginSectionId.Installed }.items).containsExactly(installedTool)
    assertThat(state.sections.single { it.id == PluginSectionId.Internal }.items).containsExactly(internalTool)
    assertThat(state.sections.single { it.id is PluginSectionId.CustomRepository }.items).containsExactly(repositoryTool)
  }

  @Test
  fun `local relevance prioritizes errors while explicit sorts override that priority`() {
    val healthy = plugin("healthy.plugin", "Alpha Plugin").apply {
      downloads = "100"
      rating = "5"
      releaseDate = 100
    }
    val broken = plugin("broken.plugin", "Zulu Plugin").apply {
      downloads = "1"
      rating = "1"
      releaseDate = 1
    }
    val local = localState(
      listOf(
        PluginSectionState(
          PluginSectionId.Installed,
          items = listOf(
            localItem(healthy, enabled = true),
            localItem(broken, enabled = true, errors = listOf(HtmlChunk.text("broken"))),
          ),
        ),
        PluginSectionState(PluginSectionId.Bundled),
      )
    )

    fun result(query: String): List<String> = composeUnifiedPluginsPageSourceState(
      PluginsQueryState(query, query),
      local,
    ).sections.single { it.id == PluginSectionId.Installed }.items.map { it.pluginId.idString }

    assertThat(result("Plugin")).containsExactly("broken.plugin", "healthy.plugin")
    assertThat(result("Plugin /sortBy:name")).containsExactly("healthy.plugin", "broken.plugin")
    assertThat(result("Plugin /sortBy:downloads")).containsExactly("healthy.plugin", "broken.plugin")
    assertThat(result("Plugin /sortBy:rating")).containsExactly("healthy.plugin", "broken.plugin")
    assertThat(result("Plugin /sortBy:updated")).containsExactly("healthy.plugin", "broken.plugin")
  }

  @Test
  fun `installed relevance uses legacy name and description match scores`() {
    val shortName = localItem(plugin("name.short", "Target"), enabled = true)
    val longName = localItem(plugin("name.long", "Target Integration"), enabled = true)
    val shortDescription = localItem(
      plugin("description.short", "Brief").apply { description = "Target" },
      enabled = true,
    )
    val longDescription = localItem(
      plugin("description.long", "Verbose").apply { description = "Target appears in a longer description" },
      enabled = true,
    )
    val local = localState(
      listOf(
        PluginSectionState(
          PluginSectionId.Installed,
          items = listOf(longDescription, longName, shortDescription, shortName),
        ),
        PluginSectionState(PluginSectionId.Bundled),
      )
    )

    val result = composeUnifiedPluginsPageSourceState(
      PluginsQueryState("Target", "Target"),
      local,
    ).sections.single { it.id == PluginSectionId.Installed }.items.map { it.pluginId.idString }

    assertThat(result).containsExactly("name.short", "name.long", "description.short", "description.long")
  }

  @Test
  fun `bundled relevance uses match score before category tie breakers`() {
    val exactName = localItem(plugin("name.exact", "Target"), enabled = true, category = "tools")
    val language = localItem(plugin("name.language", "Target B"), enabled = true, category = "Languages")
    val tool = localItem(plugin("name.tool", "Target A"), enabled = true, category = "Tools")
    val longName = localItem(plugin("name.long", "Target Integration"), enabled = true, category = "Actions")
    val local = localState(
      listOf(
        PluginSectionState(PluginSectionId.Installed),
        PluginSectionState(
          PluginSectionId.Bundled,
          items = listOf(longName, tool, language, exactName),
        ),
      )
    )

    val result = composeUnifiedPluginsPageSourceState(
      PluginsQueryState("Target", "Target"),
      local,
    ).sections.single { it.id == PluginSectionId.Bundled }.items.map { it.pluginId.idString }

    assertThat(result).containsExactly("name.exact", "name.language", "name.tool", "name.long")
  }

  @Test
  fun `bundled relevance sorts exact categories and names with Other last`() {
    val alphaTool = localItem(plugin("alpha.tool", "Alpha Tool"), enabled = true, category = "Tools")
    val zuluTool = localItem(plugin("zulu.tool", "Zulu Tool"), enabled = true, category = "Tools")
    val alphaLowerTool = localItem(plugin("alpha.lower.tool", "Alpha Lower Tool"), enabled = true, category = "tools")
    val zuluLowerTool = localItem(plugin("zulu.lower.tool", "Zulu Lower Tool"), enabled = true, category = "tools")
    val other = localItem(plugin("other.plugin", "Other Plugin"), enabled = true)
    val zuluLanguage = localItem(plugin("zulu.language", "Zulu Language"), enabled = true, category = "Languages")
    val alphaLanguage = localItem(plugin("alpha.language", "Alpha Language"), enabled = true, category = "Languages")
    val local = localState(
      listOf(
        PluginSectionState(PluginSectionId.Installed),
        PluginSectionState(
          PluginSectionId.Bundled,
          items = listOf(alphaTool, zuluLowerTool, other, zuluLanguage, alphaLowerTool, alphaLanguage, zuluTool),
        ),
      )
    )

    fun result(query: String): List<String> = composeUnifiedPluginsPageSourceState(
      PluginsQueryState(query, query),
      local,
    ).sections.single { it.id == PluginSectionId.Bundled }.items.map { it.pluginId.idString }

    assertThat(result("")).containsExactly(
      "alpha.language",
      "zulu.language",
      "alpha.tool",
      "zulu.tool",
      "alpha.lower.tool",
      "zulu.lower.tool",
      "other.plugin",
    )
    assertThat(result("/sortBy:name")).containsExactly(
      "alpha.language",
      "alpha.lower.tool",
      "alpha.tool",
      "other.plugin",
      "zulu.language",
      "zulu.lower.tool",
      "zulu.tool",
    )
  }

  @Test
  fun `repository filter targets one cached section and keeps local sections unfiltered`() {
    val localPlugin = item(plugin("local.plugin", "Local"))
    val firstPlugin = item(plugin("first.plugin", "First"))
    val secondPlugin = item(plugin("second.plugin", "Second"))
    val state = composeUnifiedPluginsPageSourceState(
      PluginsQueryState(
        rawQuery = "/repository: second",
        normalizedQuery = "/repository: second",
        scope = PluginsQueryScope.Marketplace,
      ),
      localState(listOf(
        PluginSectionState(PluginSectionId.Installed, items = listOf(localPlugin)),
        PluginSectionState(PluginSectionId.Bundled),
      )),
      repositoryState = UnifiedPluginRepositorySourceState(
        sections = listOf(
          PluginSectionState(PluginSectionId.CustomRepository("first"), items = listOf(firstPlugin)),
          PluginSectionState(PluginSectionId.CustomRepository("second"), items = listOf(secondPlugin)),
        ),
        listModelData = PluginListModelData.EMPTY,
        repositoryPlugins = emptyList(),
        suggestionsRefreshRevision = 0,
      ),
    )

    assertThat(installedIds(state)).containsExactly("local.plugin")
    assertThat(repositorySections(state)[0].items).isEmpty()
    assertThat(repositorySections(state)[1].items).containsExactly(secondPlugin)
  }

  @Test
  fun `repository catalog failure remains visible without a query`() {
    val error = PluginSectionError("Unable to load repository plugins", retryable = true)
    val state = composeUnifiedPluginsPageSourceState(
      PluginsQueryState(),
      localState(listOf(PluginSectionState(PluginSectionId.Installed), PluginSectionState(PluginSectionId.Bundled))),
      repositoryState = UnifiedPluginRepositorySourceState(
        sections = listOf(
          PluginSectionState(
            PluginSectionId.CustomRepositoryCatalog,
            status = PluginSectionStatus.Failed(error),
          )
        ),
        listModelData = PluginListModelData.EMPTY,
        repositoryPlugins = emptyList(),
        suggestionsRefreshRevision = 0,
      ),
    )

    val section = state.sections.single { it.id == PluginSectionId.CustomRepositoryCatalog }
    assertThat(section.status).isEqualTo(PluginSectionStatus.Failed(error))
  }

  @Test
  fun `unified repository filter empties local and primary sections`() {
    val localPlugin = item(plugin("local.plugin", "Local"))
    val firstPlugin = item(plugin("first.plugin", "First"))
    val secondPlugin = item(plugin("second.plugin", "Second"))
    val state = composeUnifiedPluginsPageSourceState(
      PluginsQueryState("/repository:second", "/repository:second"),
      localState(listOf(
        PluginSectionState(PluginSectionId.Installed, items = listOf(localPlugin), status = PluginSectionStatus.Loading(false)),
        PluginSectionState(PluginSectionId.Bundled),
      )),
      repositoryState = UnifiedPluginRepositorySourceState(
        sections = listOf(
          PluginSectionState(PluginSectionId.CustomRepository("first"), items = listOf(firstPlugin)),
          PluginSectionState(PluginSectionId.CustomRepository("second"), items = listOf(secondPlugin)),
        ),
        listModelData = PluginListModelData.EMPTY,
        repositoryPlugins = emptyList(),
        suggestionsRefreshRevision = 0,
      ),
    )

    assertThat(installedIds(state)).isEmpty()
    assertThat(state.sections.single { it.id == PluginSectionId.Installed }.status).isEqualTo(PluginSectionStatus.Ready)
    assertThat(state.sections.single { it.id == PluginSectionId.Marketplace }.status).isEqualTo(PluginSectionStatus.Ready)
    assertThat(repositorySections(state)[0].items).isEmpty()
    assertThat(repositorySections(state)[1].items).containsExactly(secondPlugin)
  }

  @Test
  fun `sort controls primary Marketplace without suppressing cached filtering`() {
    val alpha = item(plugin("alpha.plugin", "Alpha"))
    val beta = item(plugin("beta.plugin", "Beta"))
    val query = PluginsQueryState("Beta /sortBy:downloads", "Beta /sortBy:downloads")
    val state = composeUnifiedPluginsPageSourceState(
      query,
      localState(listOf(
        PluginSectionState(PluginSectionId.Installed, items = listOf(alpha, beta)),
        PluginSectionState(PluginSectionId.Bundled),
      )),
      repositoryState = UnifiedPluginRepositorySourceState(
        sections = listOf(PluginSectionState(PluginSectionId.CustomRepository("repository"), items = listOf(alpha, beta))),
        listModelData = PluginListModelData.EMPTY,
        repositoryPlugins = emptyList(),
        suggestionsRefreshRevision = 0,
      ),
    )

    assertThat(installedIds(state)).containsExactly("beta.plugin")
    assertThat(repositorySections(state).single().items.map { it.pluginId.idString }).containsExactly("beta.plugin")
    assertThat(state.searchControls.sortVisible).isTrue()
  }

  @Test
  fun `installed and repository constraints leave every filtered source ready and empty`() {
    val plugin = item(plugin("plugin", "Plugin"))
    val state = composeUnifiedPluginsPageSourceState(
      PluginsQueryState("/enabled /repository:repository", "/enabled /repository:repository"),
      localState(listOf(
        PluginSectionState(PluginSectionId.Installed, items = listOf(plugin), status = PluginSectionStatus.Loading(false)),
        PluginSectionState(PluginSectionId.Bundled),
      )),
      repositoryState = UnifiedPluginRepositorySourceState(
        sections = listOf(
          PluginSectionState(
            PluginSectionId.CustomRepository("repository"),
            items = listOf(plugin),
            status = PluginSectionStatus.Loading(false),
          )
        ),
        listModelData = PluginListModelData.EMPTY,
        repositoryPlugins = emptyList(),
        suggestionsRefreshRevision = 0,
      ),
    )

    assertThat(state.sections.filter { it.id != PluginSectionId.Installing }).allSatisfy { section ->
      assertThat(section.items).isEmpty()
      assertThat(section.status).isEqualTo(PluginSectionStatus.Ready)
    }
  }

  @Test
  fun `query projection keeps installing unfiltered and switches marketplace family`() {
    val installing = item(plugin("installing.plugin", "Installing"))
    val local = localState(
      sections = listOf(
        PluginSectionState(PluginSectionId.Installing, items = listOf(installing)),
        PluginSectionState(PluginSectionId.Installed),
        PluginSectionState(PluginSectionId.Bundled),
      )
    )

    val state = composeUnifiedPluginsPageSourceState(PluginsQueryState("missing", "missing", 1), local)

    assertThat(state.sections.map { it.id }).containsExactly(
      PluginSectionId.Installing,
      PluginSectionId.Installed,
      PluginSectionId.Bundled,
      PluginSectionId.Marketplace,
    )
    assertThat(state.sections.first().items).containsExactly(installing)
  }

  @Test
  fun `filter options combine unfiltered source metadata and selected absent values`() {
    val local = localState(
      sections = listOf(
        PluginSectionState(
          PluginSectionId.Installed,
          items = listOf(
            item(plugin("local.plugin", "Local")).copy(
              searchCategory = "Tools",
              searchVendor = "zeta",
              searchTags = setOf("Local Tag", " shared "),
            )
          ),
        ),
        PluginSectionState(PluginSectionId.Bundled),
      ),
    ).copy(facetsLoading = true)
    val suggestedItem = item(plugin("suggested.plugin", "Suggested")).copy(
      searchCategory = "Languages",
      searchVendor = "Acme",
      searchTags = setOf("Shared", "Suggested Tag"),
    )
    val repositoryItem = item(plugin("repository.plugin", "Repository")).copy(
      searchCategory = "Tools Integration",
      searchVendor = "JetBrains",
      searchTags = setOf("Repository Tag"),
    )
    val query = PluginsQueryState(
      rawQuery = "/vendor:Missing /category:Missing /tag:\"Manual Tag\" /repository:removed /disabled /sortBy:rating",
      normalizedQuery = "/vendor:Missing /category:Missing /tag:\"Manual Tag\" /repository:removed /disabled /sortBy:rating",
      revision = 1,
    )

    val state = composeUnifiedPluginsPageSourceState(
      query = query,
      localState = local,
      marketplaceState = UnifiedPluginMarketplaceSourceState(
        queryRevision = 1,
        section = PluginSectionState(PluginSectionId.Marketplace),
        listModelData = PluginListModelData.EMPTY,
        suggestedFacetItems = listOf(suggestedItem),
      ),
      repositoryState = UnifiedPluginRepositorySourceState(
        sections = listOf(
          PluginSectionState(PluginSectionId.CustomRepository("second"), items = listOf(repositoryItem)),
          PluginSectionState(PluginSectionId.CustomRepository("first")),
        ),
        listModelData = PluginListModelData.EMPTY,
        repositoryPlugins = emptyList(),
        suggestionsRefreshRevision = 0,
        facetsLoading = true,
      ),
    )

    val controls = state.searchControls
    assertThat(controls.options.vendors).containsExactly("Acme", "JetBrains", "Missing", "zeta")
    assertThat(controls.options.categories).containsExactly("Languages", "Missing", "Tools", "Tools Integration")
    assertThat(controls.options.tags).containsExactly("Local Tag", "Manual Tag", "Repository Tag", "Shared", "shared", "Suggested Tag")
    assertThat(controls.options.repositories).containsExactly("second", "first", "removed")
    assertThat(controls.options.facetValuesLoading).isTrue()
    assertThat(controls.selectedVendors).containsExactly("Missing")
    assertThat(controls.selectedCategories).containsExactly("Missing")
    assertThat(controls.selectedTags).containsExactly("Manual Tag")
    assertThat(controls.selectedRepositories).containsExactly("removed")
    assertThat(controls.selectedInstalledFilter).isEqualTo(UnifiedPluginInstalledFilter.Disabled)
    assertThat(controls.effectiveSort).isEqualTo(com.intellij.ide.plugins.MarketplaceTabSearchSortByOptions.RATING)
    assertThat(controls.filterSelected).isTrue()
  }

  @Test
  fun `filter options put all Marketplace tags before other tags`() {
    val marketplaceTags = (1..25).map { index -> "Marketplace Tag $index" }
    val local = localState(
      sections = listOf(
        PluginSectionState(
          PluginSectionId.Installed,
          items = listOf(
            item(plugin("local.plugin", "Local")).copy(searchTags = setOf("Local Tag", "Shared"))
          ),
        ),
        PluginSectionState(PluginSectionId.Bundled),
      ),
    )
    val query = PluginsQueryState(
      rawQuery = "/tag:\"Selected Tag\"",
      normalizedQuery = "/tag:\"Selected Tag\"",
      revision = 1,
    )

    val state = composeUnifiedPluginsPageSourceState(
      query = query,
      localState = local,
      marketplaceState = UnifiedPluginMarketplaceSourceState(
        queryRevision = 1,
        section = PluginSectionState(PluginSectionId.Marketplace),
        listModelData = PluginListModelData.EMPTY,
        popularTags = marketplaceTags + "Shared",
        popularTagsLoading = true,
      ),
    )

    assertThat(state.searchControls.options.tags)
      .containsExactlyElementsOf(marketplaceTags + listOf("Shared", "Local Tag", "Selected Tag"))
    assertThat(state.searchControls.options.facetValuesLoading).isTrue()
  }

  @Test
  fun `projection rejects a stale marketplace snapshot`() {
    val local = localState()
    val remoteModel = plugin("remote.plugin", "Remote")
    val remote = UnifiedPluginMarketplaceSourceState(
      queryRevision = 1,
      section = PluginSectionState(PluginSectionId.Marketplace, items = listOf(item(remoteModel))),
      listModelData = PluginListModelData(
        installedModels = mapOf(remoteModel.pluginId to remoteModel),
        errors = emptyMap(),
        installationStates = emptyMap(),
      ),
    )

    val state = composeUnifiedPluginsPageSourceState(PluginsQueryState("new", "new", 2), local, remote)

    assertThat(state.sections.last().id).isEqualTo(PluginSectionId.Marketplace)
    assertThat(state.sections.last().items).isEmpty()
    assertThat(state.listModelData.installedModels).isEmpty()
  }

  @Test
  fun `local list model facts override remote facts`() {
    val pluginId = PluginId.getId("shared.plugin")
    val localModel = plugin(pluginId.idString, "Local")
    val remoteModel = plugin(pluginId.idString, "Remote")
    val localState = PluginInstallationState(true)
    val remoteState = PluginInstallationState(false)
    val localErrors = listOf(HtmlChunk.text("local"))
    val remoteErrors = listOf(HtmlChunk.text("remote"))

    val merged = mergePluginListModelData(
      PluginListModelData(mapOf(pluginId to localModel), mapOf(pluginId to localErrors), mapOf(pluginId to localState)),
      PluginListModelData(mapOf(pluginId to remoteModel), mapOf(pluginId to remoteErrors), mapOf(pluginId to remoteState)),
    )

    assertThat(merged.installedModels[pluginId]).isSameAs(localModel)
    assertThat(merged.errors[pluginId]).isSameAs(localErrors)
    assertThat(merged.installationStates[pluginId]).isSameAs(localState)
  }

  @Test
  fun `query filters repository sections without refetching and preserves source order`() = runTest {
    val first = CustomPluginRepository("first", PluginSource.LOCAL)
    val second = CustomPluginRepository("second", PluginSource.LOCAL)
    val repositoryProvider = FixedRepositoryDataProvider(
      repositories = listOf(first, second),
      plugins = mapOf(
        first.id to listOf(plugin("alpha.plugin", "Alpha"), plugin("beta.plugin", "Beta")),
        second.id to listOf(plugin("gamma.plugin", "Gamma")),
      ),
    )
    val coordinator = coordinator(
      provider = FakeLocalDataProvider(UnifiedPluginInventory(emptyList(), emptyList())),
      repositoryDataProvider = repositoryProvider,
    )
    coordinator.start()
    runCurrent()

    assertThat(repositorySections(coordinator.state.value).map { (it.id as PluginSectionId.CustomRepository).repositoryId })
      .containsExactly("first", "second")

    coordinator.setQuery("Beta")
    runCurrent()

    val sections = repositorySections(coordinator.state.value)
    assertThat(sections[0].items.map { it.pluginId.idString }).containsExactly("beta.plugin")
    assertThat(sections[1].items).isEmpty()
    assertThat(repositoryProvider.repositoryLoadCount).isEqualTo(2)
    coordinator.close()
  }

  @Test
  fun `repository retry refreshes explicit Suggested query only after repository settlement`() = runTest {
    val repository = CustomPluginRepository("repository", PluginSource.LOCAL)
    val repositoryProvider = FixedRepositoryDataProvider(
      repositories = listOf(repository),
      plugins = mapOf(repository.id to listOf(plugin("repository.plugin", "Repository"))),
      failFirstLoad = true,
    )
    val marketplaceProvider = CountingMarketplaceDataProvider()
    val coordinator = coordinator(
      provider = FakeLocalDataProvider(UnifiedPluginInventory(emptyList(), emptyList())),
      initialQuery = "/suggested",
      repositoryDataProvider = repositoryProvider,
      marketplaceDataProvider = marketplaceProvider,
    )
    coordinator.start()
    runCurrent()

    assertThat(marketplaceProvider.suggestedCount).isEqualTo(1)
    coordinator.retry(PluginSectionId.CustomRepository(repository.id))
    runCurrent()

    assertThat(repositoryProvider.repositoryLoadCount).isEqualTo(2)
    assertThat(marketplaceProvider.suggestedCount).isEqualTo(2)
    coordinator.close()
  }

  @Test
  fun `local section retry reloads a failed inventory`() = runTest {
    val provider = FakeLocalDataProvider(
      inventory = UnifiedPluginInventory(emptyList(), emptyList()),
      failFirstLoad = true,
    )
    val coordinator = coordinator(provider)
    coordinator.start()
    runCurrent()

    assertThat(coordinator.state.value.sections.filter { it.id == PluginSectionId.Installed || it.id == PluginSectionId.Bundled })
      .allSatisfy { assertThat(it.status).isInstanceOf(PluginSectionStatus.Failed::class.java) }
    coordinator.retry(PluginSectionId.Installed)
    runCurrent()

    assertThat(provider.loadCount).isEqualTo(2)
    assertThat(coordinator.state.value.sections.filter { it.id == PluginSectionId.Installed || it.id == PluginSectionId.Bundled })
      .allSatisfy { assertThat(it.status).isEqualTo(PluginSectionStatus.Ready) }

    coordinator.retry(PluginSectionId.Bundled)
    runCurrent()

    assertThat(provider.loadCount).isEqualTo(3)
    coordinator.close()
  }

  private fun TestScope.coordinator(
    provider: UnifiedPluginLocalDataProvider,
    initialQuery: String = "",
    updates: Flow<PluginUpdatesEvent> = emptyFlow(),
    hostEvents: Flow<PluginModelEvent> = flow { awaitCancellation() },
    repositoryDataProvider: UnifiedPluginRepositoryDataProvider = EmptyRepositoryDataProvider,
    marketplaceDataProvider: UnifiedPluginMarketplaceDataProvider = EmptyMarketplaceDataProvider,
    internalGroup: UnifiedPluginInternalGroup? = null,
    onSearchReset: () -> Unit = {},
  ): UnifiedPluginsPageSourceCoordinator {
    val localSource = UnifiedPluginLocalSourceCoordinator(
      scope = backgroundScope,
      dataProvider = provider,
      updates = updates,
      hostEvents = hostEvents,
      sessionId = "session",
      loadErrorMessage = "Unable to load plugins",
    )
    val query = initialPluginsQueryState(initialQuery)
    val internalSource = UnifiedPluginInternalSourceCoordinator(
      scope = backgroundScope,
      loadGroup = { internalGroup },
      dataEnricher = marketplaceDataProvider,
      updates = emptyFlow(),
      loadingTitle = "Internal plugins",
      loadErrorMessage = "Unable to load internal plugins",
    )
    val marketplaceSource = UnifiedPluginMarketplaceSourceCoordinator(
      scope = backgroundScope,
      initialQuery = query,
      dataProvider = marketplaceDataProvider,
      updates = emptyFlow(),
      loadErrorMessage = "Unable to load Marketplace plugins",
    )
    val repositorySource = UnifiedPluginRepositorySourceCoordinator(
      scope = backgroundScope,
      repositoryCache = UnifiedPluginRepositoryCache(backgroundScope, repositoryDataProvider),
      dataEnricher = marketplaceDataProvider,
      updates = emptyFlow(),
      loadErrorMessage = "Unable to load repository plugins",
    )
    return UnifiedPluginsPageSourceCoordinator(
      backgroundScope,
      initialQuery,
      localSource,
      internalSource,
      marketplaceSource,
      repositorySource,
      onSearchReset,
      StandardTestDispatcher(testScheduler),
    )
  }

  private class FakeLocalDataProvider(
    private val inventory: UnifiedPluginInventory,
    private val failFirstLoad: Boolean = false,
  ) : UnifiedPluginLocalDataProvider {
    var loadCount = 0

    override suspend fun loadInventory(): UnifiedPluginInventory {
      loadCount++
      if (failFirstLoad && loadCount == 1) error("offline")
      return inventory
    }

    override suspend fun enrich(
      inventory: UnifiedPluginInventory,
      updates: PluginUpdatesEvent?,
      contentRevision: Long,
    ): UnifiedPluginLocalSnapshot {
      val plugins = inventory.installedPlugins + inventory.bundledPlugins
      return buildLocalSnapshot(
        inventory = inventory,
        updates = updates,
        contentRevision = contentRevision,
        enabledStates = plugins.associate { it.model.pluginId to true },
        errors = emptyMap(),
        installationStates = emptyMap(),
        restrictions = emptyMap(),
      )
    }
  }

  private data object EmptyMarketplaceDataProvider : UnifiedPluginMarketplaceDataProvider {
    override fun loadSuggested(): Flow<UnifiedPluginMarketplaceFetchResult> =
      flowOf(UnifiedPluginMarketplaceFetchResult(emptyList()))

    override suspend fun searchMarketplace(query: String): UnifiedPluginMarketplaceFetchResult {
      return UnifiedPluginMarketplaceFetchResult(emptyList())
    }

    override suspend fun enrich(
      models: List<PluginUiModel>,
      updates: PluginUpdatesEvent?,
      contentRevision: Long,
    ): UnifiedPluginMarketplaceSnapshot {
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

  private data object EmptyRepositoryDataProvider : UnifiedPluginRepositoryDataProvider {
    override suspend fun loadCatalog(): UnifiedPluginRepositoryCatalogResult = UnifiedPluginRepositoryCatalogResult(emptyList())

    override suspend fun loadRepository(repository: CustomPluginRepository): CustomPluginRepositoryLoadResult {
      return CustomPluginRepositoryLoadResult(emptyList())
    }
  }

  private class FixedRepositoryDataProvider(
    private val repositories: List<CustomPluginRepository>,
    private val plugins: Map<String, List<PluginUiModel>>,
    private val failFirstLoad: Boolean = false,
  ) : UnifiedPluginRepositoryDataProvider {
    var repositoryLoadCount = 0

    override suspend fun loadCatalog(): UnifiedPluginRepositoryCatalogResult {
      return UnifiedPluginRepositoryCatalogResult(repositories)
    }

    override suspend fun loadRepository(repository: CustomPluginRepository): CustomPluginRepositoryLoadResult {
      repositoryLoadCount++
      if (failFirstLoad && repositoryLoadCount == 1) return CustomPluginRepositoryLoadResult(emptyList(), "offline")
      return CustomPluginRepositoryLoadResult(plugins[repository.id].orEmpty())
    }
  }

  private class CountingMarketplaceDataProvider : UnifiedPluginMarketplaceDataProvider by EmptyMarketplaceDataProvider {
    var suggestedCount = 0

    override fun loadSuggested(): Flow<UnifiedPluginMarketplaceFetchResult> = flow {
      suggestedCount++
      emit(UnifiedPluginMarketplaceFetchResult(emptyList()))
    }
  }

  private class RecordingMarketplaceDataProvider : UnifiedPluginMarketplaceDataProvider by EmptyMarketplaceDataProvider {
    var suggestedCount = 0
    val searches = mutableListOf<String>()

    override fun loadSuggested(): Flow<UnifiedPluginMarketplaceFetchResult> = flow {
      suggestedCount++
      emit(UnifiedPluginMarketplaceFetchResult(emptyList()))
    }

    override suspend fun searchMarketplace(query: String): UnifiedPluginMarketplaceFetchResult {
      searches.add(query)
      return UnifiedPluginMarketplaceFetchResult(emptyList())
    }
  }

  private fun localState(
    sections: List<PluginSectionState> = listOf(
      PluginSectionState(PluginSectionId.Installed),
      PluginSectionState(PluginSectionId.Bundled),
    ),
  ): UnifiedPluginLocalSourceState {
    return UnifiedPluginLocalSourceState(sections, PluginListModelData.EMPTY, mayEstablishSelection = true)
  }

  private fun inventoryItem(id: String, name: String): UnifiedPluginInventoryItem {
    return UnifiedPluginInventoryItem(plugin(id, name), runtimeOn = PluginSource.LOCAL, stagedOn = null, bundledOn = null)
  }

  private fun item(model: PluginDto): PluginItemState {
    return PluginItemState(model.pluginId, model.name, modelHandle = PluginItemModelHandle(model))
  }

  private fun localItem(
    model: PluginDto,
    enabled: Boolean,
    update: PluginUiModel? = null,
    errors: List<HtmlChunk> = emptyList(),
    tags: Set<String> = emptySet(),
    category: String? = null,
  ): PluginItemState {
    return PluginItemState(
      pluginId = model.pluginId,
      name = model.name,
      modelHandle = PluginItemModelHandle(model),
      rowInput = PluginRowInput(
        installedPlugin = model,
        installationState = PluginInstallationState(true),
        errors = errors,
        updateDescriptor = update,
        enabled = enabled,
        restrictedByProduct = false,
      ),
      searchCategory = category,
      searchTags = tags,
    )
  }

  private fun plugin(id: String, name: String): PluginDto = PluginDto(name, PluginId.getId(id))

  private fun installedIds(state: UnifiedPluginsPageSourceState): List<String> {
    return state.sections.single { it.id == PluginSectionId.Installed }.items.map { it.pluginId.idString }
  }

  private fun repositorySections(state: UnifiedPluginsPageSourceState): List<PluginSectionState> {
    return state.sections.filter { it.id is PluginSectionId.CustomRepository }
  }
}
