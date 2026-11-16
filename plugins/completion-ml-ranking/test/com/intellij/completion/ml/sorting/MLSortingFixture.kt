// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.completion.ml.sorting

import com.intellij.codeInsight.completion.LightCompletionFixture
import com.intellij.codeInsight.completion.lightCompletionFixture
import com.intellij.completion.ml.experiments.ExperimentInfo
import com.intellij.completion.ml.experiments.ExperimentStatus
import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.completion.ml.settings.CompletionMLRankingSettings
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeatureMapper
import com.intellij.internal.ml.FloatFeature
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.LightJavaProject
import com.intellij.testFramework.junit5.fixture.TestFixture
import com.intellij.testFramework.junit5.fixture.testFixture
import com.intellij.testFramework.replaceService

/**
 * The fixture of an ML sorting test. Create it with [mlSortingFixture].
 *
 * The test model scores each item by its position. [withRanker] picks the order of the scores.
 */
internal class MLSortingFixture(
  /**
   * The completion fixture of the test.
   */
  val completion: LightCompletionFixture,
) {
  internal var ranker: Ranker = Ranker.Default

  /**
   * Makes the test model score the items with [value].
   */
  fun withRanker(value: Ranker) {
    ranker = value
  }
}

/**
 * Creates an [MLSortingFixture] for one test. Declare it as an instance field.
 *
 * The fixture applies [customizeSettings] to the Java ranking settings, and restores the old settings after the test.
 * It turns on the ranking support and registers the test model.
 * The experiment status of the test is [experimentStatus] of the settings, or a status with the test model version.
 */
internal fun mlSortingFixture(
  projectFixture: TestFixture<LightJavaProject>,
  customizeSettings: (MLRankingSettingsState) -> MLRankingSettingsState,
  experimentStatus: (MLRankingSettingsState) -> ExperimentStatus = ::TestExperimentStatus,
): TestFixture<MLSortingFixture> = testFixture("mlSortingFixture") {
  val completion = lightCompletionFixture(projectFixture).init()
  val testRootDisposable = completion.fixture.testRootDisposable
  val sorting = MLSortingFixture(completion)

  val settings = CompletionMLRankingSettings.getInstance()
  val settingsStateBefore = MLRankingSettingsState.build("Java", settings)
  val actualSettingsState = customizeSettings(settingsStateBefore)
  actualSettingsState.setStateTo(settings)
  Disposer.register(testRootDisposable, Disposable { settingsStateBefore.setStateTo(CompletionMLRankingSettings.getInstance()) })
  RankingSupport.enableInTests(testRootDisposable)
  ApplicationManager.getApplication().replaceService(ExperimentStatus::class.java, experimentStatus(actualSettingsState), testRootDisposable)

  ExperimentModelProvider.registerProvider(TestModelProvider(sorting::ranker), testRootDisposable)
  initialized(sorting) {}
}

internal sealed interface Ranker {
  fun score(position: Double): Double

  object Default : Ranker {
    override fun score(position: Double): Double = -position
  }

  object Inverse : Ranker {
    override fun score(position: Double): Double = position
  }
}

private class TestExperimentStatus(private val settings: MLRankingSettingsState) : ExperimentStatus {
  companion object {
    const val VERSION = 1
  }

  override fun forLanguage(language: Language): ExperimentInfo =
    ExperimentInfo(true, VERSION, settings.rankingEnabled, settings.diffEnabled, true)

  override fun disable() = Unit

  override fun isDisabled(): Boolean = false
}

internal class MLRankingSettingsState private constructor(private val language: String,
                                                          val rankingEnabled: Boolean,
                                                          val diffEnabled: Boolean) {
  companion object {
    fun build(language: String, settings: CompletionMLRankingSettings): MLRankingSettingsState {
      return MLRankingSettingsState(language,
                                    settings.isRankingEnabled && settings.isLanguageEnabled(language),
                                    settings.isShowDiffEnabled)
    }
  }

  fun withRankingEnabled(value: Boolean): MLRankingSettingsState = MLRankingSettingsState(language, value, diffEnabled)

  fun withDiffEnabled(value: Boolean): MLRankingSettingsState = MLRankingSettingsState(language, rankingEnabled, value)

  fun setStateTo(settings: CompletionMLRankingSettings) {
    with(settings) {
      isRankingEnabled = rankingEnabled
      isShowDiffEnabled = diffEnabled
      setLanguageEnabled(language, rankingEnabled)
    }
  }
}

private class TestModelProvider(private val rankerSupplier: () -> Ranker) : ExperimentModelProvider {
  override fun experimentGroupNumber(): Int = TestExperimentStatus.VERSION

  override fun getModel(): DecisionFunction {
    return object : DecisionFunction {
      override fun getFeaturesOrder(): Array<FeatureMapper> {
        return arrayOf(FloatFeature("position", 1000.0, false).createMapper(null))
      }

      override fun getRequiredFeatures(): List<String> = listOf("position")

      override fun getUnknownFeatures(features: MutableCollection<String>): List<String> = emptyList()

      override fun version(): String? = null

      override fun predict(features: DoubleArray): Double = rankerSupplier.invoke().score(features[0])
    }
  }

  override fun getDisplayNameInSettings(): String = "Test provider"

  override fun isLanguageSupported(language: Language): Boolean = true
}
