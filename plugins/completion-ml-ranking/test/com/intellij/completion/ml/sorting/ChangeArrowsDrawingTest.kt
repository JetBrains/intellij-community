// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.completion.ml.tracker.LookupTracker
import com.intellij.completion.ml.tracker.setupCompletionContext
import junit.framework.TestCase

class ChangeArrowsDrawingTest : MLSortingTestCase() {
  private val arrowChecker = ArrowPresenceChecker()

  override fun customizeSettings(settings: MLRankingSettingsState): MLRankingSettingsState {
    return settings.withRankingEnabled(true).withDiffEnabled(true)
  }

  override fun setUp() {
    super.setUp()

    project.messageBus.connect(testRootDisposable).subscribe(
      LookupManagerListener.TOPIC,
      ItemsDecoratorInitializer()
    )
    project.messageBus.connect(testRootDisposable).subscribe(
      LookupManagerListener.TOPIC,
      arrowChecker
    )
  }

  fun testArrowsAreShowingAndUpdated() {
    withRanker(Ranker.Inverse)
    setupCompletionContext(myFixture)
    complete()
    val invokedCountBeforeTyping = arrowChecker.invokedCount
    type('r')
    TestCase.assertTrue(arrowChecker.invokedCount > 1)
    TestCase.assertTrue(arrowChecker.arrowsFound)
    TestCase.assertTrue(invokedCountBeforeTyping < arrowChecker.invokedCount)
  }

  fun testNoArrowsWithDefaultRanker() {
    setupCompletionContext(myFixture)
    complete()

    TestCase.assertTrue(arrowChecker.invokedCount > 1)
    TestCase.assertFalse(arrowChecker.arrowsFound)
  }

  private class ArrowPresenceChecker : LookupTracker() {
    var invokedCount: Int = 0
    var arrowsFound: Boolean = false

    override fun lookupCreated(lookup: LookupImpl, storage: MutableLookupStorage) {
      lookup.addPresentationCustomizer { _, presentation ->
        invokedCount += 1
        arrowsFound = arrowsFound || presentation.icon is ItemsDecoratorInitializer.LeftDecoratedIcon

        presentation
      }
    }
  }
}