// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.MarketplaceTabSearchSortByOptions
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.CheckedActionGroup
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.PresentationFactory
import com.intellij.openapi.actionSystem.impl.Utils
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
internal class UnifiedPluginsSearchActionsTest {
  @Test
  fun `filter actions reflect snapshot and emit semantic intents`() {
    val intents = ArrayList<UnifiedPluginSearchControlIntent>()
    val state = UnifiedPluginsSearchControlsState(
      options = UnifiedPluginFilterOptions(
        vendors = listOf("Acme", "JetBrains"),
        categories = listOf("Programming Language"),
        tags = listOf("Developer Tools"),
        repositories = listOf("repository"),
      ),
      selectedVendors = setOf("JetBrains"),
      selectedCategories = setOf("Programming Language"),
      selectedInstalledFilter = UnifiedPluginInstalledFilter.Disabled,
    )
    val group = createUnifiedPluginFilterActionGroup(state, intents::add)
    val rootActions = group.getChildren(TestActionEvent.createTestEvent())
    assertThat(rootActions.map { (it as? Separator)?.text ?: it.templatePresentation.text }).containsExactly(
      "Tag", "Vendor", "Repository", "Installed", "Category",
      "Update available", "Enabled", "Disabled", "Invalid", "Updated bundled",
    )

    val vendorGroup = rootActions[1] as ActionGroup
    val vendorActions = vendorGroup.getChildren(TestActionEvent.createTestEvent())
    assertThat(vendorActions.map { it.templatePresentation.text }).containsExactly("Acme", "JetBrains")
    val jetBrains = vendorActions[1] as ToggleAction
    val event = TestActionEvent.createTestEvent(jetBrains)
    jetBrains.update(event)
    assertThat(jetBrains.isSelected(event)).isTrue()
    assertThat(Toggleable.isSelected(event.presentation)).isTrue()

    jetBrains.actionPerformed(event)

    assertThat(intents).containsExactly(
      UnifiedPluginSearchControlIntent.ToggleAttribute(
        UnifiedPluginQueryAttribute.Vendor,
        "JetBrains",
        selected = false,
      )
    )
    assertThat(jetBrains.isSelected(event)).isFalse()
    assertThat(Toggleable.isSelected(event.presentation)).isFalse()

    jetBrains.actionPerformed(event)

    assertThat(intents.last()).isEqualTo(
      UnifiedPluginSearchControlIntent.ToggleAttribute(
        UnifiedPluginQueryAttribute.Vendor,
        "JetBrains",
        selected = true,
      )
    )
    assertThat(jetBrains.isSelected(event)).isTrue()
    assertThat(Toggleable.isSelected(event.presentation)).isTrue()

    val categoryGroup = rootActions[4] as ActionGroup
    val categoryAction = categoryGroup.getChildren(TestActionEvent.createTestEvent()).single() as ToggleAction
    val categoryEvent = TestActionEvent.createTestEvent(categoryAction)
    assertThat(categoryAction.isSelected(categoryEvent)).isTrue()

    categoryAction.actionPerformed(categoryEvent)

    assertThat(intents.last()).isEqualTo(
      UnifiedPluginSearchControlIntent.ToggleAttribute(
        UnifiedPluginQueryAttribute.Category,
        "Programming Language",
        selected = false,
      )
    )
    assertThat(categoryAction.isSelected(categoryEvent)).isFalse()
    val installedActions = group.getChildren(TestActionEvent.createTestEvent()).takeLast(5)
    val disabled = installedActions[2] as ToggleAction
    assertThat(disabled.isSelected(TestActionEvent.createTestEvent(disabled))).isTrue()
  }

  @Test
  fun `repository filter is hidden without repository values`() {
    listOf(false, true).forEach { repositoriesLoading ->
      val state = UnifiedPluginsSearchControlsState(
        options = UnifiedPluginFilterOptions(repositoriesLoading = repositoriesLoading)
      )

      val group = createUnifiedPluginFilterActionGroup(state) {}
      assertThat(group.getChildren(TestActionEvent.createTestEvent()).map { (it as? Separator)?.text ?: it.templatePresentation.text })
        .containsExactly(
          "Tag", "Vendor", "Installed", "Category",
          "Update available", "Enabled", "Disabled", "Invalid", "Updated bundled",
        )
    }
  }

  @Test
  fun `local vendor actions remain enabled while the catalog loads`() {
    val intents = ArrayList<UnifiedPluginSearchControlIntent>()
    val state = UnifiedPluginsSearchControlsState(
      options = UnifiedPluginFilterOptions(vendors = listOf("Local Vendor"), facetValuesLoading = true),
    )
    val group = createUnifiedPluginFilterActionGroup(state, intents::add)
    val vendorGroup = group.getChildren(TestActionEvent.createTestEvent())[1] as ActionGroup
    val vendor = vendorGroup.getChildren(TestActionEvent.createTestEvent()).single() as ToggleAction
    val event = TestActionEvent.createTestEvent(vendor)
    vendor.update(event)

    assertThat(event.presentation.isEnabled).isTrue()

    vendor.actionPerformed(event)

    assertThat(intents).containsExactly(
      UnifiedPluginSearchControlIntent.ToggleAttribute(UnifiedPluginQueryAttribute.Vendor, "Local Vendor", selected = true)
    )
  }

  @Test
  fun `large vendor catalogs update without EDT access`(): Unit = timeoutRunBlocking {
    val vendors = (1..6_000).map { index -> "Vendor $index" }
    val state = UnifiedPluginsSearchControlsState(
      options = UnifiedPluginFilterOptions(vendors = vendors, categories = listOf("Tools"), tags = listOf("Theme")),
      selectedVendors = setOf(vendors.last()),
    )
    val group = createUnifiedPluginFilterActionGroup(state) {}
    val event = TestActionEvent.createTestEvent()
    val vendorGroup = group.getChildren(event)[1] as ActionGroup
    assertThat(vendorGroup.getChildren(event)).hasSize(vendors.size)

    withContext(Dispatchers.Default) {
      assertBackgroundUpdates(group)
      assertBackgroundUpdates(createUnifiedPluginSortActionGroup(state) {})
      assertBackgroundUpdates(createUnifiedPluginFilterActionGroup(UnifiedPluginsSearchControlsState()) {})

      val selected = vendorGroup.getChildren(event).last() as ToggleAction
      assertThat(selected.isSelected(TestActionEvent.createTestEvent(selected))).isTrue()
    }
  }

  @Test
  fun `opening Filter does not read choices from closed submenus`(): Unit = timeoutRunBlocking {
    val vendorReads = AtomicInteger()
    val tagReads = AtomicInteger()
    val vendorCount = 6_000
    val state = UnifiedPluginsSearchControlsState(
      options = UnifiedPluginFilterOptions(
        vendors = countedFacetValues("Vendor", vendorCount, vendorReads),
        tags = countedFacetValues("Tag", 200, tagReads),
      ),
      selectedVendors = setOf("Vendor ${vendorCount - 1}"),
    )
    val group = createUnifiedPluginFilterActionGroup(state) {}
    assertThat(vendorReads.get()).isZero()
    assertThat(tagReads.get()).isZero()

    val rootActions = expandPopup(group)

    assertThat(vendorReads.get()).isZero()
    assertThat(tagReads.get()).isZero()
    assertThat(rootActions[1].templatePresentation.text).isEqualTo("Vendor")
    val vendorGroup = rootActions[1] as ActionGroup
    val vendorActions = expandPopup(vendorGroup)

    assertThat(vendorActions).hasSize(vendorCount)
    assertThat(vendorReads.get()).isEqualTo(vendorCount)
    assertThat(tagReads.get()).isZero()
    val selected = vendorActions.last() as ToggleAction
    assertThat(selected.isSelected(TestActionEvent.createTestEvent(selected))).isTrue()
    val event = TestActionEvent.createTestEvent(vendorGroup)
    val children = vendorGroup.getChildren(event)
    assertThat(vendorGroup.getChildren(event)).isSameAs(children)
    assertThat(vendorReads.get()).isEqualTo(vendorCount)
  }

  @Test
  fun `empty facet submenus keep visible disabled placeholders`(): Unit = timeoutRunBlocking {
    for (loading in listOf(false, true)) {
      val state = UnifiedPluginsSearchControlsState(options = UnifiedPluginFilterOptions(facetValuesLoading = loading))
      val rootActions = expandPopup(createUnifiedPluginFilterActionGroup(state) {})
      val facetGroups = rootActions.filterIsInstance<ActionGroup>()
      assertThat(facetGroups.map { it.templatePresentation.text }).containsExactly("Tag", "Vendor", "Category")

      for (facetGroup in facetGroups) {
        val presentations = PresentationFactory()
        val placeholder = expandPopup(facetGroup, presentations).single()
        assertThat(presentations.getPresentation(placeholder).isEnabled).isFalse()
        assertThat(placeholder.templatePresentation.text).isEqualTo(if (loading) "Loading options\u2026" else "No options")
      }
    }
  }

  @Test
  fun `tag group contains every available tag`() {
    val tags = (1..35).map { index -> "Tag $index" }
    val state = UnifiedPluginsSearchControlsState(
      options = UnifiedPluginFilterOptions(tags = tags),
    )

    val group = createUnifiedPluginFilterActionGroup(state) {}
    val tagGroup = group.getChildren(TestActionEvent.createTestEvent()).first() as ActionGroup

    assertThat(tagGroup.getChildren(TestActionEvent.createTestEvent()).map { it.templatePresentation.text })
      .containsExactlyElementsOf(tags)
  }

  @Test
  fun `installed actions share an optional radio selection`() {
    val intents = ArrayList<UnifiedPluginSearchControlIntent>()
    val state = UnifiedPluginsSearchControlsState(
      selectedInstalledFilter = UnifiedPluginInstalledFilter.Disabled,
    )
    val group = createUnifiedPluginFilterActionGroup(state, intents::add)
    val actions = group.getChildren(TestActionEvent.createTestEvent()).takeLast(5).map { it as ToggleAction }
    val enabled = actions[1]
    val disabled = actions[2]

    assertThat(group).isInstanceOf(CheckedActionGroup::class.java)
    assertThat(disabled.isSelected(TestActionEvent.createTestEvent(disabled))).isTrue()

    enabled.actionPerformed(TestActionEvent.createTestEvent(enabled))

    assertThat(enabled.isSelected(TestActionEvent.createTestEvent(enabled))).isTrue()
    assertThat(disabled.isSelected(TestActionEvent.createTestEvent(disabled))).isFalse()
    assertPresentationSelection(enabled, selected = true)
    assertPresentationSelection(disabled, selected = false)
    assertThat(intents).containsExactly(
      UnifiedPluginSearchControlIntent.ToggleInstalledFilter(UnifiedPluginInstalledFilter.Enabled, true)
    )

    enabled.actionPerformed(TestActionEvent.createTestEvent(enabled))

    assertThat(actions.count { it.isSelected(TestActionEvent.createTestEvent(it)) }).isZero()
    assertThat(intents.last()).isEqualTo(
      UnifiedPluginSearchControlIntent.ToggleInstalledFilter(UnifiedPluginInstalledFilter.Enabled, false)
    )
  }

  @Test
  fun `sort actions use radio group order and emit one selection`() {
    val intents = ArrayList<UnifiedPluginSearchControlIntent>()
    val state = UnifiedPluginsSearchControlsState(effectiveSort = MarketplaceTabSearchSortByOptions.RATING)

    val group = createUnifiedPluginSortActionGroup(state, intents::add)
    val actions = group.getChildren(TestActionEvent.createTestEvent())

    assertThat(group).isInstanceOf(CheckedActionGroup::class.java)
    assertThat(actions.map { it.templatePresentation.text }).containsExactly(
      "Relevance", "Downloads", "Rating", "Name", "Updated"
    )
    assertThat((actions[2] as ToggleAction).isSelected(TestActionEvent.createTestEvent(actions[2]))).isTrue()

    val rating = actions[2] as ToggleAction
    val name = actions[3] as ToggleAction
    name.actionPerformed(TestActionEvent.createTestEvent(name))

    assertThat(intents).containsExactly(
      UnifiedPluginSearchControlIntent.SelectSort(MarketplaceTabSearchSortByOptions.NAME)
    )
    assertThat(rating.isSelected(TestActionEvent.createTestEvent(rating))).isFalse()
    assertThat(name.isSelected(TestActionEvent.createTestEvent(name))).isTrue()
    assertPresentationSelection(rating, selected = false)
    assertPresentationSelection(name, selected = true)

    name.actionPerformed(TestActionEvent.createTestEvent(name))

    assertThat(name.isSelected(TestActionEvent.createTestEvent(name))).isTrue()
    assertThat(intents).hasSize(1)
  }

  @Test
  fun `long repository action exposes full value as description and popup tooltip`() {
    val repository = "https://plugins.example.test/" + "very-long-segment/".repeat(8) + "plugins.xml"
    val state = UnifiedPluginsSearchControlsState(
      options = UnifiedPluginFilterOptions(repositories = listOf(repository)),
      selectedRepositories = setOf(repository),
    )

    val group = createUnifiedPluginFilterActionGroup(state) {}
    val repositoryGroup = group.getChildren(TestActionEvent.createTestEvent())[2] as ActionGroup
    val action = repositoryGroup.getChildren(TestActionEvent.createTestEvent()).single()

    assertThat(action.templatePresentation.text)
      .startsWith(repository.take(10))
      .endsWith(repository.takeLast(10))
      .contains("...")
      .hasSizeLessThan(repository.length)
    assertThat(action.templatePresentation.description).isEqualTo(repository)
    assertThat(action.templatePresentation.getClientProperty(ActionUtil.TOOLTIP_TEXT)).isEqualTo(repository)
    assertThat((action as ToggleAction).isSelected(TestActionEvent.createTestEvent(action))).isTrue()
  }

  private fun countedFacetValues(prefix: String, count: Int, reads: AtomicInteger): List<String> {
    return object : AbstractList<String>() {
      override val size: Int = count

      override fun get(index: Int): String {
        reads.incrementAndGet()
        return "$prefix $index"
      }
    }
  }

  private suspend fun expandPopup(
    group: ActionGroup,
    presentations: PresentationFactory = PresentationFactory(),
  ): List<AnAction> = withContext(Dispatchers.EDT) {
    Utils.expandActionGroupSuspend(group, presentations, DataContext.EMPTY_CONTEXT, "UnifiedPlugins.SearchToolbar", ActionUiKind.POPUP, false)
  }

  private fun assertBackgroundUpdates(action: AnAction) {
    assertThat(action.actionUpdateThread).isEqualTo(ActionUpdateThread.BGT)
    val event = TestActionEvent.createTestEvent(action)
    action.update(event)
    if (action is ActionGroup) action.getChildren(event).forEach(::assertBackgroundUpdates)
  }

  private fun assertPresentationSelection(action: ToggleAction, selected: Boolean) {
    val event = TestActionEvent.createTestEvent(action)
    action.update(event)
    assertThat(Toggleable.isSelected(event.presentation)).isEqualTo(selected)
  }
}
