package com.intellij.completion.ml.sorting

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionContributorEP
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.codeInsight.completion.ml.MLRankingIgnorable
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementDecorator
import com.intellij.completion.ml.tracker.setupCompletionContext
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.DefaultPluginDescriptor
import com.intellij.openapi.extensions.LoadingOrder
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

@TestApplication
class IgnoredItemsTest {
  companion object {
    private val projectFixture = lightCompletionProjectFixture()
  }

  private val sorting by mlSortingFixture(projectFixture, { it.withRankingEnabled(true) })
  private lateinit var defaultOrdering: List<String>

  @BeforeEach
  fun setUp(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    setUpContributor()
    setupCompletionContext(sorting.completion.fixture)
    defaultOrdering = invokeCompletion()
  }

  private fun setUpContributor() {
    val completionContributor = CompletionContributorEP(
      JavaLanguage.INSTANCE.id,
      WrappingContributor::class.java.name,
      DefaultPluginDescriptor("registerCompletionContributor")
    )
    CompletionContributor.EP.point.registerExtension(completionContributor, LoadingOrder.FIRST, sorting.completion.fixture.testRootDisposable)
  }

  @Test
  fun `test ml changes the order`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    assertTrue(defaultOrdering.size > 3,
               "Test is broken, please update test data to ensure that completion provides at least 4 elements")
    val reordered = orderingWithIgnored(emptyList())
    assertFalse(defaultOrdering == reordered)
  }

  @Test
  fun `test ignore all`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val reordered = orderingWithIgnored(defaultOrdering)
    assertEquals(defaultOrdering, reordered)
  }

  @Test
  fun `test delegating items ignored`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    WrappingContributor.NESTED_WRAPPING = true
    val reordered = orderingWithIgnored(emptyList())
    assertFalse(defaultOrdering.equals(reordered))
    WrappingContributor.NESTED_WRAPPING = false
  }

  @Test
  fun `test ignore top items`(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    val topItemsCount = 3
    val reordered = orderingWithIgnored(defaultOrdering.take(topItemsCount))
    assertEquals(defaultOrdering.take(topItemsCount), reordered.take(topItemsCount))
    assertFalse(defaultOrdering[topItemsCount] == reordered[topItemsCount])
  }

  private fun invokeCompletion(): List<String> {
    sorting.completion.complete()
    val result = sorting.completion.items!!.map { it.lookupString }
    sorting.completion.getLookup()!!.hide()

    return result
  }

  private fun orderingWithIgnored(ignored: List<String>): List<String> {
    sorting.withRanker(Ranker.Inverse)
    WrappingContributor.ITEMS_TO_IGNORE = ignored
    val result = invokeCompletion()
    WrappingContributor.ITEMS_TO_IGNORE = emptyList()
    sorting.withRanker(Ranker.Default)
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
