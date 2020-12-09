// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.completion.ml.storage

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.ml.ContextFeatures
import com.intellij.codeInsight.completion.ml.MLFeatureValue
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.completion.ml.experiment.ExperimentStatus
import com.intellij.completion.ml.features.ContextFeaturesStorage
import com.intellij.completion.ml.performance.CompletionPerformanceTracker
import com.intellij.completion.ml.personalization.UserFactorStorage
import com.intellij.completion.ml.personalization.UserFactorsManager
import com.intellij.completion.ml.personalization.session.LookupSessionFactorsStorage
import com.intellij.completion.ml.sorting.RankingModelWrapper
import com.intellij.completion.ml.sorting.RankingSupport
import com.intellij.completion.ml.util.idString
import com.intellij.lang.Language
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.util.UserDataHolderBase
import org.jetbrains.annotations.TestOnly

class MutableLookupStorage(
  override val startedTimestamp: Long,
  override val language: Language,
  override val model: RankingModelWrapper?)
  : LookupStorage {
  private var _userFactors: Map<String, String>? = null
  override val userFactors: Map<String, String>
    get() = _userFactors ?: emptyMap()

  private var contextFeaturesStorage: ContextFeatures? = null
  override val contextFactors: Map<String, String>
    get() = contextFeaturesStorage?.asMap() ?: emptyMap()

  private var mlUsed: Boolean = false
  private var shouldReRank = model != null

  private var _loggingEnabled: Boolean = false
  override val performanceTracker: CompletionPerformanceTracker = CompletionPerformanceTracker()

  companion object {
    private val LOG = logger<MutableLookupStorage>()
    private val LOOKUP_STORAGE = Key.create<MutableLookupStorage>("completion.ml.lookup.storage")

    @Volatile
    private var alwaysComputeFeaturesInTests = true

    @TestOnly
    fun setComputeFeaturesAlways(value: Boolean, parentDisposable: Disposable) {
      val valueBefore = alwaysComputeFeaturesInTests
      alwaysComputeFeaturesInTests = value
      Disposer.register(parentDisposable, Disposable {
        alwaysComputeFeaturesInTests = valueBefore
      })
    }

    fun get(lookup: LookupImpl): MutableLookupStorage? {
      return lookup.getUserData(LOOKUP_STORAGE)
    }

    fun initOrGetLookupStorage(lookup: LookupImpl, language: Language): MutableLookupStorage {
      val existed = get(lookup)
      if (existed != null) return existed
      val storage = MutableLookupStorage(System.currentTimeMillis(), language, RankingSupport.getRankingModel(language))
      lookup.putUserData(LOOKUP_STORAGE, storage)
      return storage
    }

    fun get(parameters: CompletionParameters): MutableLookupStorage? {
      var storage = parameters.getUserData(LOOKUP_STORAGE)
      if (storage == null) {
        val activeLookup = LookupManager.getActiveLookup(parameters.editor) as? LookupImpl
        if (activeLookup != null) {
          storage = get(activeLookup)
          if (storage != null) {
            LOG.debug("Can't get storage from parameters. Fallback to storage from active lookup")
            saveAsUserData(parameters, storage)
          }
        }
      }
      return storage
    }

    fun saveAsUserData(parameters: CompletionParameters, storage: MutableLookupStorage) {
      val completionProcess = parameters.process
      if (completionProcess is UserDataHolder) {
        completionProcess.putUserData(LOOKUP_STORAGE, storage)
      }
    }

    private fun <T> CompletionParameters.getUserData(key: Key<T>): T? {
      return (process as? UserDataHolder)?.getUserData(key)
    }
  }

  override val sessionFactors: LookupSessionFactorsStorage = LookupSessionFactorsStorage(startedTimestamp)

  private val item2storage: MutableMap<String, MutableElementStorage> = mutableMapOf()

  override fun getItemStorage(id: String): MutableElementStorage = item2storage.computeIfAbsent(id) {
    MutableElementStorage()
  }

  override fun mlUsed(): Boolean = mlUsed

  fun fireReorderedUsingMLScores() {
    mlUsed = true
    performanceTracker.reorderedByML()
  }

  override fun shouldComputeFeatures(): Boolean = shouldReRank() ||
                                                  (ApplicationManager.getApplication().isUnitTestMode && alwaysComputeFeaturesInTests) ||
                                                  (_loggingEnabled && !experimentWithoutComputingFeatures())

  override fun shouldReRank(): Boolean = model != null && shouldReRank

  fun disableReRanking() {
    shouldReRank = false
  }

  fun isContextFactorsInitialized(): Boolean = contextFeaturesStorage != null

  fun fireElementScored(element: LookupElement, factors: MutableMap<String, Any>, mlScore: Double?) {
    getItemStorage(element.idString()).fireElementScored(factors, mlScore)
  }

  fun initUserFactors(project: Project) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (_userFactors == null && UserFactorsManager.ENABLE_USER_FACTORS) {
      val userFactorValues = mutableMapOf<String, String>()
      val userFactors = UserFactorsManager.getInstance().getAllFactors()
      val applicationStorage: UserFactorStorage = UserFactorStorage.getInstance()
      val projectStorage: UserFactorStorage = UserFactorStorage.getInstance(project)
      for (factor in userFactors) {
        factor.compute(applicationStorage)?.let { userFactorValues["${factor.id}:App"] = it }
        factor.compute(projectStorage)?.let { userFactorValues["${factor.id}:Project"] = it }
      }
      _userFactors = userFactorValues
    }
  }

  override fun contextProvidersResult(): ContextFeatures = contextFeaturesStorage ?: ContextFeaturesStorage.EMPTY

  fun initContextFactors(contextFactors: MutableMap<String, MLFeatureValue>,
                         environment: UserDataHolderBase) {
    if (isContextFactorsInitialized()) {
      LOG.error("Context factors should be initialized only once")
    }
    else {
      val features = ContextFeaturesStorage(contextFactors)
      environment.copyUserDataTo(features)
      contextFeaturesStorage = features
    }
  }

  private fun experimentWithoutComputingFeatures(): Boolean {
    val experimentInfo = ExperimentStatus.getInstance().forLanguage(language)
    if (experimentInfo.inExperiment) {
      return !experimentInfo.shouldCalculateFeatures
    }
    return false
  }

  fun markLoggingEnabled() {
    _loggingEnabled = true
  }
}