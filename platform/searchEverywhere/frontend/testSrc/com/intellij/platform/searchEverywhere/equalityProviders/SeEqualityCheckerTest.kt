// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.platform.searchEverywhere.equalityProviders

import com.intellij.ide.actions.searcheverywhere.AbstractEqualityProvider
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.platform.searchEverywhere.SeItem
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeItemDataFactory
import com.intellij.platform.searchEverywhere.SeLegacyItem
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeSessionImpl
import com.intellij.platform.searchEverywhere.impl.SeItemRegistry
import com.intellij.platform.searchEverywhere.presentations.SeAdaptedItemEmptyPresentation
import com.intellij.platform.searchEverywhere.presentations.SeItemPresentation
import com.intellij.platform.searchEverywhere.toProviderId
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.Processor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.util.UUID
import javax.swing.DefaultListCellRenderer
import javax.swing.ListCellRenderer
import kotlin.time.Duration.Companion.seconds

@TestApplication
class SeEqualityCheckerTest {
  @TestDisposable
  lateinit var disposable: Disposable

  private val session = SeSessionImpl(UUID.randomUUID().toString())

  @AfterEach
  fun closeSession() {
    SeItemRegistry.getInstance().closeSession(session)
  }

  @Test
  fun addsItemsThatAreNotEqual() = runTest {
    val checker = createChecker(providers = listOf(EqualKeysProvider()))

    assertAdded(checker.check(itemData("a", weight = 10)))
    assertAdded(checker.check(itemData("b", weight = 10)))
  }

  @Test
  fun skipsEqualItemWithLowerWeight() = runTest {
    val checker = createChecker(providers = listOf(EqualKeysProvider()))

    assertAdded(checker.check(itemData("a", weight = 10)))
    assertNull(checker.check(itemData("a", weight = 5)))
  }

  @Test
  fun replacesEqualItemWithHigherWeight() = runTest {
    val checker = createChecker(providers = listOf(EqualKeysProvider()))
    val first = itemData("a", weight = 5)

    assertAdded(checker.check(first))
    assertEquals(listOf(first.uuid), checker.check(itemData("a", weight = 10))?.uuidsToReplace)
  }

  @Test
  fun doesNotCompareWithReplacedItem() = runTest {
    val checker = createChecker(providers = listOf(EqualKeysProvider()))

    assertAdded(checker.check(itemData("a", weight = 5)))
    assertNotNull(checker.check(itemData("a", weight = 10)))
    assertNull(checker.check(itemData("a", weight = 7)))
  }

  @Test
  fun prefersProviderWithHigherPriorityForSameWeight() = runTest {
    val checker = createChecker(providers = listOf(EqualKeysProvider()))
    val fileItem = itemData("a", weight = 10, providerId = SeProviderIdUtils.FILES_ID)

    assertAdded(checker.check(fileItem))
    assertEquals(listOf(fileItem.uuid), checker.check(itemData("a", weight = 10, providerId = SeProviderIdUtils.RECENT_FILES_ID))?.uuidsToReplace)
    assertNull(checker.check(itemData("a", weight = 10, providerId = SeProviderIdUtils.FILES_ID)))
  }

  @Test
  fun convertsSortWeightOfLegacyContributorToPriority() = runTest {
    val checker = createChecker(providers = listOf(EqualKeysProvider()))
    val fileItem = itemData("a", weight = 10, providerId = SeProviderIdUtils.FILES_ID)

    assertAdded(checker.check(fileItem))
    assertNull(checker.check(legacyItemData("a", weight = 10, sortWeight = 300)))
    assertEquals(listOf(fileItem.uuid), checker.check(legacyItemData("a", weight = 10, sortWeight = 100))?.uuidsToReplace)
  }

  @Test
  fun legacyProvidersGetSortWeightOfPlatformProvider() = runTest {
    val checker = createChecker(legacyProviders = listOf(EqualKeysLegacyProvider()))
    val fileItem = itemData("a", weight = 10, providerId = SeProviderIdUtils.FILES_ID)

    assertAdded(checker.check(fileItem))
    assertEquals(listOf(fileItem.uuid), checker.check(itemData("a", weight = 10, providerId = SeProviderIdUtils.RECENT_FILES_ID))?.uuidsToReplace)
  }

  @Test
  fun asksNextProviderAfterDoNothing() = runTest {
    val checker = createChecker(providers = listOf(DoNothingProvider(), EqualKeysProvider()))

    assertAdded(checker.check(itemData("a", weight = 10)))
    assertNull(checker.check(itemData("a", weight = 5)))
  }

  @Test
  fun asksLegacyProvidersFirst() = runTest {
    val checker = createChecker(legacyProviders = listOf(SkipLegacyProvider()), providers = listOf(ReplaceProvider()))

    assertAdded(checker.check(itemData("a", weight = 5)))
    assertNull(checker.check(itemData("b", weight = 10)))
  }

  @Test
  fun replacesItemFromLegacyProvider() = runTest {
    val checker = createChecker(legacyProviders = listOf(EqualKeysLegacyProvider()))
    val first = itemData("a", weight = 5)

    assertAdded(checker.check(first))
    assertEquals(listOf(first.uuid), checker.check(itemData("a", weight = 10))?.uuidsToReplace)
    assertNull(checker.check(itemData("a", weight = 7)))
  }

  private fun createChecker(
    legacyProviders: List<SEResultsEqualityProvider> = emptyList(),
    providers: List<SeItemsEqualityProvider> = emptyList(),
  ): SeEqualityChecker {
    ExtensionTestUtil.maskExtensions(SEResultsEqualityProvider.EP_NAME, legacyProviders, disposable)
    ExtensionTestUtil.maskExtensions(SeItemsEqualityProvider.EP_NAME, providers, disposable)
    return SeEqualityChecker()
  }

  private suspend fun itemData(key: String, weight: Int, providerId: String = SeProviderIdUtils.FILES_ID): SeItemData =
    createItemData(TestItem(key, weight), providerId)

  private suspend fun legacyItemData(key: String, weight: Int, sortWeight: Int): SeItemData =
    createItemData(TestLegacyItem(key, weight, TestContributor(sortWeight)), TestContributor.ID)

  private suspend fun createItemData(item: SeItem, providerId: String): SeItemData =
    SeItemDataFactory().createItemData(session, UUID.randomUUID().toString(), item, providerId.toProviderId(), emptyMap())!!

  private suspend fun SeEqualityChecker.check(itemData: SeItemData): SeItemData? = checkAndUpdateIfNeeded(itemData)

  private fun assertAdded(itemData: SeItemData?) {
    assertNotNull(itemData)
    assertEquals(emptyList<String>(), itemData!!.uuidsToReplace)
  }

  private fun runTest(block: suspend () -> Unit) {
    runBlocking {
      withTimeout(30.seconds) {
        block()
      }
    }
  }
}

private open class TestItem(val key: String, private val weight: Int) : SeItem {
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeAdaptedItemEmptyPresentation(false)
}

private class TestLegacyItem(key: String, weight: Int, override val contributor: SearchEverywhereContributor<*>) : TestItem(key, weight), SeLegacyItem

private class TestContributor(private val sortWeight: Int) : SearchEverywhereContributor<Any> {
  override fun getSearchProviderId(): String = ID
  override fun getGroupName(): String = ID
  override fun getSortWeight(): Int = sortWeight
  override fun showInFindResults(): Boolean = false
  override fun fetchElements(pattern: String, progressIndicator: ProgressIndicator, consumer: Processor<in Any>) {}
  override fun processSelectedItem(selected: Any, modifiers: Int, searchText: String): Boolean = false
  override fun getElementsRenderer(): ListCellRenderer<in Any> = DefaultListCellRenderer()

  companion object {
    const val ID: String = "TestContributor"
  }
}

private fun keyOf(element: Any): String? = (element as? TestItem)?.key

private class EqualKeysProvider : SeAbstractItemsEqualityProvider() {
  override fun areEqual(newItem: SeFoundItemInfo, alreadyFoundItem: SeFoundItemInfo): Boolean {
    val newKey = keyOf(newItem.item.rawObject) ?: return false
    return newKey == keyOf(alreadyFoundItem.item.rawObject)
  }
}

private class DoNothingProvider : SeItemsEqualityProvider {
  override fun compareItems(newItem: SeFoundItemInfo, alreadyFoundItems: Collection<SeFoundItemInfo>): SeEqualItemsAction =
    SeEqualItemsAction.DoNothing
}

private class ReplaceProvider : SeItemsEqualityProvider {
  override fun compareItems(newItem: SeFoundItemInfo, alreadyFoundItems: Collection<SeFoundItemInfo>): SeEqualItemsAction =
    if (alreadyFoundItems.isEmpty()) SeEqualItemsAction.DoNothing else SeEqualItemsAction.Replace(alreadyFoundItems.toList())
}

private class SkipLegacyProvider : SEResultsEqualityProvider {
  override fun compareItems(newItem: SearchEverywhereFoundElementInfo, alreadyFoundItems: List<SearchEverywhereFoundElementInfo>): SEEqualElementsActionType =
    if (alreadyFoundItems.isEmpty()) SEEqualElementsActionType.DoNothing else SEEqualElementsActionType.Skip
}

private class EqualKeysLegacyProvider : AbstractEqualityProvider() {
  override fun areEqual(newItem: SearchEverywhereFoundElementInfo, alreadyFoundItem: SearchEverywhereFoundElementInfo): Boolean {
    val newKey = keyOf(newItem.element) ?: return false
    return newKey == keyOf(alreadyFoundItem.element)
  }
}
