// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.rpc.ThrottledItems
import com.intellij.ide.rpc.ThrottledOneItem
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeItemDataFactory
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeResultAddedEvent
import com.intellij.platform.searchEverywhere.SeResultEndEvent
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.SeSessionImpl
import com.intellij.platform.searchEverywhere.frontend.SeSearchStatePublisher
import com.intellij.platform.searchEverywhere.frontend.vm.SeSearchContext
import com.intellij.platform.searchEverywhere.impl.SeItemRegistry
import com.intellij.platform.searchEverywhere.presentations.SeAdaptedItemEmptyPresentation
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.toProviderId
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.swing.DefaultListSelectionModel

/**
 * Checks when [SeResultListModel.addFromThrottledEvent] makes an invalid result list valid.
 */
@TestApplication
class SeResultListModelTest {
  private val session = SeSessionImpl(UUID.randomUUID().toString())
  private val model = SeResultListModel(SeSearchStatePublisher()) { DefaultListSelectionModel() }
  private val searchContext = SeSearchContext("search", SeProviderIdUtils.FILES_ID, "pattern", emptyFlow())

  @AfterEach
  fun closeSession() {
    SeItemRegistry.getInstance().closeSession(session)
  }

  @Test
  fun `an end event keeps the invalid list invalid`() {
    model.invalidate()

    model.add(ThrottledOneItem(SeResultEndEvent(SeProviderIdUtils.FILES_ID.toProviderId())))

    // A valid empty list stops the search: the tab loads more results only for an invalid list or a list scrolled to its end.
    assertFalse(model.isValid)
  }

  @Test
  fun `the first item after an end event makes the list valid`(): Unit = runBlocking {
    model.invalidate()
    model.add(ThrottledOneItem(SeResultEndEvent(SeProviderIdUtils.FILES_ID.toProviderId())))

    model.add(ThrottledOneItem(SeResultAddedEvent(itemData("file"))))

    assertTrue(model.isValid)
    assertEquals(listOf("file"), model.keys())
  }

  private fun SeResultListModel.add(event: ThrottledItems<SeResultEvent>) {
    addFromThrottledEvent(searchContext, event, isZeroOffset = false)
  }

  /** Returns the keys of the items in the list, from the top. */
  private fun SeResultListModel.keys(): List<String> =
    elements().toList().mapNotNull { (it as? SeResultListItemRow)?.item?.fetchItemIfExists() as? TestItem }.map { it.key }

  private suspend fun itemData(key: String): SeItemData =
    checkNotNull(SeItemDataFactory().createItemData(session, UUID.randomUUID().toString(), TestItem(key), SeProviderIdUtils.FILES_ID.toProviderId(), emptyMap()))

  private class TestItem(val key: String) : SeItem {
    override fun weight(): Int = 0
    override suspend fun presentation(): SeItemPresentation = SeAdaptedItemEmptyPresentation(false)
  }
}
