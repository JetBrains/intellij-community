package com.intellij.completion.ml.sorting

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.completion.ml.tracker.setupCompletionContext
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import junit.framework.TestCase

class IgnoredItemsTest : MLSortingTestCase() {
  private lateinit var defaultOrdering: List<String>

  override fun setUp() {
    super.setUp()
    setUpContributor()
    setupCompletionContext(myFixture)
    defaultOrdering = invokeCompletion()
  }

  private fun setUpContributor() {
    val completionContributor = CompletionContributorEP(
      JavaLanguage.INSTANCE.id,
      WrappingContributor::class.java.name,
      DefaultPluginDescriptor("registerCompletionContributor")
    )
    CompletionContributor.EP.point.registerExtension(completionContributor, LoadingOrder.FIRST, testRootDisposable)
  }

  override fun customizeSettings(settings: MLRankingSettingsState): MLRankingSettingsState = settings.withRankingEnabled(true)

  fun `test ml changes the order`() {
    TestCase.assertTrue("Test is broken, please update test data to ensure that completion provides at least 4 elements",
                        defaultOrdering.size > 3)
    val reordered = orderingWithIgnored(emptyList())
    TestCase.assertFalse(defaultOrdering == reordered)
  }

  fun `test ignore all`() {
    val reordered = orderingWithIgnored(defaultOrdering)
    TestCase.assertEquals(defaultOrdering, reordered)
  }

  fun `test delegating items ignored`() {
    WrappingContributor.NESTED_WRAPPING = true
    val reordered = orderingWithIgnored(emptyList())
    TestCase.assertFalse(defaultOrdering.equals(reordered))
    WrappingContributor.NESTED_WRAPPING = false
  }

  fun `test ignore top items`() {
    val topItemsCount = 3
    val reordered = orderingWithIgnored(defaultOrdering.take(topItemsCount))
    TestCase.assertEquals(defaultOrdering.take(topItemsCount), reordered.take(topItemsCount))
    TestCase.assertFalse(defaultOrdering[topItemsCount] == reordered[topItemsCount])
  }

  private fun invokeCompletion(): List<String> {
    complete()
    val result = myItems.map { it.lookupString }
    lookup.hide()

    return result
  }

  private fun orderingWithIgnored(ignored: List<String>): List<String> {
    withRanker(Ranker.Inverse)
    WrappingContributor.ITEMS_TO_IGNORE = ignored
    val result = invokeCompletion()
    WrappingContributor.ITEMS_TO_IGNORE = emptyList()
    withRanker(Ranker.Default)
    return result
  }

  private class WrappingContributor : CompletionContributor() {
    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
      result.runRemainingContributors(parameters) {
        val lookupElement = it.lookupElement
        if (lookupElement.lookupString in ITEMS_TO_IGNORE) {
          result.passResult(it.withLookupElement(makeIgnored(lookupElement)))
        }
        else {
          result.passResult(it)
        }

      }
    }

    private fun makeIgnored(element: LookupElement): LookupElement {
      val ignored = MLRankingIgnorable.wrap(element)
      if (!NESTED_WRAPPING) {
        return ignored
      }

      return object : LookupElementDecorator<LookupElement>(element) {}
    }

    companion object {
      var ITEMS_TO_IGNORE: List<String> = emptyList()
      var NESTED_WRAPPING = false
    }
  }
}