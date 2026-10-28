// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DEPRECATION")

package com.intellij.platform.searchEverywhere.equalityProviders

import com.intellij.ide.actions.searcheverywhere.AbstractEqualityProvider
import com.intellij.ide.actions.searcheverywhere.ActionsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.OptionEqualityProvider
import com.intellij.ide.actions.searcheverywhere.PsiElementsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider
import com.intellij.ide.actions.searcheverywhere.SEResultsEqualityProvider.SEEqualElementsActionType
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereFoundElementInfo
import com.intellij.ide.actions.searcheverywhere.TrivialElementsEqualityProvider
import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.util.gotoByName.GotoActionModel
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
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
import org.junit.jupiter.api.Assertions.assertTrue
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
    val recentFileItem = itemData("a", weight = 10, providerId = SeProviderIdUtils.RECENT_FILES_ID)
    assertEquals(listOf(fileItem.uuid), checker.check(recentFileItem)?.uuidsToReplace)
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
    val recentFileItem = itemData("a", weight = 10, providerId = SeProviderIdUtils.RECENT_FILES_ID)
    assertEquals(listOf(fileItem.uuid), checker.check(recentFileItem)?.uuidsToReplace)
  }

  @Test
  fun asksNextProviderAfterDoNothing() = runTest {
    val checker = createChecker(providers = listOf(DoNothingProvider(), EqualKeysProvider()))

    assertAdded(checker.check(itemData("a", weight = 10)))
    assertNull(checker.check(itemData("a", weight = 5)))
  }

  @Test
  fun asksProvidersBeforeLegacyProviders() = runTest {
    val checker = createChecker(legacyProviders = listOf(SkipLegacyProvider()), providers = listOf(ReplaceProvider()))
    val first = itemData("a", weight = 5)

    assertAdded(checker.check(first))
    assertEquals(listOf(first.uuid), checker.check(itemData("b", weight = 10))?.uuidsToReplace)
  }

  @Test
  fun asksLegacyProvidersAfterDoNothing() = runTest {
    val checker = createChecker(legacyProviders = listOf(EqualKeysLegacyProvider()), providers = listOf(DoNothingProvider()))

    assertAdded(checker.check(itemData("a", weight = 10)))
    assertNull(checker.check(itemData("a", weight = 5)))
  }

  @Test
  fun skipsLegacyProviderWithRegisteredReplacement() {
    val trivialProvider = TrivialElementsEqualityProvider()
    val psiProvider = PsiElementsEqualityProvider()
    val otherProvider = EqualKeysLegacyProvider()

    val legacyProviders = SeEqualityChecker.legacyProvidersToAsk(listOf(trivialProvider, psiProvider, otherProvider),
                                                                 listOf(SeTrivialItemsEqualityProvider(), EqualKeysProvider()))

    assertEquals(listOf(psiProvider, otherProvider), legacyProviders)
  }

  @Test
  fun registeredPlatformProvidersReplaceLegacyOnes() {
    val platformProviderClasses: List<Class<*>> = listOf(
      SeTrivialItemsEqualityProvider::class.java, SePsiElementsEqualityProvider::class.java,
      SeActionsEqualityProvider::class.java, SeOptionsEqualityProvider::class.java,
    )
    val legacyPlatformProviderClasses: Set<Class<*>> = setOf(
      TrivialElementsEqualityProvider::class.java, PsiElementsEqualityProvider::class.java,
      ActionsEqualityProvider::class.java, OptionEqualityProvider::class.java,
    )
    val providers = SeItemsEqualityProvider.EP_NAME.extensionList
    val legacyProviders = SEResultsEqualityProvider.EP_NAME.extensionList

    assertEquals(platformProviderClasses, providers.map { it.javaClass }.filter { it in platformProviderClasses })
    val registeredLegacyClasses = legacyProviders.map { it.javaClass }.filterTo(HashSet()) { it in legacyPlatformProviderClasses }
    assertEquals(legacyPlatformProviderClasses, registeredLegacyClasses)
    assertTrue(SeEqualityChecker.legacyProvidersToAsk(legacyProviders, providers).none { it.javaClass in legacyPlatformProviderClasses })
  }

  @Test
  fun trivialProviderSkipsSameObject() = runTest {
    val checker = createChecker(providers = listOf(SeTrivialItemsEqualityProvider()))
    val item = TestItem("a", weight = 10)

    assertAdded(checker.check(createItemData(item, SeProviderIdUtils.FILES_ID)))
    assertNull(checker.check(createItemData(item, SeProviderIdUtils.FILES_ID)))
    assertAdded(checker.check(itemData("a", weight = 10)))
  }

  @Test
  fun actionsProviderSkipsSameAction() = runTest {
    val checker = createChecker(providers = listOf(SeActionsEqualityProvider()))
    val action = TestAction()

    assertAdded(checker.check(itemData("a", weight = 10, rawObject = action)))
    assertNull(checker.check(itemData("b", weight = 10, rawObject = action)))
    assertAdded(checker.check(itemData("c", weight = 10, rawObject = TestAction())))
  }

  @Test
  fun optionsProviderSkipsEqualOption() = runTest {
    val checker = createChecker(providers = listOf(SeOptionsEqualityProvider()))
    val option = GotoActionModel.MatchedValue(OptionDescription("option"), "option", GotoActionModel.MatchedValueType.OPTION)

    assertAdded(checker.check(itemData("a", weight = 10, rawObject = option)))
    assertNull(checker.check(itemData("b", weight = 10, rawObject = OptionDescription("option"))))
    assertAdded(checker.check(itemData("c", weight = 10, rawObject = OptionDescription("other option"))))
  }

  @Test
  fun collisionsScenario() = runTest {
    assertCollisionScenario(
      providers = listOf(
        ScenarioProvider("test1", 0, *values("item1_", 1..15)),
        ScenarioProvider("test2", 10, *values("item2_", 1..2), "duplicateItem1", "duplicateItem2", *values("item2_", 3..12)),
        ScenarioProvider("test3", 8, *values("item3_", 1..4), "duplicateItem1", *values("item3_", 5..8), "duplicateItem2",
                         "duplicateItem3"),
        ScenarioProvider("test4", 15, *values("item4_", 1..2), "duplicateItem2", "duplicateItem3", *values("item4_", 3..9)),
        ScenarioProvider("test6", 20, *values("item6_", 1..4), "duplicateItem3", *values("item6_", 5..11), "duplicateItem4",
                         *values("item6_", 12..13)),
        ScenarioProvider("test7", 5, *values("item7_", 1..2), "duplicateItem3", *values("item7_", 3..10)),
        ScenarioProvider("test8", 10, *values("item8_", 1..5), "duplicateItem4"),
        ScenarioProvider("test9", 15, *values("item9_", 1..4), "duplicateItem5", "duplicateItem6"),
        ScenarioProvider("test10", 10, *values("item10_", 1..4), "duplicateItem5", "duplicateItem6"),
      ),
      expected = mapOf(
        "test1" to listOf(*values("item1_", 1..15)),
        "test2" to listOf(*values("item2_", 1..2), "duplicateItem1", *values("item2_", 3..12)),
        "test3" to listOf(*values("item3_", 1..8)),
        "test4" to listOf(*values("item4_", 1..2), "duplicateItem2", *values("item4_", 3..9)),
        "test6" to listOf(*values("item6_", 1..4), "duplicateItem3", *values("item6_", 5..11), "duplicateItem4", *values("item6_", 12..13)),
        "test7" to listOf(*values("item7_", 1..10)),
        "test8" to listOf(*values("item8_", 1..5)),
        "test9" to listOf(*values("item9_", 1..4), "duplicateItem5", "duplicateItem6"),
        "test10" to listOf(*values("item10_", 1..4)),
      ),
    )
  }

  @Test
  fun onlyCollisionsScenario() = runTest {
    assertCollisionScenario(
      providers = listOf(
        ScenarioProvider("test1", 10, *values("item", 1..12)),
        ScenarioProvider("test2", 9, *values("item", 1..12)),
        ScenarioProvider("test3", 8, *values("item", 1..12)),
        ScenarioProvider("test4", 7, *values("item", 1..12)),
        ScenarioProvider("test5", 6, *values("item", 1..12)),
      ),
      expected = mapOf(
        "test1" to listOf(*values("item", 1..12)),
        "test2" to emptyList(),
        "test3" to emptyList(),
        "test4" to emptyList(),
        "test5" to emptyList(),
      ),
    )
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
    maskProviders(legacyProviders, providers, disposable)
    return SeEqualityChecker()
  }

  private fun maskProviders(
    legacyProviders: List<SEResultsEqualityProvider>,
    providers: List<SeItemsEqualityProvider>,
    parentDisposable: Disposable,
  ) {
    ExtensionTestUtil.maskExtensions(SEResultsEqualityProvider.EP_NAME, legacyProviders, parentDisposable)
    ExtensionTestUtil.maskExtensions(SeItemsEqualityProvider.EP_NAME, providers, parentDisposable)
  }

  private suspend fun itemData(
    key: String,
    weight: Int,
    providerId: String = SeProviderIdUtils.FILES_ID,
    rawObject: Any? = null,
  ): SeItemData =
    createItemData(TestItem(key, weight, rawObject), providerId)

  private suspend fun legacyItemData(key: String, weight: Int, sortWeight: Int): SeItemData =
    createItemData(TestLegacyItem(key, weight, TestContributor(sortWeight)), TestContributor.ID)

  private suspend fun createItemData(item: SeItem, providerId: String): SeItemData =
    SeItemDataFactory().createItemData(session, UUID.randomUUID().toString(), item, providerId.toProviderId(), emptyMap())!!

  private suspend fun SeEqualityChecker.check(itemData: SeItemData): SeItemData? = checkAndUpdateIfNeeded(itemData)

  /**
   * Runs a collision scenario of the old `MixingMultiThreadSearchTest` and compares the shown values of each provider with [expected].
   *
   * The checker has no limits of the old searcher, so each provider delivers all its values.
   * The scenario runs with the new and the legacy trivial provider, and in each [DeliveryOrder].
   */
  private suspend fun assertCollisionScenario(providers: List<ScenarioProvider>, expected: Map<String, List<String>>) {
    val trivialProviders = listOf(
      TrivialProviderSetup("new provider", legacyProviders = emptyList(), providers = listOf(SeTrivialItemsEqualityProvider())),
      TrivialProviderSetup("legacy provider", legacyProviders = listOf(TrivialElementsEqualityProvider()), providers = emptyList()),
    )

    for (trivialProvider in trivialProviders) {
      val setupDisposable = Disposer.newDisposable(disposable, trivialProvider.name)
      try {
        maskProviders(trivialProvider.legacyProviders, trivialProvider.providers, setupDisposable)
        for (order in DeliveryOrder.entries) {
          val shownValues = showValues(SeEqualityChecker(), order.arrange(providers))
          assertEquals(expected, providers.associate { it.id to shownValues[it.id].orEmpty() }, "${trivialProvider.name}, $order")
        }
      }
      finally {
        Disposer.dispose(setupDisposable)
      }
    }
  }

  /** Returns the values that stay in the results after [checker] checks all [deliveries], grouped by the provider id. */
  private suspend fun showValues(checker: SeEqualityChecker, deliveries: List<Pair<ScenarioProvider, String>>): Map<String, List<String>> {
    val shownValues = LinkedHashMap<String, Pair<String, String>>()
    for ((provider, value) in deliveries) {
      val itemData = checker.check(itemData(value, provider.weight, provider.id, rawObject = value)) ?: continue
      itemData.uuidsToReplace.forEach { shownValues.remove(it) }
      shownValues[itemData.uuid] = provider.id to value
    }
    return shownValues.values.groupBy({ it.first }, { it.second })
  }

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

private open class TestItem(val key: String, private val weight: Int, private val wrappedObject: Any? = null) : SeItem {
  override val rawObject: Any get() = wrappedObject ?: this
  override fun weight(): Int = weight
  override suspend fun presentation(): SeItemPresentation = SeAdaptedItemEmptyPresentation(false)
}

private class TestLegacyItem(key: String, weight: Int, override val contributor: SearchEverywhereContributor<*>)
  : TestItem(key, weight), SeLegacyItem

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

private class TrivialProviderSetup(
  val name: String,
  val legacyProviders: List<SEResultsEqualityProvider>,
  val providers: List<SeItemsEqualityProvider>,
)

private class ScenarioProvider(val id: String, val weight: Int, vararg val values: String) {
  fun deliveries(): List<Pair<ScenarioProvider, String>> = values.map { this to it }
}

/** The order in which the providers of a scenario deliver their values. Each provider keeps the order of its own values. */
private enum class DeliveryOrder {
  BY_PROVIDER {
    override fun arrange(providers: List<ScenarioProvider>) = providers.flatMap { it.deliveries() }
  },
  BY_PROVIDER_REVERSED {
    override fun arrange(providers: List<ScenarioProvider>) = providers.asReversed().flatMap { it.deliveries() }
  },
  ROUND_ROBIN {
    override fun arrange(providers: List<ScenarioProvider>): List<Pair<ScenarioProvider, String>> {
      val deliveries = providers.map { it.deliveries() }
      return (0 until deliveries.maxOf { it.size }).flatMap { index -> deliveries.mapNotNull { it.getOrNull(index) } }
    }
  };

  abstract fun arrange(providers: List<ScenarioProvider>): List<Pair<ScenarioProvider, String>>
}

private fun values(prefix: String, numbers: IntRange): Array<String> = numbers.map { "$prefix$it" }.toTypedArray()

private class TestAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {}
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
  override fun compareItems(
    newItem: SearchEverywhereFoundElementInfo,
    alreadyFoundItems: List<SearchEverywhereFoundElementInfo>,
  ): SEEqualElementsActionType =
    if (alreadyFoundItems.isEmpty()) SEEqualElementsActionType.DoNothing else SEEqualElementsActionType.Skip
}

private class EqualKeysLegacyProvider : AbstractEqualityProvider() {
  override fun areEqual(newItem: SearchEverywhereFoundElementInfo, alreadyFoundItem: SearchEverywhereFoundElementInfo): Boolean {
    val newKey = keyOf(newItem.element) ?: return false
    return newKey == keyOf(alreadyFoundItem.element)
  }
}
