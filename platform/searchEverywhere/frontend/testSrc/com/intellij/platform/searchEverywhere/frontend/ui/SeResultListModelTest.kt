// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.ide.rpc.ThrottledAccumulatedItems
import com.intellij.ide.rpc.ThrottledItems
import com.intellij.ide.rpc.ThrottledOneItem
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeItemDataFactory
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeResultAddedEvent
import com.intellij.platform.searchEverywhere.SeResultEndEvent
import com.intellij.platform.searchEverywhere.SeResultEvent
import com.intellij.platform.searchEverywhere.SeResultSkippedEvent
import com.intellij.platform.searchEverywhere.SeSessionImpl
import com.intellij.platform.searchEverywhere.frontend.SeSearchStatePublisher
import com.intellij.platform.searchEverywhere.frontend.tabs.files.SeFilesTab
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
 * Checks how [SeResultListModel.addFromThrottledEvent] changes the validity of the list.
 */
@TestApplication
class SeResultListModelTest {
  private val session = SeSessionImpl(UUID.randomUUID().toString())
  private val model = SeResultListModel(SeSearchStatePublisher()) { DefaultListSelectionModel() }
  private val searchContext = SeSearchContext(UUID.randomUUID().toString(), SeFilesTab.ID, "pattern", emptyFlow())

  @AfterEach
  fun closeSession() {
    SeItemRegistry.getInstance().closeSession(session)
  }

  @Test
  fun `an end event keeps the invalid list invalid`() {
    model.invalidate()

    model.add(ThrottledOneItem(SeResultEndEvent(SeProviderIdUtils.NON_INDEXABLE_FILES_ID.toProviderId())))

    assertFalse(model.isValid)
  }

  @Test
  fun `a skipped event keeps the invalid list invalid`() {
    model.invalidate()

    model.add(ThrottledOneItem(SeResultSkippedEvent(SeProviderIdUtils.NON_INDEXABLE_FILES_ID.toProviderId())))

    assertFalse(model.isValid)
  }

  @Test
  fun `an end event keeps the rows of the previous search`(): Unit = runBlocking {
    model.add(ThrottledOneItem(SeResultAddedEvent(itemData("old"))))
    model.invalidate()

    model.add(ThrottledOneItem(SeResultEndEvent(SeProviderIdUtils.NON_INDEXABLE_FILES_ID.toProviderId())))

    assertEquals(listOf("old"), model.keys())
  }

  @Test
  fun `an added item makes the invalid list valid`(): Unit = runBlocking {
    model.add(ThrottledOneItem(SeResultAddedEvent(itemData("old"))))
    model.invalidate()
    model.add(ThrottledOneItem(SeResultEndEvent(SeProviderIdUtils.NON_INDEXABLE_FILES_ID.toProviderId())))

    model.add(ThrottledOneItem(SeResultAddedEvent(itemData("new"))))

    assertTrue(model.isValid)
    assertEquals(listOf("new"), model.keys())
  }

  @Test
  fun `an accumulated batch with an added item makes the invalid list valid`(): Unit = runBlocking {
    model.invalidate()

    model.add(ThrottledAccumulatedItems(listOf(
      SeResultEndEvent(SeProviderIdUtils.NON_INDEXABLE_FILES_ID.toProviderId()),
      SeResultAddedEvent(itemData("new")),
    )))

    assertTrue(model.isValid)
    assertEquals(listOf("new"), model.keys())
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
    override fun weight(): Int = 100
    override suspend fun presentation(): SeItemPresentation = SeAdaptedItemEmptyPresentation(false)
  }
}
