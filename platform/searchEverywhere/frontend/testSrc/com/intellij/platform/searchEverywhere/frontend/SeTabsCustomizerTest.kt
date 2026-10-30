// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.ide.actions.searcheverywhere.SearchHistoryList
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SePreviewInfo
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.SeSessionImpl
import com.intellij.platform.searchEverywhere.frontend.vm.SeDummyTabVm
import com.intellij.platform.searchEverywhere.frontend.vm.SePopupVm
import com.intellij.platform.searchEverywhere.frontend.vm.SeTabsModel
import com.intellij.platform.searchEverywhere.providers.SeLegacyContributors
import com.intellij.platform.searchEverywhere.utils.suspendLazy
import com.intellij.platform.util.coroutines.childScope
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Checks that [SePopupVm] applies [SeTabsCustomizer] to its tabs.
 *
 * The customizer can rename a tab, change the priority of a tab, or hide a tab.
 */
@TestApplication
class SeTabsCustomizerTest {
  @TestDisposable
  lateinit var disposable: Disposable

  @BeforeEach
  fun setUp() {
    ApplicationManager.getApplication().replaceService(SeTabsCustomizer::class.java, TestTabsCustomizer(), disposable)
  }

  @Test
  fun `customizer changes the tabs of the popup`(): Unit = timeoutRunBlocking {
    val popupScope = childScope("SeTabsCustomizerTest")
    try {
      val tabs = listOf(TestTab("a", 300), TestTab(RENAMED_ID, 200), TestTab(HIDDEN_ID, 100), TestTab(MOVED_ID, 50))
      val vm = createPopupVm(popupScope, tabs)

      assertEquals(EXPECTED_TABS, vm.tabsModel.describe())
    }
    finally {
      popupScope.cancel()
    }
  }

  @Test
  fun `customizer changes the deferred tabs of the popup`(): Unit = timeoutRunBlocking {
    val popupScope = childScope("SeTabsCustomizerTest")
    try {
      val deferredTabs = listOf(TestTab(RENAMED_ID, 200), TestTab(HIDDEN_ID, 100), TestTab(MOVED_ID, 50))
      val vm = createPopupVm(popupScope, listOf(TestTab("a", 300)), deferredTabs, dummyTabId = "dummy")

      // The popup removes the dummy tabs after it processed every deferred tab.
      val model = vm.tabsModelFlow.first { !it.contains("dummy") }

      assertEquals(EXPECTED_TABS, model.describe())
    }
    finally {
      popupScope.cancel()
    }
  }

  private fun createPopupVm(
    scope: CoroutineScope,
    tabs: List<SeTab>,
    deferredTabs: List<SeTab> = emptyList(),
    dummyTabId: String? = null,
  ): SePopupVm {
    return SePopupVm(
      coroutineScope = scope,
      session = SeSessionImpl(UUID.randomUUID().toString()),
      project = null,
      tabs = tabs,
      initialDummyTabVms = listOfNotNull(dummyTabId).map { SeDummyTabVm(it, SeTabInfo(0, it)) },
      deferredTabs = deferredTabs.map { tab -> suspendLazy { tab } },
      adaptedTabs = suspendLazy { emptyList() },
      initialSearchPattern = "",
      initialTabId = tabs.first().id,
      historyList = SearchHistoryList(false),
      availableLegacyContributors = SeLegacyContributors(emptyMap(), emptyMap()),
      onShowFindToolWindow = {},
      closePopupHandler = {},
    )
  }

  /** Describes each tab as "id:name:priority", in the order of the tab strip. */
  private fun SeTabsModel.describe(): List<String> = sortedTabVms.map { "${it.tabId}:${it.name}:${it.priority}" }

  private companion object {
    const val RENAMED_ID = "renamed"
    const val HIDDEN_ID = "hidden"
    const val MOVED_ID = "moved"

    /** [MOVED_ID] goes first with its new priority, [RENAMED_ID] gets its new name, and [HIDDEN_ID] is not there. */
    val EXPECTED_TABS = listOf("$MOVED_ID:$MOVED_ID:1000", "a:a:300", "$RENAMED_ID:Renamed:200")
  }

  private class TestTabsCustomizer : SeTabsCustomizer {
    override fun customizeTabInfo(tabId: String, info: SeTabInfo): SeTabInfo? = when (tabId) {
      RENAMED_ID -> SeTabInfo(info.priority, "Renamed")
      MOVED_ID -> SeTabInfo(1000, info.name)
      HIDDEN_ID -> null
      else -> info
    }
  }

  private class TestTab(override val id: String, override val priority: Int) : SeTab {
    override val name: String get() = id

    override fun getItems(params: SeParams): Flow<SeResultEvent> = emptyFlow()
    override suspend fun getFilterEditor(): SeFilterEditor? = null
    override suspend fun itemSelected(item: SeItemData, modifiers: Int, searchText: String): Boolean = false
    override suspend fun canBeShownInFindResults(): Boolean = false
    override suspend fun performExtendedAction(item: SeItemData): Boolean = false
    override suspend fun isPreviewEnabled(): Boolean = false
    override suspend fun getPreviewInfo(itemData: SeItemData): SePreviewInfo? = null
    override suspend fun isExtendedInfoEnabled(): Boolean = false
    override suspend fun isCommandsSupported(): Boolean = false
    override fun dispose() {}
  }
}
