package com.intellij.completion.ml.sorting

import com.intellij.codeInsight.completion.LightFixtureCompletionTestCase
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
import com.intellij.testFramework.replaceService

abstract class MLSortingTestCase : LightFixtureCompletionTestCase() {
  private var ranker: Ranker = Ranker.Default

  protected abstract fun customizeSettings(settings: MLRankingSettingsState): MLRankingSettingsState

  public override fun setUp() {
    super.setUp()

    setUpRankingSettings()
    setUpRankingModel()
  }

  public override fun tearDown() {
    super.tearDown()
  }

  protected fun withRanker(value: Ranker) {
    ranker = value
  }

  private fun setUpRankingSettings() {
    val settings = CompletionMLRankingSettings.getInstance()

    val settingsStateBefore = MLRankingSettingsState.build("Java", settings)
    val actualSettingsState = customizeSettings(settingsStateBefore)

    actualSettingsState.setStateTo(settings)

    Disposer.register(testRootDisposable, Disposable { settingsStateBefore.setStateTo(CompletionMLRankingSettings.getInstance()) })

    RankingSupport.enableInTests(testRootDisposable)

    configureExperimentStatus(actualSettingsState)
  }

  protected open fun configureExperimentStatus(actualSettingsState: MLRankingSettingsState) {
    ApplicationManager.getApplication().replaceService(
      ExperimentStatus::class.java,
      TestExperimentStatus(actualSettingsState),
      testRootDisposable
    )
  }


  private fun setUpRankingModel() {
    ExperimentModelProvider.registerProvider(TestModelProvider(::ranker), testRootDisposable)
  }

  protected sealed interface Ranker {
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

  protected class MLRankingSettingsState private constructor(private val language: String,
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
}