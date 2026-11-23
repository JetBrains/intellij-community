// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

import com.intellij.codeInsight.completion.lightCompletionProjectFixture
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeInsight.lookup.impl.LookupCellRenderer.ItemPresentationCustomizer
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.completion.ml.tracker.LookupTracker
import com.intellij.completion.ml.tracker.setupCompletionContext
import com.intellij.openapi.application.EDT
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.Dispatchers
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import javax.swing.Icon

@TestApplication
class ChangeArrowsDrawingTest {
  companion object {
    private val projectFixture = lightCompletionProjectFixture()
  }

  private val sorting by mlSortingFixture(projectFixture, { it.withRankingEnabled(true).withDiffEnabled(true) })
  private val arrowChecker = ArrowPresenceChecker()

  @BeforeEach
  fun setUp() {
    val fixture = sorting.completion.fixture
    fixture.project.messageBus.connect(fixture.testRootDisposable).subscribe(
      LookupManagerListener.TOPIC,
      ItemsDecoratorInitializer()
    )
    fixture.project.messageBus.connect(fixture.testRootDisposable).subscribe(
      LookupManagerListener.TOPIC,
      arrowChecker
    )
  }

  @Test
  fun testArrowsAreShowingAndUpdated(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    sorting.withRanker(Ranker.Inverse)
    setupCompletionContext(sorting.completion.fixture)
    sorting.completion.complete()
    val invokedCountBeforeTyping = arrowChecker.invokedCount
    sorting.completion.type('r')
    assertTrue(arrowChecker.invokedCount > 1)
    assertTrue(arrowChecker.arrowsFound)
    assertTrue(invokedCountBeforeTyping < arrowChecker.invokedCount)
  }

  @Test
  fun testNoArrowsWithDefaultRanker(): Unit = timeoutRunBlocking(context = Dispatchers.EDT) {
    setupCompletionContext(sorting.completion.fixture)
    sorting.completion.complete()

    assertTrue(arrowChecker.invokedCount > 1)
    assertFalse(arrowChecker.arrowsFound)
  }

  private class ArrowPresenceChecker : LookupTracker() {
    var invokedCount: Int = 0
    var arrowsFound: Boolean = false

    override fun lookupCreated(lookup: LookupImpl, storage: MutableLookupStorage) {
      lookup.addPresentationCustomizer(
        object : ItemPresentationCustomizer {
          override fun customizePresentation(item: LookupElement, presentation: LookupElementPresentation): LookupElementPresentation {
            invokedCount += 1
            arrowsFound = arrowsFound || presentation.icon is ItemsDecoratorInitializer.LeftDecoratedIcon
            return presentation
          }

          override fun customizeEmptyIcon(emptyIcon: Icon): Icon = emptyIcon
        }
      )
    }
  }
}
