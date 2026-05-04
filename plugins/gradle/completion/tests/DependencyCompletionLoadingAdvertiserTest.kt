// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion

import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.PlainPrefixMatcher
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.patterns.ElementPattern
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.LOCAL
import com.intellij.repository.search.completion.api.DependencyCompletionContributionSource.SERVER
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionResult
import com.intellij.testFramework.junit5.TestApplication
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@TestApplication
internal class DependencyCompletionLoadingAdvertiserTest {

  @Test
  fun `placeholder is added when server failed and no results in non-free mode`() {
    val advertiser = DependencyCompletionLoadingAdvertiser(freeMode = false)
    advertiser.onEvent(DependencyCompletionEvent.ServerFailed())

    val resultSet = TestCompletionResultSet()
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertEquals(1, resultSet.lookupElements.size)
    assertEquals(
      GradleBundle.message("gradle.dependency.completion.server.unavailable.short"),
      resultSet.lookupElements.single().renderItemText(),
    )
  }

  @Test
  fun `placeholder uses timeout text after ServerTimedOut event`() {
    val advertiser = DependencyCompletionLoadingAdvertiser(freeMode = false)
    advertiser.onEvent(DependencyCompletionEvent.ServerTimedOut)

    val resultSet = TestCompletionResultSet()
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertEquals(1, resultSet.lookupElements.size)
    assertEquals(
      GradleBundle.message("gradle.dependency.completion.server.timeout.short"),
      resultSet.lookupElements.single().renderItemText(),
    )
  }

  @Test
  fun `placeholder is added via the always-true prefix matcher`() {
    val advertiser = DependencyCompletionLoadingAdvertiser(freeMode = false)
    advertiser.onEvent(DependencyCompletionEvent.ServerFailed())

    val resultSet = TestCompletionResultSet()
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertEquals(1, resultSet.requestedPrefixMatchers.size)
    assertSame(PlainPrefixMatcher.ALWAYS_TRUE, resultSet.requestedPrefixMatchers.single())
  }

  @Test
  fun `no placeholder is added when hadResults is true`() {
    val advertiser = DependencyCompletionLoadingAdvertiser(freeMode = false)
    advertiser.onEvent(DependencyCompletionEvent.ServerFailed())

    val resultSet = TestCompletionResultSet()
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = true)

    assertTrue(resultSet.lookupElements.isEmpty())
  }

  @Test
  fun `no placeholder is added for auto-popup completion`() {
    val advertiser = DependencyCompletionLoadingAdvertiser(freeMode = false)
    advertiser.onEvent(DependencyCompletionEvent.ServerFailed())

    val resultSet = TestCompletionResultSet()
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = true, hadResults = false)

    assertTrue(resultSet.lookupElements.isEmpty())
  }

  @Test
  fun `no placeholder is added in free mode`() {
    val advertiser = DependencyCompletionLoadingAdvertiser(freeMode = true)
    advertiser.onEvent(DependencyCompletionEvent.ServerFailed())

    val resultSet = TestCompletionResultSet()
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertTrue(resultSet.lookupElements.isEmpty())
  }

  @Test
  fun `no placeholder is added without a terminal server status`() {
    val advertiser = DependencyCompletionLoadingAdvertiser(freeMode = false)
    advertiser.showSearchingStatus()
    advertiser.onComplete()

    val resultSet = TestCompletionResultSet()
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertTrue(resultSet.lookupElements.isEmpty())
  }

  @Test
  fun `Item events do not record a terminal server status`() {
    val advertiser = DependencyCompletionLoadingAdvertiser(freeMode = false)
    advertiser.onEvent(localItem())
    advertiser.onEvent(serverItem())
    advertiser.onComplete()

    val resultSet = TestCompletionResultSet()
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertTrue(resultSet.lookupElements.isEmpty())
  }

  @Test
  fun `ServerFailed after item events still records the terminal status`() {
    val advertiser = DependencyCompletionLoadingAdvertiser(freeMode = false)
    advertiser.onEvent(localItem())
    advertiser.onEvent(DependencyCompletionEvent.ServerFailed(RuntimeException("network")))

    val resultSet = TestCompletionResultSet()
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertEquals(1, resultSet.lookupElements.size)
    assertEquals(
      GradleBundle.message("gradle.dependency.completion.server.unavailable.short"),
      resultSet.lookupElements.single().renderItemText(),
    )
  }

  @Test
  fun `ServerFailed after ServerTimedOut overrides the recorded status`() {
    val advertiser = DependencyCompletionLoadingAdvertiser(freeMode = false)
    advertiser.onEvent(DependencyCompletionEvent.ServerTimedOut)
    advertiser.onEvent(DependencyCompletionEvent.ServerFailed())

    val resultSet = TestCompletionResultSet()
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertEquals(
      GradleBundle.message("gradle.dependency.completion.server.unavailable.short"),
      resultSet.lookupElements.single().renderItemText(),
    )
  }

  @Test
  fun `ServerTimedOut after ServerFailed does not override the recorded status`() {
    val advertiser = DependencyCompletionLoadingAdvertiser(freeMode = false)
    advertiser.onEvent(DependencyCompletionEvent.ServerFailed())
    advertiser.onEvent(DependencyCompletionEvent.ServerTimedOut)

    val resultSet = TestCompletionResultSet()
    advertiser.addServerErrorPlaceholderIfNeeded(resultSet, isAutoPopup = false, hadResults = false)

    assertEquals(
      GradleBundle.message("gradle.dependency.completion.server.unavailable.short"),
      resultSet.lookupElements.single().renderItemText(),
    )
  }

  @Test
  fun `lifecycle methods do not throw when there is no active completion session`() {
    val advertiser = DependencyCompletionLoadingAdvertiser(freeMode = false)
    // CompletionServiceImpl.currentCompletionProgressIndicator is null in unit tests,
    // so all ad-update calls should silently no-op.
    advertiser.showSearchingStatus()
    advertiser.onEvent(localItem())
    advertiser.onEvent(serverItem())
    advertiser.onEvent(DependencyCompletionEvent.ServerFailed())
    advertiser.onEvent(DependencyCompletionEvent.ServerTimedOut)
    advertiser.onComplete()
  }
}

private fun localItem(): DependencyCompletionEvent<DependencyCompletionResult> =
  DependencyCompletionEvent.Item(
    DependencyCompletionResult("g", "a", "1.0", source = LOCAL))

private fun serverItem(): DependencyCompletionEvent<DependencyCompletionResult> =
  DependencyCompletionEvent.Item(
    DependencyCompletionResult("g", "a", "1.0", source = SERVER))

private fun LookupElement.renderItemText(): String? {
  val presentation = LookupElementPresentation()
  renderElement(presentation)
  return presentation.itemText
}

private class TestCompletionResultSet : CompletionResultSet(PlainPrefixMatcher(""), {}, null) {
  val lookupElements: MutableList<LookupElement> = mutableListOf()
  val requestedPrefixMatchers: MutableList<PrefixMatcher> = mutableListOf()

  override fun addElement(element: LookupElement) {
    lookupElements.add(element)
  }

  override fun withPrefixMatcher(matcher: PrefixMatcher): CompletionResultSet {
    requestedPrefixMatchers.add(matcher)
    return this
  }

  override fun withPrefixMatcher(prefix: String): CompletionResultSet =
    withPrefixMatcher(PlainPrefixMatcher(prefix))

  override fun withRelevanceSorter(sorter: CompletionSorter): CompletionResultSet = this
  override fun addLookupAdvertisement(text: String) {}
  override fun caseInsensitive(): CompletionResultSet = this
  override fun restartCompletionOnPrefixChange(prefixCondition: ElementPattern<String>) {}
  override fun restartCompletionWhenNothingMatches() {}
}
