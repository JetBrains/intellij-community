// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.api.PluginDto
import com.intellij.ide.plugins.newui.PluginInstallationState
import com.intellij.ide.plugins.newui.PluginRowInput
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.text.HtmlChunk
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class UnifiedPluginsPageControllerTest {
  @Test
  fun `selection spans compatible local sections but not operation modes`() {
    val installed = section(PluginSectionId.Installed, "installed.plugin")
    val bundled = section(PluginSectionId.Bundled, "bundled.plugin")
    val marketplace = section(PluginSectionId.Suggested, "marketplace.plugin")
    val controller = UnifiedPluginsPageController(listOf(installed, bundled, marketplace))
    val installedOccurrence = installed.occurrenceId(installed.items.single().pluginId)
    val bundledOccurrence = bundled.occurrenceId(bundled.items.single().pluginId)
    val marketplaceOccurrence = marketplace.occurrenceId(marketplace.items.single().pluginId)

    controller.selectOccurrences(listOf(installedOccurrence, bundledOccurrence))

    assertThat(controller.state.value.selectedOccurrences).containsExactly(installedOccurrence, bundledOccurrence)

    controller.selectOccurrences(listOf(installedOccurrence, bundledOccurrence, marketplaceOccurrence))

    assertThat(controller.state.value.selectedOccurrences).containsExactly(marketplaceOccurrence)
  }

  @Test
  fun `selection retains surviving occurrences when one local section changes`() {
    val installed = section(PluginSectionId.Installed, "installed.plugin")
    val bundled = section(PluginSectionId.Bundled, "bundled.plugin")
    val controller = UnifiedPluginsPageController(listOf(installed, bundled))
    val installedOccurrence = installed.occurrenceId(installed.items.single().pluginId)
    val bundledOccurrence = bundled.occurrenceId(bundled.items.single().pluginId)
    controller.selectOccurrences(listOf(installedOccurrence, bundledOccurrence))

    controller.updateSection(PluginSectionState(PluginSectionId.Installed))

    assertThat(controller.state.value.selectedOccurrences).containsExactly(bundledOccurrence)
  }

  @Test
  fun `Installed search intent hides an inactive empty Marketplace family`() {
    val controller = UnifiedPluginsPageController()

    controller.replaceSourceState(
      query = PluginsQueryState(
        rawQuery = "/enabled",
        normalizedQuery = "/enabled",
        revision = 1,
        scope = PluginsQueryScope.Installed,
      ),
      updatedSections = listOf(
        PluginSectionState(PluginSectionId.Installed),
        PluginSectionState(PluginSectionId.Bundled),
        PluginSectionState(PluginSectionId.Suggested),
      ),
      mayEstablishSelection = true,
    )

    assertThat(controller.state.value.sections.map { it.id })
      .doesNotContain(PluginSectionId.Suggested, PluginSectionId.Marketplace)
  }

  @Test
  fun `default ready sections are hidden`() {
    val state = UnifiedPluginsPageController().state.value

    assertThat(state.sections).isEmpty()
    assertThat(state.selectedOccurrence).isNull()
  }

  @Test
  fun `sections use semantic order and preserve custom repository order`() {
    val firstRepository = PluginSectionId.CustomRepository("first")
    val secondRepository = PluginSectionId.CustomRepository("second")
    val controller = UnifiedPluginsPageController(
      initialSections = listOf(
        section(secondRepository, "second.plugin"),
        section(PluginSectionId.Bundled, "bundled.plugin"),
        section(firstRepository, "first.plugin"),
        section(PluginSectionId.Installing, "installing.plugin"),
        section(PluginSectionId.Installed, "installed.plugin"),
        section(PluginSectionId.Marketplace, "marketplace.plugin"),
      ),
      initialQuery = PluginsQueryState("query", "query"),
    )

    assertThat(controller.state.value.sections.map { it.id }).containsExactly(
      PluginSectionId.Installing,
      PluginSectionId.Installed,
      PluginSectionId.Bundled,
      PluginSectionId.Marketplace,
      secondRepository,
      firstRepository,
    )
  }

  @Test
  fun `loading and failed empty sections remain visible until they settle empty`() {
    val error = PluginSectionError("Unable to load plugins", retryable = true)
    val controller = UnifiedPluginsPageController(
      listOf(
        PluginSectionState(PluginSectionId.Installed),
        PluginSectionState(PluginSectionId.Bundled, status = PluginSectionStatus.Loading(false)),
        PluginSectionState(PluginSectionId.Suggested, status = PluginSectionStatus.Failed(error)),
        PluginSectionState(PluginSectionId.Internal, status = PluginSectionStatus.Degraded(error)),
      )
    )

    assertThat(controller.state.value.sections.map { it.id }).containsExactly(
      PluginSectionId.Bundled,
      PluginSectionId.Suggested,
      PluginSectionId.Internal,
    )

    controller.updateSections(
      listOf(
        PluginSectionState(PluginSectionId.Bundled),
        PluginSectionState(PluginSectionId.Suggested),
        PluginSectionState(PluginSectionId.Internal),
      )
    )

    assertThat(controller.state.value.sections).isEmpty()
  }

  @Test
  fun `failed custom repository remains visible without a query until retry`() {
    val repositoryId = PluginSectionId.CustomRepository("repository")
    val error = PluginSectionError("Unable to load repository plugins", retryable = true)
    val controller = UnifiedPluginsPageController(
      listOf(PluginSectionState(repositoryId, status = PluginSectionStatus.Loading(false)))
    )

    assertThat(controller.state.value.sections.map { it.id }).doesNotContain(repositoryId)

    controller.updateSection(PluginSectionState(repositoryId, status = PluginSectionStatus.Failed(error)))
    assertThat(controller.state.value.sections.map { it.id }).contains(repositoryId)

    controller.updateSection(PluginSectionState(repositoryId, status = PluginSectionStatus.Loading(false)))
    assertThat(controller.state.value.sections.map { it.id }).doesNotContain(repositoryId)

    controller.updateSection(PluginSectionState(repositoryId))
    assertThat(controller.state.value.sections.map { it.id }).doesNotContain(repositoryId)

    controller.setQuery(PluginsQueryState("query", "query", 1))
    controller.updateSection(PluginSectionState(repositoryId, status = PluginSectionStatus.Failed(error)))
    assertThat(controller.state.value.sections.map { it.id }).contains(repositoryId)

    controller.updateSection(PluginSectionState(repositoryId))
    assertThat(controller.state.value.sections.map { it.id }).doesNotContain(repositoryId)
  }

  @Test
  fun `repository filter keeps its empty selected repository visible`() {
    val repositoryId = PluginSectionId.CustomRepository("repository")
    val controller = UnifiedPluginsPageController(listOf(PluginSectionState(repositoryId)))

    assertThat(controller.state.value.sections.map { it.id }).doesNotContain(repositoryId)

    controller.setQuery(PluginsQueryState("/repository:repository", "/repository:repository", 1))
    assertThat(controller.state.value.sections.map { it.id }).contains(repositoryId)

    controller.setQuery(PluginsQueryState("/repository:other", "/repository:other", 2))
    assertThat(controller.state.value.sections.map { it.id }).doesNotContain(repositoryId)

    controller.setQuery(PluginsQueryState("query", "query", 3))
    assertThat(controller.state.value.sections.map { it.id }).doesNotContain(repositoryId)

    controller.setQuery(PluginsQueryState("/disabled /repository:repository", "/disabled /repository:repository", 4))
    assertThat(controller.state.value.sections.map { it.id }).doesNotContain(repositoryId)
  }

  @Test
  fun `collapsed section projects three items and loading replaces count`() {
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, itemCount = 5))

    var installed = controller.state.value.section(PluginSectionId.Installed)
    assertThat(installed.count).isEqualTo(5)
    assertThat(installed.canExpand).isTrue()
    assertThat(installed.visibleItems).hasSize(PluginSectionState.COLLAPSED_ITEM_LIMIT)

    controller.setSectionExpanded(PluginSectionId.Installed, true)
    installed = controller.state.value.section(PluginSectionId.Installed)
    assertThat(installed.visibleItems).hasSize(5)

    controller.updateSection(installed.copy(status = PluginSectionStatus.Loading(showingStaleContent = true)))
    installed = controller.state.value.section(PluginSectionId.Installed)
    assertThat(installed.count).isNull()
    assertThat(installed.visibleItems).hasSize(5)
  }

  @Test
  fun `section expansion changes clear selection`() {
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, itemCount = 5))

    controller.setSectionExpanded(PluginSectionId.Installed, true)
    assertThat(controller.state.value.selectedOccurrences).isEmpty()

    val lastOccurrence = occurrence(PluginSectionId.Installed, "plugin.5")
    controller.selectOccurrence(lastOccurrence)
    assertThat(controller.state.value.selectedOccurrence).isEqualTo(lastOccurrence)

    controller.setSectionExpanded(PluginSectionId.Installed, false)
    assertThat(controller.state.value.selectedOccurrences).isEmpty()

    val firstOccurrence = occurrence(PluginSectionId.Installed, "plugin.1")
    controller.selectOccurrence(firstOccurrence)
    controller.setSectionExpanded(PluginSectionId.Installed, false)
    assertThat(controller.state.value.selectedOccurrence).isEqualTo(firstOccurrence)
  }

  @Test
  fun `two item limit applies to every collapsed section and reveal expands it`() {
    val installed = section(PluginSectionId.Installed, itemCount = 3)
    val suggested = section(PluginSectionId.Suggested, itemCount = 3)
    val marketplace = section(PluginSectionId.Marketplace, itemCount = 3)
    val controller = UnifiedPluginsPageController(
      initialSections = listOf(installed, suggested, marketplace),
      collapsedItemLimit = 2,
    )
    controller.setSectionExpanded(PluginSectionId.Suggested, false)

    assertThat(controller.state.value.section(PluginSectionId.Installed).visibleItems).hasSize(2)
    assertThat(controller.state.value.section(PluginSectionId.Installed).canExpand).isTrue()
    assertThat(controller.state.value.section(PluginSectionId.Suggested).visibleItems).hasSize(2)

    val thirdInstalled = installed.occurrenceId(installed.items[2].pluginId)
    assertThat(controller.selectAndRevealOccurrence(thirdInstalled)).isTrue()
    assertThat(controller.state.value.section(PluginSectionId.Installed).visibleItems).hasSize(3)

    controller.setQuery(PluginsQueryState("query", "query", 1))
    controller.setSectionExpanded(PluginSectionId.Marketplace, false)
    assertThat(controller.state.value.section(PluginSectionId.Marketplace).visibleItems).hasSize(2)
  }

  @Test
  fun `large sections cap display without truncating inventory`() {
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, itemCount = 1_001))
    controller.setSectionExpanded(PluginSectionId.Installed, true)

    val limitedSection = controller.state.value.section(PluginSectionId.Installed)
    assertThat(limitedSection.items).hasSize(1_001)
    assertThat(limitedSection.count).isEqualTo(1_001)
    assertThat(limitedSection.displayItems).hasSize(PluginSectionState.MAX_DISPLAYED_ITEM_COUNT)
    assertThat(limitedSection.visibleItems).hasSize(PluginSectionState.MAX_DISPLAYED_ITEM_COUNT)
    assertThat(limitedSection.exceedsDisplayLimit).isTrue()

    val hiddenOccurrence = limitedSection.occurrenceId(limitedSection.items.last().pluginId)
    controller.selectOccurrence(hiddenOccurrence)
    assertThat(controller.state.value.selectedOccurrences).doesNotContain(hiddenOccurrence)
    assertThat(controller.selectAndRevealOccurrence(hiddenOccurrence)).isFalse()

    controller.updateSection(section(PluginSectionId.Installed, itemCount = PluginSectionState.MAX_DISPLAYED_ITEM_COUNT))
    val boundarySection = controller.state.value.section(PluginSectionId.Installed)
    assertThat(boundarySection.exceedsDisplayLimit).isFalse()
  }

  @Test
  fun `controller retains immutable source item snapshots`() {
    val items = (1..1_001).map { item("plugin.$it") }
    val controller = UnifiedPluginsPageController()

    controller.replaceSourceState(
      query = PluginsQueryState(),
      updatedSections = listOf(PluginSectionState(PluginSectionId.Installed, items = items)),
      mayEstablishSelection = false,
    )

    assertThat(controller.state.value.section(PluginSectionId.Installed).items).isSameAs(items)
  }

  @Test
  fun `expanded Bundled section publishes category actions only without a query`() {
    val firstLanguage = categoryItem("first.language", "Languages", enabled = false)
    val secondLanguage = categoryItem("second.language", "Languages", enabled = true)
    val firstTool = categoryItem("first.tool", "Tools", enabled = false)
    val secondTool = categoryItem("second.tool", "Tools", enabled = false)
    val controller = UnifiedPluginsPageController(
      listOf(PluginSectionState(PluginSectionId.Bundled, items = listOf(firstLanguage, secondLanguage, firstTool, secondTool)))
    )

    assertThat(controller.state.value.section(PluginSectionId.Bundled).categoryGroups).isEmpty()

    controller.setSectionExpanded(PluginSectionId.Bundled, true)

    val bundled = controller.state.value.section(PluginSectionId.Bundled)
    assertThat(bundled.categoryGroups.map { it.category to it.action }).containsExactly(
      "Languages" to BundledPluginCategoryAction.DisableAll,
      "Tools" to BundledPluginCategoryAction.EnableAll,
    )
    assertThat(bundled.categoryGroups.first().pluginIds).containsExactly(firstLanguage.pluginId, secondLanguage.pluginId)

    controller.updateSection(
      PluginSectionState(
        PluginSectionId.Bundled,
        items = listOf(firstLanguage,
                       secondLanguage.copy(rowInput = secondLanguage.rowInput?.copy(enabled = false)),
                       firstTool,
                       secondTool),
      )
    )
    val updatedBundled = controller.state.value.section(PluginSectionId.Bundled)
    assertThat(updatedBundled.categoryGroups.map(BundledPluginCategoryGroupState::action))
      .containsOnly(BundledPluginCategoryAction.EnableAll)

    controller.setQuery(PluginsQueryState("language", "language", 1))
    assertThat(controller.state.value.section(PluginSectionId.Bundled).categoryGroups).isEmpty()

    controller.setQuery(PluginsQueryState(revision = 2))
    assertThat(controller.state.value.section(PluginSectionId.Bundled).categoryGroups).hasSize(2)
  }

  @Test
  fun `Bundled category priority applies without a query and matches exact case`() {
    val language = categoryItem("language", "Languages", enabled = false)
    val lowerCaseTool = categoryItem("lower.tool", "tools", enabled = false)
    val tool = categoryItem("tool", "Tools", enabled = false)
    val controller = UnifiedPluginsPageController(
      initialSections = listOf(PluginSectionState(PluginSectionId.Bundled, items = listOf(language, lowerCaseTool, tool))),
      collapsedItemLimit = 2,
      priorityBundledCategories = setOf("Tools"),
    )

    assertThat(controller.state.value.section(PluginSectionId.Bundled).visibleItems.map(PluginItemState::pluginId))
      .containsExactly(tool.pluginId, language.pluginId)

    controller.setSectionExpanded(PluginSectionId.Bundled, true)

    assertThat(controller.state.value.section(PluginSectionId.Bundled).categoryGroups.map(BundledPluginCategoryGroupState::category))
      .containsExactly("Tools", "Languages", "tools")

    controller.setQuery(PluginsQueryState("tool", "tool", 1))

    val searchResult = controller.state.value.section(PluginSectionId.Bundled)
    assertThat(searchResult.items.map(PluginItemState::pluginId)).containsExactly(language.pluginId, lowerCaseTool.pluginId, tool.pluginId)
    assertThat(searchResult.categoryGroups).isEmpty()
  }

  @Test
  fun `collapsed Bundled section keeps errors ahead of provider priority`() {
    val languageError = categoryItem("language.error", "Languages", enabled = false, hasErrors = true)
    val tool = categoryItem("tool", "Tools", enabled = false)
    val editor = categoryItem("editor", "Editor", enabled = false)
    val controller = UnifiedPluginsPageController(
      initialSections = listOf(PluginSectionState(PluginSectionId.Bundled, items = listOf(languageError, editor, tool))),
      priorityBundledCategories = setOf("Tools"),
    )

    assertThat(controller.state.value.section(PluginSectionId.Bundled).visibleItems.map(PluginItemState::pluginId))
      .containsExactly(languageError.pluginId, tool.pluginId, editor.pluginId)
  }

  @Test
  fun `expanded Bundled section keeps error categories contiguous and ahead of provider priority`() {
    val frameworkError = categoryItem("framework.error", "Frameworks", enabled = false, hasErrors = true)
    val languageError = categoryItem("language.error", "Languages", enabled = false, hasErrors = true)
    val editor = categoryItem("editor", "Editor", enabled = false)
    val framework = categoryItem("framework", "Frameworks", enabled = false)
    val language = categoryItem("language", "Languages", enabled = false)
    val tool = categoryItem("tool", "Tools", enabled = false)
    val controller = UnifiedPluginsPageController(
      initialSections = listOf(
        PluginSectionState(
          PluginSectionId.Bundled,
          items = listOf(frameworkError, languageError, editor, framework, language, tool),
        )
      ),
      priorityBundledCategories = setOf("Frameworks", "Tools"),
    )

    controller.setSectionExpanded(PluginSectionId.Bundled, true)

    val bundled = controller.state.value.section(PluginSectionId.Bundled)
    assertThat(bundled.items.map(PluginItemState::pluginId)).containsExactly(
      frameworkError.pluginId,
      framework.pluginId,
      languageError.pluginId,
      language.pluginId,
      tool.pluginId,
      editor.pluginId,
    )
    assertThat(bundled.categoryGroups.map(BundledPluginCategoryGroupState::category))
      .containsExactly("Frameworks", "Languages", "Tools", "Editor")
  }

  @Test
  fun `Bundled category action includes plugins beyond the display limit`() {
    val items = (1..1_001).map { index ->
      PluginItemState(PluginId.getId("bundled.$index"), "Bundled $index", searchCategory = "Tools")
    }
    val controller = UnifiedPluginsPageController(listOf(PluginSectionState(PluginSectionId.Bundled, items = items)))

    controller.setSectionExpanded(PluginSectionId.Bundled, true)

    val bundled = controller.state.value.section(PluginSectionId.Bundled)
    assertThat(bundled.visibleItems).hasSize(PluginSectionState.MAX_DISPLAYED_ITEM_COUNT)
    assertThat(bundled.categoryGroups.single().pluginIds).containsExactlyElementsOf(items.map(PluginItemState::pluginId))
  }

  @Test
  fun `Installing auto-expands when it first becomes expandable`() {
    val controller = UnifiedPluginsPageController(listOf(section(PluginSectionId.Installing, itemCount = 3)))
    assertThat(controller.state.value.section(PluginSectionId.Installing).expanded).isFalse()

    controller.updateSection(section(PluginSectionId.Installing, itemCount = 4))

    val installing = controller.state.value.section(PluginSectionId.Installing)
    assertThat(installing.expanded).isTrue()
    assertThat(installing.visibleItems).hasSize(4)
  }

  @Test
  fun `two item limit expands Installing when the third item appears`() {
    val controller = UnifiedPluginsPageController(
      initialSections = listOf(section(PluginSectionId.Installing, itemCount = 2)),
      collapsedItemLimit = 2,
    )
    assertThat(controller.state.value.section(PluginSectionId.Installing).expanded).isFalse()

    controller.updateSection(section(PluginSectionId.Installing, itemCount = 3))

    val installing = controller.state.value.section(PluginSectionId.Installing)
    assertThat(installing.expanded).isTrue()
    assertThat(installing.visibleItems).hasSize(3)
  }

  @Test
  fun `manual Installing collapse survives later source replacements`() {
    val controller = UnifiedPluginsPageController(listOf(section(PluginSectionId.Installing, itemCount = 4)))
    controller.setSectionExpanded(PluginSectionId.Installing, false)

    controller.updateSection(section(PluginSectionId.Installing, itemCount = 5))
    controller.replaceSourceState(
      PluginsQueryState("query", "query", 1),
      listOf(section(PluginSectionId.Installing, itemCount = 6)),
      mayEstablishSelection = false,
    )

    val installing = controller.state.value.section(PluginSectionId.Installing)
    assertThat(installing.expanded).isFalse()
    assertThat(installing.visibleItems).hasSize(PluginSectionState.COLLAPSED_ITEM_LIMIT)
  }

  @Test
  fun `auto-expansion remains specific to Installing`() {
    val controller = UnifiedPluginsPageController(
      listOf(
        section(PluginSectionId.Installing, itemCount = 4),
        section(PluginSectionId.Installed, itemCount = 4),
      )
    )

    assertThat(controller.state.value.section(PluginSectionId.Installing).expanded).isTrue()
    assertThat(controller.state.value.section(PluginSectionId.Installed).expanded).isFalse()
  }

  @Test
  fun `expansion persists while a section is absent`() {
    val repositoryId = PluginSectionId.CustomRepository("repository")
    val controller = UnifiedPluginsPageController(
      initialSections = listOf(section(repositoryId, itemCount = 5)),
      initialQuery = PluginsQueryState("query", "query"),
    )
    controller.setSectionExpanded(repositoryId, true)

    controller.removeSection(repositoryId)
    assertThat(controller.state.value.sections.map { it.id }).doesNotContain(repositoryId)

    controller.updateSection(section(repositoryId, itemCount = 4))
    val repository = controller.state.value.section(repositoryId)
    assertThat(repository.expanded).isTrue()
    assertThat(repository.visibleItems).hasSize(4)
  }

  @Test
  fun `Suggested and Marketplace start expanded and preserve manual collapses`() {
    val controller = UnifiedPluginsPageController(
      listOf(
        section(PluginSectionId.Suggested, itemCount = 5),
        section(PluginSectionId.Marketplace, itemCount = 5),
      )
    )
    assertThat(controller.state.value.section(PluginSectionId.Suggested).expanded).isTrue()
    assertThat(controller.state.value.section(PluginSectionId.Suggested).visibleItems).hasSize(5)
    controller.setSectionExpanded(PluginSectionId.Suggested, false)

    controller.setQuery(PluginsQueryState("query", "query", 1))
    assertThat(controller.state.value.section(PluginSectionId.Marketplace).expanded).isTrue()
    assertThat(controller.state.value.section(PluginSectionId.Marketplace).visibleItems).hasSize(5)
    controller.setSectionExpanded(PluginSectionId.Marketplace, false)

    controller.setQuery(PluginsQueryState(revision = 2))
    assertThat(controller.state.value.section(PluginSectionId.Suggested).expanded).isFalse()
    assertThat(controller.state.value.section(PluginSectionId.Suggested).visibleItems)
      .hasSize(PluginSectionState.COLLAPSED_ITEM_LIMIT)
  }

  @Test
  fun `remote completion does not steal local selection`() {
    val controller = UnifiedPluginsPageController()
    controller.updateSections(
      listOf(
        section(PluginSectionId.Installed, "local.plugin"),
        section(PluginSectionId.Bundled),
      )
    )
    val localSelection = occurrence(PluginSectionId.Installed, "local.plugin")
    assertThat(controller.state.value.selectedOccurrence).isEqualTo(localSelection)
    controller.selectOccurrence(localSelection)

    controller.updateSection(section(PluginSectionId.Suggested, "remote.plugin"))
    assertThat(controller.state.value.selectedOccurrence).isEqualTo(localSelection)

    controller.updateSection(section(PluginSectionId.Installed))
    assertThat(controller.state.value.selectedOccurrence)
      .isEqualTo(occurrence(PluginSectionId.Suggested, "remote.plugin"))
  }

  @Test
  fun `remote completion does not replace explicitly cleared selection`() {
    val controller = UnifiedPluginsPageController(listOf(section(PluginSectionId.Installed, "local.plugin")))
    controller.selectOccurrence(null)

    controller.updateSection(section(PluginSectionId.Suggested, "remote.plugin"))

    assertThat(controller.state.value.selectedOccurrence).isNull()
  }

  @Test
  fun `installing publication preserves empty selection across later source updates`() {
    val controller = UnifiedPluginsPageController()
    val installing = section(PluginSectionId.Installing, "installing.plugin")

    controller.replaceSourceState(PluginsQueryState(), listOf(installing), mayEstablishSelection = false)
    assertThat(controller.state.value.selectedOccurrence).isNull()

    controller.updateSection(section(PluginSectionId.Installed, "installed.plugin"))
    assertThat(controller.state.value.selectedOccurrence).isNull()
  }

  @Test
  fun `query selection intent survives preserving source updates`() {
    val controller = UnifiedPluginsPageController(listOf(section(PluginSectionId.Installed, "old.plugin")))
    val query = PluginsQueryState("new", "new", 1)

    controller.replaceSourceState(
      query,
      listOf(section(PluginSectionId.Installed), section(PluginSectionId.Bundled)),
      mayEstablishSelection = false,
    )
    assertThat(controller.state.value.selectedOccurrence).isNull()

    controller.replaceSourceState(
      query,
      listOf(section(PluginSectionId.Installed, "new.plugin"), section(PluginSectionId.Bundled)),
      mayEstablishSelection = false,
    )

    assertThat(controller.state.value.selectedOccurrence)
      .isEqualTo(occurrence(PluginSectionId.Installed, "new.plugin"))
  }

  @Test
  fun `external selection expands a collapsed section to reveal its occurrence`() {
    val controller = UnifiedPluginsPageController()
    controller.updateSection(section(PluginSectionId.Installed, itemCount = 5))
    val occurrence = occurrence(PluginSectionId.Installed, "plugin.5")

    assertThat(controller.selectAndRevealOccurrence(occurrence)).isTrue()

    val state = controller.state.value
    assertThat(state.selectedOccurrence).isEqualTo(occurrence)
    assertThat(state.section(PluginSectionId.Installed).expanded).isTrue()
    assertThat(state.section(PluginSectionId.Installed).visibleItems).hasSize(5)
  }

  @Test
  fun `query revision switches marketplace family and clears selection`() {
    val controller = UnifiedPluginsPageController(
      listOf(
        section(PluginSectionId.Suggested, "suggested.plugin"),
        section(PluginSectionId.Marketplace, "marketplace.plugin"),
      )
    )
    assertThat(controller.state.value.selectedOccurrence)
      .isEqualTo(occurrence(PluginSectionId.Suggested, "suggested.plugin"))

    controller.setQuery(PluginsQueryState("  kotlin  ", "kotlin", 1))
    val marketplaceState = controller.state.value
    assertThat(marketplaceState.query).isEqualTo(PluginsQueryState("  kotlin  ", "kotlin", 1))
    assertThat(marketplaceState.sections.map { it.id }).contains(PluginSectionId.Marketplace).doesNotContain(PluginSectionId.Suggested)
    assertThat(marketplaceState.selectedOccurrence).isNull()

    controller.updateSection(section(PluginSectionId.Marketplace, "marketplace.plugin"))
    assertThat(controller.state.value.selectedOccurrence)
      .isEqualTo(occurrence(PluginSectionId.Marketplace, "marketplace.plugin"))

    controller.setQuery(PluginsQueryState(revision = 2))
    val suggestedState = controller.state.value
    assertThat(suggestedState.query.revision).isEqualTo(2)
    assertThat(suggestedState.sections.map { it.id }).contains(PluginSectionId.Suggested).doesNotContain(PluginSectionId.Marketplace)
    assertThat(suggestedState.selectedOccurrence).isNull()
  }

  @Test
  fun `Internal section follows Suggested and Marketplace`() {
    val controller = UnifiedPluginsPageController(
      listOf(
        section(PluginSectionId.Suggested, "suggested.plugin"),
        section(PluginSectionId.Marketplace, "marketplace.plugin"),
        PluginSectionState(PluginSectionId.Internal, title = "Managed plugins", items = listOf(item("internal.plugin"))),
        section(PluginSectionId.Bundled, "bundled.plugin"),
        section(PluginSectionId.Installed, "installed.plugin"),
      )
    )

    assertThat(controller.state.value.sections.map(PluginSectionState::id)).containsSubsequence(
      PluginSectionId.Installed,
      PluginSectionId.Bundled,
      PluginSectionId.Suggested,
      PluginSectionId.Internal,
    )

    controller.setQuery(PluginsQueryState("query", "query", 1))
    assertThat(controller.state.value.sections.map(PluginSectionState::id)).containsSubsequence(
      PluginSectionId.Installed,
      PluginSectionId.Bundled,
      PluginSectionId.Marketplace,
      PluginSectionId.Internal,
    )
  }

  @Test
  fun `query and local section results publish atomically`() {
    val controller = UnifiedPluginsPageController(listOf(section(PluginSectionId.Installed, "old.plugin")))

    controller.replaceSourceState(
      PluginsQueryState("new", "new", 1),
      listOf(section(PluginSectionId.Installed, "new.plugin"), section(PluginSectionId.Bundled)),
      mayEstablishSelection = true,
    )

    val state = controller.state.value
    assertThat(state.query.normalizedQuery).isEqualTo("new")
    assertThat(state.section(PluginSectionId.Installed).items.map { it.pluginId.idString }).containsExactly("new.plugin")
  }

  private fun UnifiedPluginsPageState.section(id: PluginSectionId): PluginSectionState {
    return sections.single { it.id == id }
  }

  private fun section(id: PluginSectionId, vararg pluginIds: String): PluginSectionState {
    return PluginSectionState(id = id, items = pluginIds.map(::item))
  }

  private fun section(id: PluginSectionId, itemCount: Int): PluginSectionState {
    return section(id, *(1..itemCount).map { "plugin.$it" }.toTypedArray())
  }

  private fun item(pluginId: String): PluginItemState {
    return PluginItemState(PluginId.getId(pluginId), pluginId)
  }

  private fun categoryItem(pluginId: String, category: String, enabled: Boolean, hasErrors: Boolean = false): PluginItemState {
    val id = PluginId.getId(pluginId)
    val model = PluginDto(pluginId, id)
    return PluginItemState(
      pluginId = id,
      name = pluginId,
      modelHandle = PluginItemModelHandle(model),
      rowInput = PluginRowInput(
        installedPlugin = model,
        installationState = PluginInstallationState(true),
        errors = if (hasErrors) listOf(HtmlChunk.text("broken")) else emptyList(),
        updateDescriptor = null,
        enabled = enabled,
        restrictedByProduct = false,
      ),
      searchCategory = category,
    )
  }

  private fun occurrence(sectionId: PluginSectionId, pluginId: String): PluginOccurrenceId {
    return PluginOccurrenceId(sectionId, PluginId.getId(pluginId))
  }
}
