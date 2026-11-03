// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ui

import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeItemDataFactory
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeResultAddedEvent
import com.intellij.platform.searchEverywhere.SeResultReplacedEvent
import com.intellij.platform.searchEverywhere.SeSessionImpl
import com.intellij.platform.searchEverywhere.impl.SeItemRegistry
import com.intellij.platform.searchEverywhere.presentations.SeAdaptedItemEmptyPresentation
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.toProviderId
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * Checks how [handleEvent] puts the found items in the result list.
 */
@TestApplication
class SeResultListEventsTest {
  private val session = SeSessionImpl(UUID.randomUUID().toString())
  private val list = SeResultListCollection(mutableSetOf())

  @AfterEach
  fun closeSession() {
    SeItemRegistry.getInstance().closeSession(session)
  }

  @Test
  fun `an equal weight puts the provider with the lower sort weight first`(): Unit = runBlocking {
    list.add(itemData("file", 100, SeProviderIdUtils.FILES_ID))
    list.add(itemData("class", 100, SeProviderIdUtils.CLASSES_ID))

    assertEquals(listOf("class", "file"), list.keys())
  }

  @Test
  fun `a replace by a provider with a higher list priority moves the item up`(): Unit = runBlocking {
    list.add(itemData("b", 200, SeProviderIdUtils.FILES_ID))
    val a = itemData("a", 100, SeProviderIdUtils.FILES_ID)
    list.add(a)

    list.replace(a, itemData("a top hit", 100, SeProviderIdUtils.TOP_HIT_ID))

    assertEquals(listOf("a top hit", "b"), list.keys())
  }

  @Test
  fun `a replace by a provider with a lower sort weight moves the item to its place`(): Unit = runBlocking {
    list.add(itemData("b", 100, SeProviderIdUtils.FILES_ID))
    val a = itemData("a", 100, SeProviderIdUtils.FILES_ID)
    list.add(a)

    // The class goes above the file with the same weight.
    list.replace(a, itemData("a class", 100, SeProviderIdUtils.CLASSES_ID))

    assertEquals(listOf("a class", "b"), list.keys())
  }

  @Test
  fun `a replace with a higher weight moves the item up`(): Unit = runBlocking {
    list.add(itemData("b", 200, SeProviderIdUtils.FILES_ID))
    val a = itemData("a", 100, SeProviderIdUtils.FILES_ID)
    list.add(a)

    list.replace(a, itemData("a better", 300, SeProviderIdUtils.FILES_ID))

    assertEquals(listOf("a better", "b"), list.keys())
  }

  @Test
  fun `a replace with a lower weight moves the item down`(): Unit = runBlocking {
    val a = itemData("a", 300, SeProviderIdUtils.FILES_ID)
    list.add(a)
    list.add(itemData("b", 200, SeProviderIdUtils.FILES_ID))

    // The class copy wins the equality check by its provider, but its weight is lower.
    list.replace(a, itemData("a class", 100, SeProviderIdUtils.CLASSES_ID))

    assertEquals(listOf("b", "a class"), list.keys())
  }

  private fun SeResultListCollection.add(itemData: SeItemData) {
    handleEvent("pattern", SeResultAddedEvent(itemData), isZeroOffset = false)
  }

  private fun SeResultListCollection.replace(replaced: SeItemData, itemData: SeItemData) {
    handleEvent("pattern", SeResultReplacedEvent(listOf(replaced.uuid), itemData), isZeroOffset = false)
  }

  /** Returns the keys of the items in the list, from the top. */
  private fun SeResultListCollection.keys(): List<String> =
    list.mapNotNull { (it as? SeResultListItemRow)?.item?.fetchItemIfExists() as? TestItem }.map { it.key }

  private suspend fun itemData(key: String, weight: Int, providerId: String): SeItemData =
    checkNotNull(SeItemDataFactory().createItemData(session, UUID.randomUUID().toString(), TestItem(key, weight), providerId.toProviderId(), emptyMap()))

  private class TestItem(val key: String, private val weight: Int) : SeItem {
    override fun weight(): Int = weight
    override suspend fun presentation(): SeItemPresentation = SeAdaptedItemEmptyPresentation(false)
  }
}
