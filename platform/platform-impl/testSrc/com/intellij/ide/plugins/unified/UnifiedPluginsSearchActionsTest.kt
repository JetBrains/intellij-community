// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins.unified

import com.intellij.ide.plugins.MarketplaceTabSearchSortByOptions
import com.intellij.openapi.actionSystem.CheckedActionGroup
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.Toggleable
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

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
      "Tag", "Repository", "Installed", "Vendor", "Category",
      "Update available", "Enabled", "Disabled", "Invalid", "Updated bundled",
    )

    val vendorGroup = rootActions[3] as DefaultActionGroup
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

    val categoryGroup = rootActions[4] as DefaultActionGroup
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
          "Tag", "Installed", "Vendor", "Category",
          "Update available", "Enabled", "Disabled", "Invalid", "Updated bundled",
        )
    }
  }

  @Test
  fun `tag group contains every available tag`() {
    val tags = (1..35).map { index -> "Tag $index" }
    val state = UnifiedPluginsSearchControlsState(
      options = UnifiedPluginFilterOptions(tags = tags),
    )

    val group = createUnifiedPluginFilterActionGroup(state) {}
    val tagGroup = group.getChildren(TestActionEvent.createTestEvent()).first() as DefaultActionGroup

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
    val repositoryGroup = group.getChildren(TestActionEvent.createTestEvent())[1] as DefaultActionGroup
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

  private fun assertPresentationSelection(action: ToggleAction, selected: Boolean) {
    val event = TestActionEvent.createTestEvent(action)
    action.update(event)
    assertThat(Toggleable.isSelected(event.presentation)).isEqualTo(selected)
  }
}
