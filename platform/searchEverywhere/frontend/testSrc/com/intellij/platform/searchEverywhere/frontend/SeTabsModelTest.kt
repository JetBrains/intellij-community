// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.platform.searchEverywhere.frontend.tabs.actions.SeActionsTab
import com.intellij.platform.searchEverywhere.frontend.tabs.all.SeAllTab
import com.intellij.platform.searchEverywhere.frontend.tabs.classes.SeClassesTab
import com.intellij.platform.searchEverywhere.frontend.tabs.files.SeFilesTab
import com.intellij.platform.searchEverywhere.frontend.tabs.symbols.SeSymbolsTab
import com.intellij.platform.searchEverywhere.frontend.tabs.text.SeTextTab
import com.intellij.platform.searchEverywhere.frontend.vm.SeDummyTabVm
import com.intellij.platform.searchEverywhere.frontend.vm.SeTabVm
import com.intellij.platform.searchEverywhere.frontend.vm.SeTabsModel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SeTabsModel] tab-selection, which tracks the selected tab by id (not by index).
 *
 * Regression: opening "Find Action…" sometimes selected the Text tab instead of Actions. A plugin (CodeSearch,
 * priority ~820) contributes a tab that initializes slowly and is merged into the model asynchronously, inserting
 * itself between Symbols (850) and Actions (800) and shifting Actions's index by one. Because Actions (800) and
 * Text (250) are immediate neighbors in the sorted strip, an index-based selection could drift onto Text.
 * Tracking selection by id keeps it stable across such rebuilds.
 */
class SeTabsModelTest {
  private fun tab(id: String, priority: Int): SeTabVm = SeDummyTabVm(id, SeTabInfo(priority, id))

  // Sorted (descending priority): All, Classes, Files, Symbols, Actions, Text
  private fun mainTabs(): List<SeTabVm> = listOf(
    tab(SeAllTab.ID, Int.MAX_VALUE),
    tab(SeClassesTab.ID, 950),
    tab(SeFilesTab.ID, 900),
    tab(SeSymbolsTab.ID, 850),
    tab(SeActionsTab.ID, 800),
    tab(SeTextTab.ID, 250),
  )

  private fun codeSearchTab(): SeTabVm = tab("CodeSearchEverywhereContributor", 820)

  @Test
  fun selectedTabIsPreservedWhenHigherPriorityTabArrivesLate() {
    val model = SeTabsModel(mainTabs(), SeActionsTab.ID)
    assertEquals(SeActionsTab.ID, model.selectedTab.tabId)
    assertEquals(4, model.sortedTabVms.indexOfFirst { it.tabId == SeActionsTab.ID })

    // CodeSearch (820) sorts between Symbols (850) and Actions (800), shifting Actions 4 -> 5.
    val rebuilt = model.newModelWithReplacedTab(listOf(codeSearchTab()))

    assertEquals(5, rebuilt.sortedTabVms.indexOfFirst { it.tabId == SeActionsTab.ID })
    assertEquals(SeActionsTab.ID, rebuilt.selectedTab.tabId)
    assertEquals(SeActionsTab.ID, rebuilt.selectedTabIdFlow.value)
    // Regression guard: selection must not drift to the neighboring Text tab.
    assertNotEquals(SeTextTab.ID, rebuilt.selectedTab.tabId)
  }

  @Test
  fun showTabSelectsById() {
    val model = SeTabsModel(mainTabs(), SeActionsTab.ID)
    model.showTab(SeTextTab.ID)
    assertEquals(SeTextTab.ID, model.selectedTab.tabId)
  }

  @Test
  fun showTabIgnoresUnknownId() {
    val model = SeTabsModel(mainTabs(), SeActionsTab.ID)
    model.showTab("NonExistentTabId")
    assertEquals(SeActionsTab.ID, model.selectedTab.tabId)
  }

  @Test
  fun fallsBackToFirstTabWhenSelectedIdIsAbsent() {
    val model = SeTabsModel(mainTabs(), "NonExistentTabId")
    assertEquals(SeAllTab.ID, model.selectedTab.tabId)
    assertEquals(SeAllTab.ID, model.selectedTabIdFlow.value)
  }

  @Test
  fun nextAndPreviousTabWrapAroundByPriorityOrder() {
    val model = SeTabsModel(mainTabs(), SeTextTab.ID) // last tab (lowest priority)
    model.selectNextTab()
    assertEquals(SeAllTab.ID, model.selectedTab.tabId) // wraps to the first tab
    model.selectPreviousTab()
    assertEquals(SeTextTab.ID, model.selectedTab.tabId) // wraps back to the last tab
  }
}
