// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.target

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.platform.searchEverywhere.SeExtendedInfoBuilder
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.providers.target.selection.SeTargetItemSelectionProcessor
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * Enter on a target that is a plain [NavigationItem], not a PSI element, must navigate and close the popup.
 *
 * Every LSP-based Classes/Symbols result is such an item. The fallback selection processor used to cast the
 * `ItemWithPresentation` wrapper instead of the wrapped item, so it answered null and the popup stayed open.
 */
@TestApplication
class SeDefaultTargetItemSelectionProcessorTest {

  @Test
  fun enterOnNonPsiNavigationItemNavigatesAndClosesPopup() {
    val target = RecordingNavigationItem()
    val item = SeTargetPresentableItem(
      rawItem = target,
      matchers = null,
      weight = 0,
      presentation = TargetPresentation.builder("finishCommitInWriteAction").presentation(),
      extendedInfo = SeExtendedInfoBuilder().build(),
      isMultiSelectionSupported = false,
      isExactMatch = false,
    )

    val closePopup = runBlocking {
      withTimeout(30.seconds) {
        SeTargetItemSelectionProcessor.process(item, StubItemsProvider(), 0, "finishCommitInWriteAction")
      }
    }

    assertEquals(true, closePopup, "the default selection processor must handle a plain NavigationItem")
    assertTrue(target.navigated, "the item must be navigated")
    assertEquals(true, target.requestFocus, "navigation must request focus, like the legacy contributor did")
  }

  private class RecordingNavigationItem : NavigationItem {
    var navigated: Boolean = false
    var requestFocus: Boolean? = null

    override fun getName(): String = "finishCommitInWriteAction"
    override fun getPresentation(): ItemPresentation? = null
    override fun navigate(requestFocus: Boolean) {
      navigated = true
      this.requestFocus = requestFocus
    }
    override fun canNavigate(): Boolean = true
    override fun canNavigateToSource(): Boolean = true
  }

  private class StubItemsProvider : SeItemsProvider {
    override val id: String = SeProviderIdUtils.SYMBOLS_ID
    override val displayName: String = "Symbols"
    override suspend fun collectItems(params: SeParams, collector: SeItemsProvider.Collector) {}
    override suspend fun itemSelected(item: SeItem, modifiers: Int, searchText: String): Boolean = false
    override suspend fun canBeShownInFindResults(): Boolean = false
    override fun addDataForItem(item: SeItem, sink: DataSink) {}
    override fun dispose() {}
  }
}
