// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.completion.ml.sorting

import com.intellij.completion.ml.ranker.ExperimentModelProvider
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.internal.ml.DecisionFunction
import com.intellij.internal.ml.FeatureMapper
import com.intellij.internal.ml.completion.RankingModelProvider
import com.intellij.lang.Language
import com.intellij.testFramework.common.runAll

class RankingProvidersTest : MLSortingTestCase() {
  private lateinit var testLanguage: Language

  override fun customizeSettings(settings: MLRankingSettingsState): MLRankingSettingsState =
    settings.withRankingEnabled(true).withDiffEnabled(false)

  override fun configureExperimentStatus(actualSettingsState: MLRankingSettingsState) { }

  fun `test no providers registered`() {
    checkActiveProvider(null, 0)
  }

  fun `test not matching experiment provider`() {
    registerProviders(experiment(-1))
    checkActiveProvider(null, 0)
  }

  fun `test enabled experiment provider`() {
    val expectedProvider = experiment(0)
    registerProviders(expectedProvider)
    checkActiveProvider(expectedProvider, 0)
  }

  fun `test experiment provider replace default`() {
    val expectedProvider = experiment(0)
    registerProviders(expectedProvider, default())
    checkActiveProvider(expectedProvider, 0)
  }

  fun `test experiment provider should not replace default if not matching`() {
    val expectedProvider = default()
    registerProviders(experiment(-1), expectedProvider)
    checkActiveProvider(expectedProvider, 0)
  }

  fun `test default provider used if there is no experiment`() {
    val expectedProvider = default()
    registerProviders(expectedProvider)
    checkActiveProvider(expectedProvider, 0)
  }

  fun `test few experiment providers`() {
    val expectedProvider = experiment(0)
    registerProviders(expectedProvider, experiment(-1), experiment(1))
    checkActiveProvider(expectedProvider, 0)
  }

  fun `test too many experiment providers`() {
    registerProviders(experiment(0), experiment(0))
    assertThrows(IllegalStateException::class.java) { ExperimentModelProvider.findProvider(testLanguage, 0) }
  }

  fun `test too many default providers`() {
    registerProviders(default(), default())
    assertThrows(IllegalStateException::class.java) { ExperimentModelProvider.findProvider(testLanguage, 0) }
  }

  private fun checkActiveProvider(expectedProvider: RankingModelProvider?, @Suppress("SameParameterValue") groupNumber: Int) {
    val languageSupported = expectedProvider != null
    assertEquals(languageSupported, expectedProvider in RankingSupport.availableRankers())
    val actualProvider = ExperimentModelProvider.findProvider(testLanguage, groupNumber)
    assertEquals(expectedProvider, actualProvider)
  }

  private fun registerProviders(vararg providers: RankingModelProvider) {
    providers.forEach { ExperimentModelProvider.registerProvider(it, testRootDisposable) }
  }

  override fun setUp() {
    super.setUp()
    testLanguage = TestLanguage()
  }

  override fun tearDown() {
    runAll(
      { if (::testLanguage.isInitialized) testLanguage.unregisterLanguage(PluginManagerCore.getPlugin(PluginManagerCore.CORE_ID)!!) },
      { super.tearDown() }
    )
  }

  private fun experiment(groupNumber: Int): RankingModelProvider = TestExperimentProvider(groupNumber, testLanguage)

  private fun default(): RankingModelProvider = TestModelProvider(testLanguage)

  private open class TestModelProvider(private val supportedLanguage: Language) : RankingModelProvider {
    override fun getModel(): DecisionFunction = TestDummyDecisionFunction()

    override fun getDisplayNameInSettings(): String = supportedLanguage.displayName

    override fun isLanguageSupported(language: Language): Boolean = language == supportedLanguage
  }

  private class TestExperimentProvider(private val experimentGroupNumber: Int, supportedLanguage: Language)
    : TestModelProvider(supportedLanguage), ExperimentModelProvider
  {
    override fun experimentGroupNumber(): Int = experimentGroupNumber
  }

  private class TestDummyDecisionFunction : DecisionFunction {
    override fun getFeaturesOrder(): Array<FeatureMapper> = emptyArray()
    override fun getRequiredFeatures(): List<String> = emptyList()
    override fun getUnknownFeatures(features: MutableCollection<String>): List<String> = emptyList()
    override fun version(): String? = null
    override fun predict(features: DoubleArray?): Double = 0.0
  }

  private class TestLanguage : Language("RankingProvidersTest_TEST_LANG_ID") {
    override fun getDisplayName(): String = "unique unusable blah-blah" + System.identityHashCode(this)
  }
}
