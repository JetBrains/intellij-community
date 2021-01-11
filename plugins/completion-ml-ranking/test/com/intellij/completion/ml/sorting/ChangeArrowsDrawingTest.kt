// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.sorting

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
import com.intellij.codeInsight.lookup.LookupManagerListener
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.experiment.ExperimentInfo
import com.intellij.completion.ml.experiment.ExperimentStatus
import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.completion.ml.storage.MutableLookupStorage
import com.intellij.completion.ml.tracker.LookupTracker
import com.intellij.completion.ml.tracker.setupCompletionContext
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeatureMapper
import com.intellij.internal.ml.FloatFeature
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.replaceService
import junit.framework.TestCase

class ChangeArrowsDrawingTest : LightFixtureCompletionTestCase() {
  private val arrowChecker = ArrowPresenceChecker()
  override fun setUp() {
    super.setUp()
    val settings = CompletionMLRankingSettings.getInstance()
    val settingsStateBefore = MLRankingSettingsState.build("Java", settings)
    with(settings) {
      isRankingEnabled = true
      isShowDiffEnabled = true
      setLanguageEnabled("Java", true)
    }

    RankingSupport.enableInTests(testRootDisposable)
    ApplicationManager.getApplication().replaceService(ExperimentStatus::class.java, TestArrowsExperimentStatus(), testRootDisposable)
    Disposer.register(testRootDisposable, Disposable { settingsStateBefore.restore(CompletionMLRankingSettings.getInstance()) })
    project.messageBus.connect(testRootDisposable).subscribe(
      LookupManagerListener.TOPIC,
      ItemsDecoratorInitializer()
    )
    project.messageBus.connect(testRootDisposable).subscribe(
      LookupManagerListener.TOPIC,
      arrowChecker
    )
    ExperimentModelProvider.registerProvider(TestInverseRankingModelProvider(), testRootDisposable)
  }

  fun testArrowsAreShowingAndUpdated() {
    setupCompletionContext(myFixture)
    complete()
    val invokedCountBeforeTyping = arrowChecker.invokedCount
    type('r')
    arrowChecker.assertArrowsAvailable()
    TestCase.assertTrue(invokedCountBeforeTyping < arrowChecker.invokedCount)
  }

  private class TestArrowsExperimentStatus : ExperimentStatus {
    companion object {
      const val VERSION = 1
    }

    override fun forLanguage(language: Language): ExperimentInfo =
      ExperimentInfo(true, VERSION, true, true, true)

    override fun disable() = Unit

    override fun isDisabled(): Boolean = false
  }

  private class ArrowPresenceChecker : LookupTracker() {
    var invokedCount: Int = 0
    private var arrowsFound: Boolean = false

    override fun lookupCreated(lookup: LookupImpl, storage: MutableLookupStorage) {
      lookup.addPresentationCustomizer { _, presentation ->
        invokedCount += 1
        arrowsFound = arrowsFound || presentation.icon is ItemsDecoratorInitializer.LeftDecoratedIcon

        presentation
      }
    }

    fun assertArrowsAvailable() {
      TestCase.assertTrue(invokedCount > 1)
      TestCase.assertTrue(arrowsFound)
    }
  }

  private class TestInverseRankingModelProvider : ExperimentModelProvider {
    override fun experimentGroupNumber(): Int = TestArrowsExperimentStatus.VERSION

    override fun getModel(): DecisionFunction {
      return object : DecisionFunction {
        override fun getFeaturesOrder(): Array<FeatureMapper> {
          return arrayOf(FloatFeature("position", 1000.0, false).createMapper(null))
        }

        override fun getRequiredFeatures(): List<String> = listOf("position")

        override fun getUnknownFeatures(features: MutableCollection<String>): List<String> = emptyList()

        override fun version(): String? = null

        override fun predict(features: DoubleArray): Double = features[0]
      }
    }

    override fun getDisplayNameInSettings(): String = "Test provider"

    override fun isLanguageSupported(language: Language): Boolean = true
  }

  private data class MLRankingSettingsState(val language: String,
                                            val diffEnabled: Boolean,
                                            val languageEnabled: Boolean,
                                            val rankingEnabled: Boolean) {
    companion object {
      fun build(language: String, settings: CompletionMLRankingSettings): MLRankingSettingsState {
        return MLRankingSettingsState(language, settings.isRankingEnabled, settings.isShowDiffEnabled, settings.isLanguageEnabled(language))
      }
    }

    fun restore(settings: CompletionMLRankingSettings) {
      with(settings) {
        isRankingEnabled = rankingEnabled
        isShowDiffEnabled = diffEnabled
        setLanguageEnabled(language, languageEnabled)
      }
    }
  }
}